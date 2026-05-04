package com.auction.client.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Quản lý kết nối socket duy nhất đến server.
 * Áp dụng Singleton pattern với Double-Checked Locking để tối ưu hiệu năng.
 */
public final class ServerConnection {
    
    //Sử dụng volatile để đảm bảo an toàn luồng (thread-safe)
    private static volatile ServerConnection instance;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private ServerConnection() {}

    //Lấy singleton instance an toàn và tối ưu
    public static ServerConnection getInstance() {
        if (instance == null) {
            synchronized (ServerConnection.class) {
                if (instance == null) {
                    instance = new ServerConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Kết nối đến server
     * * @param host Địa chỉ server (vd: "localhost")
     * @param port Port server (vd: 9999)
     * @throws IOException Nếu kết nối thất bại
     */
    public void connect(String host, int port) throws IOException {
        try {
            socket = new Socket(host, port);
            
            //Tạo output stream TRƯỚC input stream để tránh deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();  //Flush để đẩy header sang cho server nhận diện
            
            in = new ObjectInputStream(socket.getInputStream());
            
            System.out.println("Kết nối server thành công: " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("Lỗi kết nối server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Gửi object tới server một cách đồng bộ
     * * @param message Object cần gửi (phải implements Serializable)
     * @throws IOException Nếu gửi thất bại
     */
    public synchronized void send(Object message) throws IOException {
        if (out == null) {
            throw new IOException("Chưa kết nối server. Yêu cầu gọi connect() trước.");
        }
        
        try {
            out.writeObject(message);
            out.flush();
            System.out.println("Đã gửi: " + message.getClass().getSimpleName());
        } catch (IOException e) {
            System.err.println("Lỗi khi gửi dữ liệu: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Lấy input stream để đọc event từ server
     * * @return ObjectInputStream
     */
    public ObjectInputStream getInputStream() {
        return in;
    }

    //Kiểm tra trạng thái kết nối
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    //Đóng toàn bộ kết nối và giải phóng tài nguyên an toàn
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Đã đóng kết nối với server an toàn.");
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }
}