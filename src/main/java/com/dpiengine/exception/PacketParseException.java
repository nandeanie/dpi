package com.dpiengine.exception;

/**
 * Thrown when a packet's link-layer or network-layer headers are too short or otherwise
 * malformed to parse safely. Callers on the hot path generally catch this, count it as a
 * skipped packet, and continue rather than aborting the whole run.
 */
public class PacketParseException extends Exception {

    public PacketParseException(String message) {
        super(message);
    }
}
