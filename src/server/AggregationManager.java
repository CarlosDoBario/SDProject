package server;

import server.model.Event;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 - Servir pedidos de agregação (quantidade, volume, avg price, max price) para um produto nos últimos d dias
 (exclui o dia atual).
 - Cálculo lazy por dia + cache com limite S (LRU por dia).
 */
public class AggregationManager {
    private final DayManager dayManager;
    private final PersistenceManager persistenceManager;

    // Configuráveis
    private final int D; // número máximo de dias a considerar (história)
    private final int S; // número máximo de séries em memória (dias)

    private static final class PerDayAgg {
        int quantity = 0;
        double volume = 0.0; // sum(price * quantity)
        double maxPrice = 0.0;
        int countEvents = 0;

        void incorporate(Event e) {
            int q = e.getQuantity();
            double p = e.getPrice();
            quantity += q;
            volume += p * q;
            if (countEvents == 0 || p > maxPrice) maxPrice = p;
            countEvents++;
        }
    }

    // dayCache: LinkedHashMap com a estratégia LRU (chave = dayIndex, valor = mapa product->PerDayAgg)
    // Protegido por sincronização em dayCache.
    private final LinkedHashMap<Integer, Map<String, PerDayAgg>> dayCache = new LinkedHashMap<>(16, 0.75f, true);
    private final java.util.HashSet<Integer> loadingDays = new java.util.HashSet<>();

    public AggregationManager(DayManager dayManager, PersistenceManager persistenceManager) {
        this(7, 3, dayManager, persistenceManager);
    }

    public AggregationManager(int D, int S, DayManager dayManager, PersistenceManager persistenceManager) {
        if (D <= 0) throw new IllegalArgumentException("D must be > 0");
        if (S < 0) throw new IllegalArgumentException("S must be >= 0");
        this.D = D;
        this.S = S;
        this.dayManager = dayManager;
        this.persistenceManager = persistenceManager;
    }

    // Carrega/obtém as estatísticas para um dado produto num dia específico.
    // Se S==0 não mantém em cache; caso contrário tenta inserir no cache respeitando a capacidade S.
    private PerDayAgg getPerDayAggForProduct(int dayIndex, String product) throws IOException {
        // verificação rápida na cache
        synchronized (dayCache) {
            Map<String, PerDayAgg> map = dayCache.get(dayIndex);
            if (map != null) {
                PerDayAgg p = map.get(product);
                if (p != null) return p;
            }
            // Se outra thread estiver a carregar este dia, espera
            if (loadingDays.contains(dayIndex)) {
                try {
                    while (loadingDays.contains(dayIndex)) {
                        dayCache.wait();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new PerDayAgg();
                }
                map = dayCache.get(dayIndex);
                if (map != null) {
                    PerDayAgg p = map.get(product);
                    if (p != null) return p;
                }
                // caso não exista, irá continuar para carregar
            } else {
                // marca como a carregar
                loadingDays.add(dayIndex);
            }
        }

        // Carregar do disco (streaming), apenas computar para o produto pedido
        PerDayAgg agg = new PerDayAgg();
        if (persistenceManager.dayExists(dayIndex)) {
            persistenceManager.streamDay(dayIndex, e -> {
                if (product.equals(e.getProductName())) {
                    agg.incorporate(e);
                }
            });
        }

        // Depois de carregar, decidir cachear ou não
        synchronized (dayCache) {
            loadingDays.remove(dayIndex);
            if (S > 0) {
                Map<String, PerDayAgg> map = dayCache.get(dayIndex);
                if (map == null) {
                    // garantir capacidade S (evict LRU se necessário)
                    while (dayCache.size() >= S) {
                        Iterator<Integer> it = dayCache.keySet().iterator();
                        if (it.hasNext()) {
                            it.next();
                            it.remove();
                        } else {
                            break;
                        }
                    }
                    map = new HashMap<>();
                    dayCache.put(dayIndex, map);
                }
                map.put(product, agg);
            }
            dayCache.notifyAll();
        }

        return agg;
    }

    // Normaliza o intervalo de dias pedidos: devolve array com índices dos dias anteriores (exclui dia atual).
    private int[] targetDays(int d) {
        if (d <= 0) throw new IllegalArgumentException("d must be >= 1");
        if (d > D) d = D;
        int current = dayManager.getDayIndex();
        int maxAvailable = current;
        int actual = Math.min(d, maxAvailable);
        int[] days = new int[actual];
        for (int i = 0; i < actual; i++) {
            days[i] = current - 1 - i;
        }
        return days;
    }

    public int aggregateQuantity(String productName, int d) throws IOException {
        int total = 0;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            total += p.quantity;
        }
        return total;
    }

    public double aggregateVolume(String productName, int d) throws IOException {
        double total = 0.0;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            total += p.volume;
        }
        return total;
    }

    public double aggregateAvgPrice(String productName, int d) throws IOException {
        long totalQty = 0;
        double totalVolume = 0.0;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            totalQty += p.quantity;
            totalVolume += p.volume;
        }
        if (totalQty == 0) return 0.0;
        return totalVolume / totalQty;
    }

    public double aggregateMaxPrice(String productName, int d) throws IOException {
        double max = 0.0;
        boolean any = false;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            if (p.countEvents > 0) {
                if (!any || p.maxPrice > max) max = p.maxPrice;
                any = true;
            }
        }
        return any ? max : 0.0;
    }

    // Limpa a cache
    public void clearCache() {
        synchronized (dayCache) {
            dayCache.clear();
        }
    }
}