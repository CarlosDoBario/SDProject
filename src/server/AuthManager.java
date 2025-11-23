package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 Gestão simples de utilizadores (em memória).
 */
public class AuthManager {
    private final Map<String, String> users = new ConcurrentHashMap<>();

    // Regista um utilizador. Retorna true se criado, false se já existia.
    public boolean register(String username, String password) {
        if (username == null || password == null) return false;
        return users.putIfAbsent(username, password) == null;
    }

    // Valida credenciais. Retorna true se ok.
    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        String p = users.get(username);
        return p != null && p.equals(password);
    }

    // Saber se um utilizador existe (p.ex. admin checks).
    public boolean userExists(String username) {
        return users.containsKey(username);
    }
}