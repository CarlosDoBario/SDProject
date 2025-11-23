package server;

import server.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 Gestão do dia corrente (série do dia atual).
 */
public class DayManager {
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final List<Event> currentDay = new ArrayList<>();
    private int dayIndex = 0;

    private final List<EventListener> listeners = new ArrayList<>();

    // Interface simples para listeners que querem ser notificados de novos eventos.
    public interface EventListener {
        void onEvent(Event e);
    }

    // Adiciona um evento à série do dia corrente. Thread-safe.
    public void addEvent(Event e) {
        rw.writeLock().lock();
        try {
            currentDay.add(e);
        } finally {
            rw.writeLock().unlock();
        }
        notifyListeners(e);
    }

    private void notifyListeners(Event e) {
        List<EventListener> copy;
        synchronized (listeners) {
            if (listeners.isEmpty()) return;
            copy = new ArrayList<>(listeners);
        }
        for (EventListener l : copy) {
            try {
                l.onEvent(e);
            } catch (Throwable t) {
                System.err.println("Listener threw exception: " + t.getMessage());
            }
        }
    }

    // Regista um listener que será notificado quando novos eventos forem adicionados.
    public void addEventListener(EventListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    // Remove um listener previamente registado.
    public void removeEventListener(EventListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    // Tira um snapshot da série do dia corrente (cópia da lista).
    // Permite leituras concorrentes sem expor a colecção interna.
    public List<Event> snapshotCurrentDay() {
        rw.readLock().lock();
        try {
            return new ArrayList<>(currentDay);
        } finally {
            rw.readLock().unlock();
        }
    }

    // Fecha o dia corrente: devolve a lista de eventos a persistir e inicia uma nova série vazia
    public List<Event> closeCurrentDayAndStartNew() {
        rw.writeLock().lock();
        try {
            List<Event> toPersist = new ArrayList<>(currentDay);
            currentDay.clear();
            dayIndex += 1;
            return toPersist;
        } finally {
            rw.writeLock().unlock();
        }
    }

    // Índice do dia corrente. Incrementa na função anterior
    public int getDayIndex() {
        rw.readLock().lock();
        try {
            return dayIndex;
        } finally {
            rw.readLock().unlock();
        }
    }

    // Número de eventos atualmente registados no dia corrente.
    public int getCurrentDaySize() {
        rw.readLock().lock();
        try {
            return currentDay.size();
        } finally {
            rw.readLock().unlock();
        }
    }
}