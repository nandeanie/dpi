package com.dpiengine.exception;

/**
 * Thrown when a file does not conform to the PCAP format: bad magic number, truncated
 * global header, or a per-packet header claiming more bytes than remain in the file.
 */
public class PcapFormatException extends Exception {

    public PcapFormatException(String message) {
        super(message);
    }

    public PcapFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
