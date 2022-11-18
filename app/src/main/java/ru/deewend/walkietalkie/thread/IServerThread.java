package ru.deewend.walkietalkie.thread;

import java.util.List;

public interface IServerThread {
    /* public */ List<IClientThread> getActiveConnections();

    default void closeConnectionsAsync() {
        synchronized (this) {
            List<IClientThread> activeConnections = getActiveConnections();
            for (IClientThread clientThread : activeConnections) {
                ((Thread) clientThread).interrupt();
            }
            activeConnections.clear();
        }
    }
}
