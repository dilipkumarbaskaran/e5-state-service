package e5.stateservice.service;

/**
 * Functional interface for property validation and parsing.
 *
 * @param <T> The type of the property value.
 */
@FunctionalInterface
public interface Validator<T> {
    T parse(String value) throws Exception;

    default boolean isValid(T value) {
        return true;
    }
}
