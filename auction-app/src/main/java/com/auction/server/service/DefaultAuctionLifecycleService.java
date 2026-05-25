package com.auction.server.service;

import com.auction.server.DAO.AuctionDao;
import com.auction.server.realtime.EventBroadcaster;
import com.auction.server.concurrency.AuctionLockManager;
import com.auction.shared.exception.AppExceptions.*;
import com.auction.shared.model.Auction;
import com.auction.shared.model.AuctionStatus;
import com.auction.shared.networkMessage.event.AuctionEvents.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultAuctionLifecycleService implements AuctionLifecycleService {

    /** Cửa sổ thời gian winner phải thanh toán sau khi FINISHED. */
    private static final long PAYMENT_WINDOW_HOURS = 24L;

    private final AuctionDao auctionDao;
    private final EventBroadcaster broadcaster;
    private final AuctionLockManager lockManager;

    /** 2 thread đủ cho task ngắn (chỉ load + transition + save + broadcast). */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    /** Tracking futures để cancel/reschedule khi cần (anti-sniping). */
    private final Map<String, ScheduledFuture<?>> startTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> closeTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> paymentTimeoutTasks = new ConcurrentHashMap<>();

    public DefaultAuctionLifecycleService(AuctionDao auctionDao,
                                          EventBroadcaster broadcaster,
                                          AuctionLockManager lockManager) {
        this.auctionDao = auctionDao;
        this.broadcaster = broadcaster;
        this.lockManager = lockManager;
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

            // Chain: FINISHED → schedule payment timeout
            schedulePaymentTimeout(a);
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

    // ═══════════════════════════════════════════════════════════
    // LAZY: syncByTime, syncAll (như PA B)

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
    public void syncAll() {
        List<Auction> auctions = auctionDao.findAll();
        for (Auction auction : auctions) {
            // Skip terminal states để tránh load+save không cần thiết
            AuctionStatus s = auction.getStatus();
            if (s == AuctionStatus.FINISHED
                    || s == AuctionStatus.PAID
                    || s == AuctionStatus.CANCELED) {
                continue;
            }
            syncByTime(auction.getId());
        }
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
            schedulePaymentTimeout(a);
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
            System.err.println("[LifecycleService] Scheduled task failed: " + t.getMessage());
            t.printStackTrace();
        }
    }
}