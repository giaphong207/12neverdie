package com.auction.client.util;

import com.auction.client.main.ClientApp;
import com.auction.client.network.ServerMessageListener;
import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gửi request xuống server trên thread phụ rồi đẩy response về FX thread.
 *
 * Pattern thay thế:
 *   new Thread(() -> {
 *       try {
 *           ServerMessageListener listener = ClientApp.getListener();
 *           if (listener == null) { ... }
 *           Object response = listener.sendAndWait(request, 10_000);
 *           if (response == null) { ... }
 *           Platform.runLater(() -> handleResponse(response));
 *       } catch (Exception e) { ... }
 *   }).start();
 *
 * Được rút gọn thành:
 *   RequestExecutor.send(request, onSuccess, onError);
 *
 * Trong đó onSuccess nhận response (Object thô, controller tự cast theo
 * sealed result type của mình), onError nhận message lỗi để hiển thị.
 *
 * onSuccess và onError đều được gọi trên FX thread — controller có thể
 * thao tác UI trực tiếp, không cần Platform.runLater thêm.
 */
public final class RequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(RequestExecutor.class);

    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    /**
     * Single-thread vì ServerMessageListener.sendAndWait đã synchronized —
     * mọi request đều phải xếp hàng tuần tự. Multi-thread không thêm được
     * parallelism, chỉ tạo thread idle. Dùng daemon để JVM tắt được sạch
     * khi user đóng app dù còn task pending.
     */
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RequestExecutor");
        t.setDaemon(true);
        return t;
    });

    private RequestExecutor() {}

    public static void send(Object request,
                            Consumer<Object> onSuccess,
                            Consumer<String> onError) {
        send(request, DEFAULT_TIMEOUT_MS, onSuccess, onError);
    }

    public static void send(Object request,
                            long timeoutMs,
                            Consumer<Object> onSuccess,
                            Consumer<String> onError) {
        executor.submit(() -> {
            try {
                ServerMessageListener listener = ClientApp.getListener();
                if (listener == null) {
                    Platform.runLater(() -> onError.accept("Listener chưa được khởi tạo"));
                    return;
                }

                Object response = listener.sendAndWait(request, timeoutMs);

                if (response == null) {
                    Platform.runLater(() -> onError.accept("Server không phản hồi"));
                    return;
                }

                Platform.runLater(() -> onSuccess.accept(response));
            } catch (Exception e) {
                log.error("Lỗi gửi request {}", request.getClass().getSimpleName(), e);
                Platform.runLater(() -> onError.accept("Lỗi mạng: " + e.getMessage()));
            }
        });
    }

    public static void shutdown() {
        executor.shutdown();
    }
}