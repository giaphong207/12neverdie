package com.auction.server.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class SerializationUtils {

    public static void writeObject(String path, Serializable obj) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs(); //ktra xem nếu file tồn tại thì đi qua, nếu k thì tạo file mới trên ổ cứng

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(obj);
        }
    }

    public static Object readObject(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return ois.readObject();
        }
    }
}