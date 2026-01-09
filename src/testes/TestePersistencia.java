package testes;

import client.ClientAPI;
import client.ClientConnection;

public class TestePersistencia {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Iniciando Teste de Persistência e Agregaçao...");

        try (ClientConnection conn = new ClientConnection(HOST, PORT);
             ClientAPI api = new ClientAPI(conn)) {

            try { api.register("admin", "admin"); } catch (Exception ignored) {}
            api.login("admin", "admin");

            // 1. Criar dados no "passado"
            System.out.println("-> Inserindo eventos no dia atual...");
            api.addEvent("Televisao", 10, 500.0, System.currentTimeMillis());
            api.addEvent("Televisao", 5, 450.0, System.currentTimeMillis());

            // 2. Avançar o dia (força a escrita no disco)
            System.out.println("-> Avançando o dia (Persistindo dados)...");
            int diaFinalizado = api.advanceDay();
            System.out.println("   Dia " + diaFinalizado + " foi guardado no disco.");

            // 3. Testar Agregação Lazy
            System.out.println("-> Solicitando agregaçao (Quantidade de Televisoes nos últimos 2 dias)...");
            // Isto obriga o AggregationManager a carregar o ficheiro do disco
            int qtd = api.aggregateQuantity("Televisao", 2);

            System.out.println("\n======= RESULTADO =======");
            if (qtd == 15) {
                System.out.println("Persistência e Agregaçao: OK");
                System.out.println("Total detectado: " + qtd + " unidades.");
            } else {
                System.out.println("Erro: Esperava 15 unidades, mas obteve " + qtd);
            }
            System.out.println("=========================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}