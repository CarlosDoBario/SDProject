package testes;

import client.ClientAPI;
import client.ClientConnection;

public class TesteFiltragem {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Iniciando Teste de Filtragem...");

        try (ClientConnection conn = new ClientConnection(HOST, PORT);
             ClientAPI api = new ClientAPI(conn)) {

            try { api.register("userF", "pass"); } catch (Exception ignored) {}
            api.login("userF", "pass");

            // 1. Preparar dados: Inserir vários produtos no dia atual
            api.addEvent("Prod_A", 10, 1.0, System.currentTimeMillis());
            api.addEvent("Prod_B", 20, 2.0, System.currentTimeMillis());
            api.addEvent("Prod_C", 30, 3.0, System.currentTimeMillis());

            // 2. Avançar o dia para poder filtrar (o filtro é sobre dias anteriores)
            int diaParaFiltrar = api.advanceDay();
            System.out.println("-> Dados persistidos no Dia " + diaParaFiltrar);

            // 3. Executar Filtro: Apenas Prod_A e Prod_C
            System.out.println("-> Solicitando filtro para 'Prod_A' e 'Prod_C'...");
            String resultado = api.filterByDay(2, "Prod_A,Prod_C", diaParaFiltrar);

            System.out.println("\n======= RESULTADO =======");
            System.out.println("Conteúdo recebido:\n" + resultado);

            if (resultado.contains("Prod_A") && resultado.contains("Prod_C") && !resultado.contains("Prod_B")) {
                System.out.println("\nFiltragem: OK (Apenas os produtos solicitados foram retornados)");
            } else {
                System.out.println("\nFiltragem: ERRO (O conteúdo retornado não condiz com o filtro)");
            }
            System.out.println("=========================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}