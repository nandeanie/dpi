package com.dpiengine.exception;

/**
 * Thrown when command-line arguments or engine configuration are invalid, e.g. a missing
 * input file path or a thread-count of zero.
 */
public class EngineConfigurationException extends RuntimeException {

    public EngineConfigurationException(String message) {
        super(message);
    }
}
