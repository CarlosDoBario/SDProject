package testes;

import client.ClientAPI;
import client.ClientConnection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteStress {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int NUM_CLIENTS = 50; // Número de clientes concorrentes
    private static final int REQUESTS_PER_CLIENT = 100; // Pedidos por cada cliente

    public static void main(String[] args) {
        System.out.println("Iniciando Teste de Carga e Escalabilidade...");
        System.out.println("Cenário: " + NUM_CLIENTS + " clientes, " + REQUESTS_PER_CLIENT + " pedidos cada.");

        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int id = i;
            Thread t = new Thread(() -> {
                try (ClientConnection conn = new ClientConnection(HOST, PORT);
                     ClientAPI api = new ClientAPI(conn)) {

                    String user = "user" + id;
                    String pass = "pass" + id;

                    // Tenta registar (pode falhar se já existir, ignoramos)
                    try { api.register(user, pass); } catch (Exception ignored) {}

                    if (api.login(user, pass)) {
                        for (int j = 0; j < REQUESTS_PER_CLIENT; j++) {
                            try {
                                // Simula a inserção de um evento
                                api.addEvent("Produto_" + id, 1, 10.5, System.currentTimeMillis());
                                successfulRequests.incrementAndGet();
                            } catch (IOException e) {
                                failedRequests.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro no cliente " + id + ": " + e.getMessage());
                }
            });
            threads.add(t);
            t.start();
        }

        // Aguarda todos os clientes terminarem
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTimePerRequest = (double) totalTime / (successfulRequests.get() + failedRequests.get());

        // Mensagem de resultado final
        System.out.println("\n======= RESULTADO DO TESTE =======");
        System.out.println("Tempo Total de Execucao: " + totalTime + " ms");
        System.out.println("Pedidos Bem Sucedidos: " + successfulRequests.get());
        System.out.println("Pedidos Falhados: " + failedRequests.get());
        System.out.printf("Latência Média por Pedido: %.2f ms\n", avgTimePerRequest);
        System.out.println("Throughtput Estimado: " + (int)(successfulRequests.get() / (totalTime / 1000.0)) + " pedidos/segundo");
        System.out.println("==================================");
    }
}
