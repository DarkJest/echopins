package dev.echopins.domain.event;

import dev.echopins.domain.event.DomainEvents.DomainEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A minimal synchronous event bus for domain facts.
 *
 * <p>Deliberately not NeoForge's event bus: these are EchoPins' own facts, they must fire in
 * unit tests with no mod loader present, and publishing them on a global bus would invite other
 * mods to depend on internals that are not a stable API in v1.
 *
 * <p>Listeners run synchronously on the publishing thread (the server thread). A listener that
 * throws is isolated so one broken consumer cannot abort a pin deletion halfway through.
 */
public final class DomainEventBus {

    @FunctionalInterface
    public interface ErrorHandler {
        void onListenerFailure(DomainEvent event, Throwable error);
    }

    private final Map<Class<?>, List<Consumer<? super DomainEvent>>> listeners = new ConcurrentHashMap<>();
    private final ErrorHandler errorHandler;

    public DomainEventBus(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>())
                .add((Consumer<? super DomainEvent>) listener);
    }

    public void publish(DomainEvent event) {
        List<Consumer<? super DomainEvent>> registered = listeners.get(event.getClass());
        if (registered == null) {
            return;
        }
        for (Consumer<? super DomainEvent> listener : registered) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                errorHandler.onListenerFailure(event, e);
            }
        }
    }

    /** Drops every listener. Used on server shutdown so nothing survives into the next world. */
    public void clear() {
        listeners.clear();
    }
}
