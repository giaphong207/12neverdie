package com.auction.server.service;

import com.auction.server.dao.AuctionDao;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.concurrency.AuctionLockManager;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.auction.Auction;
import com.auction.shared.model.auction.AuctionStatus;
import com.auction.shared.networkMessage.AuctionEvents.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class DefaultAuctionLifecycleService implements AuctionLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(DefaultAuctionLifecycleService.class);

    /** Cửa sổ thời gian winner phải thanh toán sau khi FINISHED. */
    private static final long PAYMENT_WINDOW_HOURS = 24L;

    private final AuctionDao auctionDao;
    private final EventBroadcaster broadcaster;
    private final AuctionLockManager lockManager;
    private final WalletService walletService;

    /** 2 thread đủ cho task ngắn (chỉ load + transition + save + broadcast). */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    /** Tracking futures để cancel/reschedule khi cần (anti-sniping). */
    private final Map<String, ScheduledFuture<?>> startTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> closeTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> paymentTimeoutTasks = new ConcurrentHashMap<>();

    public DefaultAuctionLifecycleService(AuctionDao auctionDao,
                                          EventBroadcaster broadcaster,
                                          AuctionLockManager lockManager,
                                          WalletService walletService) {
        this.auctionDao = auctionDao;
        this.broadcaster = broadcaster;
        this.lockManager = lockManager;
        this.walletService = walletService;
    }

    // ═══════════════════════════════════════════════════════════
    // EAGER: scheduler-based transitions
    // ═══════════════════════════════════════════════════════════

    @Override
    public void scheduleStart(Auction auction) {
        long delayMs = computeDelay(auction.getStartTime());
        ScheduledFuture<?> future = scheduler.schedule(
                () -> safeRun(() -> doStart(auction.getId())),
                delayMs, TimeUnit.MILLISECONDS);
        startTasks.put(auction.getId(), future);
    }

    @Override
    public void scheduleClose(Auction auction) {
        long delayMs = computeDelay(auction.getEndTime());
        ScheduledFuture<?> future = scheduler.schedule(
                () -> safeRun(() -> doClose(auction.getId())),
                delayMs, TimeUnit.MILLISECONDS);
        closeTasks.put(auction.getId(), future);
    }

    @Override
    public void rescheduleClose(Auction auction) {
        ScheduledFuture<?> oldFuture = closeTasks.remove(auction.getId());
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
        scheduleClose(auction);  // schedule lại với endTime mới
    }

    @Override
    public void schedulePaymentTimeout(Auction auction) {
        long delayMs = TimeUnit.HOURS.toMillis(PAYMENT_WINDOW_HOURS);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> safeRun(() -> doPaymentTimeout(auction.getId())),
                delayMs, TimeUnit.MILLISECONDS);
        paymentTimeoutTasks.put(auction.getId(), future);
    }

    // ═══════════════════════════════════════════════════════════
    // Scheduler callbacks (private — chỉ scheduler gọi)
    // Bọc lock để an toàn với placeBid đang chạy đồng thời
    // ═══════════════════════════════════════════════════════════

    private void doStart(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId).orElse(null);
            if (a == null) return;
            if (a.getStatus() != AuctionStatus.OPEN) return;  // đã transition rồi

            a.start();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionStartedEvent(a));
        } finally {
            lock.unlock();
            startTasks.remove(auctionId);
        }
    }

    private void doClose(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId).orElse(null);
            if (a == null) return;
            if (a.getStatus() != AuctionStatus.RUNNING) return;

            a.finish();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionEndedEvent(a));

            // FINISHED → thanh toán tự động (PAID), hoặc giữ FINISHED nếu winner thiếu tiền
            settleOrSchedule(a);
        } finally {
            lock.unlock();
            closeTasks.remove(auctionId);
        }
    }

    private void doPaymentTimeout(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId).orElse(null);
            if (a == null) return;
            if (a.getStatus() != AuctionStatus.FINISHED) return;  // đã PAID rồi → skip

            a.cancel();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionCancelledEvent(a));
        } finally {
            lock.unlock();
            paymentTimeoutTasks.remove(auctionId);
        }
    }

    /**
     * Thanh toán TỰ ĐỘNG ngay khi phiên vừa FINISHED (phải gọi khi đang giữ lock):
     * - Không có người đặt giá → giữ FINISHED, không thanh toán.
     * - Winner đủ tiền → trừ winner + cộng seller → PAID + broadcast AuctionPaidEvent.
     * - Winner thiếu tiền → giữ FINISHED + log + hẹn timeout (huỷ sau nếu vẫn chưa trả).
     */
    private void settleOrSchedule(Auction a) {
        String winnerId = a.getHighestBidderId();
        if (winnerId == null) {
            log.info("Phiên {} kết thúc không có người đặt giá → giữ FINISHED.", a.getId());
            return;
        }
        boolean paid = walletService.settlePayment(winnerId, a.getSellerId(), a.getCurrentPrice());
        if (paid) {
            a.markPaid();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionPaidEvent(a));
            // Realtime: báo số dư mới cho winner (bị trừ) và seller (được cộng)
            broadcaster.broadcast(new WalletUpdatedEvent(a,
                    winnerId, walletService.getBalance(winnerId),
                    a.getSellerId(), walletService.getBalance(a.getSellerId())));
            log.info("Phiên {} thanh toán tự động OK: winner {} → seller {} ({}).",
                    a.getId(), winnerId, a.getSellerId(), a.getCurrentPrice());
        } else {
            log.warn("Winner {} không đủ tiền cho phiên {} ({}) → giữ FINISHED, hẹn timeout.",
                    winnerId, a.getId(), a.getCurrentPrice());
            schedulePaymentTimeout(a);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LAZY: syncByTime

    // ═══════════════════════════════════════════════════════════
    // Manual (gọi từ Admin/Payment service)
    // ═══════════════════════════════════════════════════════════
    @Override
    public Auction syncByTime(String auctionId) {
        Auction auction = auctionDao.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        LocalDateTime now = LocalDateTime.now();
        boolean transitioned = false;

        // OPEN → RUNNING nếu đã đến giờ start
        if (auction.getStatus() == AuctionStatus.OPEN
                && !now.isBefore(auction.getStartTime())) {
            auction.start();
            transitioned = true;
        }

        // RUNNING → FINISHED nếu đã quá giờ end
        // (kiểm tra lại status vì start() ở trên có thể vừa chạy)
        if (auction.getStatus() == AuctionStatus.RUNNING
                && !now.isBefore(auction.getEndTime())) {
            auction.finish();
            transitioned = true;
        }

        if (transitioned) {
            auctionDao.save(auction);
            broadcaster.broadcast(new AuctionUpdatedEvent(auction));
        }

        return auction;
    }

    @Override
    public Auction finishAuction(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));
            a.finish();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionEndedEvent(a));
            settleOrSchedule(a);
            return a;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Auction cancelAuction(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));
            a.cancel();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionCancelledEvent(a));

            // Cleanup task chưa fire
            cancelScheduledTask(startTasks, auctionId);
            cancelScheduledTask(closeTasks, auctionId);
            cancelScheduledTask(paymentTimeoutTasks, auctionId);
            return a;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Auction markAuctionPaid(String auctionId) {
        ReentrantLock lock = lockManager.getLock(auctionId);
        lock.lock();
        try {
            Auction a = auctionDao.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));
            a.markPaid();
            auctionDao.save(a);
            broadcaster.broadcast(new AuctionPaidEvent(a));

            // Đã PAID → cancel payment timeout (nếu vẫn pending)
            cancelScheduledTask(paymentTimeoutTasks, auctionId);
            return a;
        } finally {
            lock.unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Lifecycle management
    // ═══════════════════════════════════════════════════════════

    @Override
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private long computeDelay(LocalDateTime target) {
        long ms = Duration.between(LocalDateTime.now(), target).toMillis();
        return Math.max(0L, ms);  // nếu đã quá hạn, fire ngay
    }

    private void cancelScheduledTask(Map<String, ScheduledFuture<?>> map, String id) {
        ScheduledFuture<?> f = map.remove(id);
        if (f != null) f.cancel(false);
    }

    /** Bọc Runnable để exception trong scheduler không kill thread. */
    private void safeRun(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log.error("Scheduled task failed", t);
        }
    }
}