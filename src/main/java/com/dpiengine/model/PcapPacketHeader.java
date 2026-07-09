package com.dpiengine.model;

/**
 * The 16-byte header that precedes every captured packet's bytes in a PCAP file.
 */
public final class PcapPacketHeader {

    public static final int BYTE_LENGTH = 16;

    private final long timestampSeconds;
    private final long timestampMicros;
    private final long includedLength;
    private final long originalLength;

    public PcapPacketHeader(long timestampSeconds, long timestampMicros,
                             long includedLength, long originalLength) {
        this.timestampSeconds = timestampSeconds;
        this.timestampMicros = timestampMicros;
        this.includedLength = includedLength;
        this.originalLength = originalLength;
    }

    public long timestampSeconds() {
        return timestampSeconds;
    }

    public long timestampMicros() {
        return timestampMicros;
    }

    public long includedLength() {
        return includedLength;
    }

    public long originalLength() {
        return originalLength;
    }
}
