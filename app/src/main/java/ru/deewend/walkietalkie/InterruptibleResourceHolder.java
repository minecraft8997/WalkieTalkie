package ru.deewend.walkietalkie;

import java.util.Objects;

@SuppressWarnings("unused")
public class InterruptibleResourceHolder<T extends AutoCloseable> {
    private volatile boolean allowedToSet = true;
    private volatile T resource;

    public InterruptibleResourceHolder() {
    }

    public T set(T resource) throws InterruptedException {
        Objects.requireNonNull(resource);

        synchronized (this) {
            if (allowedToSet) {
                if (this.resource != null) {
                    throw new IllegalStateException("Resource already set");
                }
                this.resource = resource;

                return resource;
            }
        }

        throw new InterruptedException();
    }

    public T get() {
        return resource;
    }

    @SuppressWarnings("EmptyTryBlock")
    public boolean interrupt() throws Exception {
        synchronized (this) {
            if (!allowedToSet) return false;

            allowedToSet = false;
        }

        T resource = this.resource;
        if (resource == null) return false;
        try (T ignored = resource) { /* empty body */ }

        return true;
    }
}
