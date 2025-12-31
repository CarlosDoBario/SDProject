package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import server.model.Event;

public class FilterManager {
    private final DayManager dayManager;
    private final PersistenceManager persistenceManager;

    private final int D;
    private final int S;

    public FilterManager(DayManager dayManager, PersistenceManager persistenceManager) {
        this(7, 3, dayManager, persistenceManager);
    }

    public FilterManager(int D, int S, DayManager dayManager, PersistenceManager persistenceManager) {
        if (D <= 0) throw new IllegalArgumentException("D must be > 0");
        if (S < 0) throw new IllegalArgumentException("S must be >= 0");
        this.D = D;
        this.S = S;
        this.dayManager = dayManager;
        this.persistenceManager = persistenceManager;
    }

    private String listToString(List<Event> events){
        StringBuilder res = new StringBuilder(); 
        for (Event e : events) {
            res.append("quantity=" + e.getQuantity() +
            ", price=" + e.getPrice() +
            ", timestamp=" + e.getTimestamp() + "\n"); 
        }

        return res.toString();
    }
    private String serialize(Map<String, List<Event>> eventsMap, int nProducts) {
        StringBuilder res = new StringBuilder();
    
        for (Map.Entry<String, List<Event>> entry : eventsMap.entrySet()) {
            res.append(entry.getKey())
               .append("\n")
               .append(listToString(entry.getValue()))
               .append("\n");
        }
    
        return res.toString();
    }
    

    public String filterByProducts(int nProducts, String products, int day) throws IOException {
        Map<String, List<Event>> map = new HashMap<>();
        List<String> productsList = Arrays.asList(products.trim().split("\\s+"));
        for(String p : productsList){
            map.put(p, new ArrayList<>());
        }
        List<Event> dayEvents = new ArrayList<>();
        if (day == dayManager.getDayIndex()){
            for (Event e : dayManager.getDayEvents()){
                dayEvents.add(e);
            }
            
        }
        else if (persistenceManager.dayExists(day)) {
            persistenceManager.streamDay(day, e -> {
                dayEvents.add(e);
            });
        }
        else return "Não foram encontrados eventos";
        for (Event e : dayEvents) {
            String product = e.getProductName();
            if(productsList.contains(product)){
                map.computeIfAbsent(product, k -> new ArrayList<>())
               .add(e);
            }
        }

        String res = serialize(map, nProducts);
    return res;
    }
}
