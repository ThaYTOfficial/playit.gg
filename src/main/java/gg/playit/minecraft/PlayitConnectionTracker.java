package gg.playit.minecraft;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tracker for active connections.
 * Uses ConcurrentHashMap for lock-free operations.
 */
public class PlayitConnectionTracker {
    private final Set<String> activeConnections = ConcurrentHashMap.newKeySet();

    public boolean addConnection(String key) {
        return activeConnections.add(key);
    }

    public void removeConnection(String key) {
        activeConnections.remove(key);
    }
    
    public int getActiveCount() {
        return activeConnections.size();
    }
}
