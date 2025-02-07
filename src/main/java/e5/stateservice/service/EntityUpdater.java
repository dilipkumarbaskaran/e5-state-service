package e5.stateservice.service;

@FunctionalInterface
public interface EntityUpdater<T> {
    void update(T entity);
}
