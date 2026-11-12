package com.dth.frw.shared.exception;

import java.util.Map;

/**
 * A request that is well-formed but breaks a business rule, naming the field at
 * fault.
 *
 * <p>Answered {@code 422} with the same {@code errors} map a failed bean
 * validation produces, so the form shows it under the input rather than as a
 * toast. The messages are keys the frontend maps to text, not prose.
 */
public class ValidationFailedException extends RuntimeException {

    private final transient Map<String, Object> errors;

    public ValidationFailedException(String message, Map<String, Object> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, Object> errors() {
        return errors;
    }
}
