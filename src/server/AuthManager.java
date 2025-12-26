package server;

import common.IOUtils;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class AuthManager {
    private final Map<String, String> users = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final File authFile;

    public AuthManager(String path) {
        this.authFile = new File(path);
        loadUsers();
    }

    private void loadUsers() {
        if (!authFile.exists()) return;
        lock.lock();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(authFile)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String u = IOUtils.readString(in);
                String p = IOUtils.readString(in);
                users.put(u, p);
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar users: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void saveUsers() {
        // Assume-se que o lock já está detido por quem chama saveUsers ou detém aqui
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(authFile)))) {
            out.writeInt(users.size());
            for (Map.Entry<String, String> entry : users.entrySet()) {
                IOUtils.writeString(out, entry.getKey());
                IOUtils.writeString(out, entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Erro ao guardar users: " + e.getMessage());
        }
    }

    public boolean register(String username, String password) {
        lock.lock();
        try {
            if (users.containsKey(username)) return false;
            users.put(username, password);
            saveUsers();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean login(String username, String password) {
        lock.lock();
        try {
            String p = users.get(username);
            return p != null && p.equals(password);
        } finally {
            lock.unlock();
        }
    }
}