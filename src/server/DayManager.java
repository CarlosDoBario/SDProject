package server;

import server.model.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestão do dia corrente.
 * Permite inicializar com um dia específico para suportar reinícios do servidor.
 */
public class DayManager {
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Event> currentDay = new ArrayList<>();
    private int dayIndex; // Removido o final para permitir inicialização

    private final ReentrantLock listenersLock = new ReentrantLock();
    private final List<EventListener> listeners = new ArrayList<>();

    public interface EventListener {
        void onEvent(Event e);
    }

    // Construtor essencial para a persistência: permite ao MainServer definir o dia atual
    public DayManager(int startDay) {
        this.dayIndex = startDay;
    }

    public void addEvent(Event e) {
        lock.lock();
        try {
            currentDay.add(e);
        } finally {
            lock.unlock();
        }
        notifyListeners(e);
    }

    private void notifyListeners(Event e) {
        List<EventListener> copy;
        listenersLock.lock();
        try {
            if (listeners.isEmpty()) return;
            copy = new ArrayList<>(listeners);
        } finally {
            listenersLock.unlock();
        }
        for (EventListener l : copy) {
            try {
                l.onEvent(e);
            } catch (Throwable t) {
                System.err.println("Listener error: " + t.getMessage());
            }
        }
    }

    public void addEventListener(EventListener listener) {
        listenersLock.lock();
        try {
            listeners.add(listener);
        } finally {
            listenersLock.unlock();
        }
    }

    public List<Event> closeCurrentDayAndStartNew() {
        lock.lock();
        try {
            List<Event> toPersist = new ArrayList<>(currentDay);
            currentDay.clear();
            dayIndex++;
            return toPersist;
        } finally {
            lock.unlock();
        }
    }

    public int getDayIndex() {
        lock.lock();
        try {
            return dayIndex;
        } finally {
            lock.unlock();
        }
    }
}