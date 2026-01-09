package testes;

import client.ClientAPI;
import client.ClientConnection;

public class TesteRobustez {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Iniciando Teste de Robustez...");

        try {
            // 1. Cliente "Zombi"
            Thread zombieThread = new Thread(() -> {
                try (ClientConnection conn = new ClientConnection(HOST, PORT);
                     ClientAPI api = new ClientAPI(conn)) {

                    // Tenta registar, se já existir não faz mal
                    try { api.register("zombi", "pass"); } catch (Exception ignored) {}

                    if (api.login("zombi", "pass")) {
                        System.out.println("[Zombi] Autenticado. Bloqueando em waitSimultaneous...");
                        // Esta operação bloqueia a thread no servidor
                        api.waitSimultaneous("ProdA", "ProdB");
                    }
                } catch (Exception e) {
                    System.out.println("[Zombi] Terminou: " + e.getMessage());
                }
            });
            zombieThread.start();

            Thread.sleep(1000); // Espera o zombi bloquear

            // 2. Cliente "Normal"
            System.out.println("[Normal] A tentar interagir com o servidor...");
            try (ClientConnection conn = new ClientConnection(HOST, PORT);
                 ClientAPI api = new ClientAPI(conn)) {

                try { api.register("normal", "pass"); } catch (Exception ignored) {}

                if (api.login("normal", "pass")) {
                    long start = System.currentTimeMillis();
                    api.addEvent("Teste", 1, 10.0, System.currentTimeMillis());
                    long end = System.currentTimeMillis();

                    System.out.println("[Normal] Operação concluída em " + (end - start) + "ms.");
                    System.out.println("\n======= RESULTADO =======");
                    System.out.println("Robustez: OK (O servidor continua funcional)");
                    System.out.println("=========================");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}