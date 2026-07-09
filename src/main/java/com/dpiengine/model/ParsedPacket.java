package com.dpiengine.model;

/**
 * Result of parsing a RawPacket's Ethernet/IP/TCP or UDP headers. Holds the five-tuple,
 * the offset at which the L4 payload begins, and a reference back to the original packet
 * bytes so no payload copy is needed until something actually needs to inspect it.
 */
public final class ParsedPacket {

    private final RawPacket rawPacket;
    private final FiveTuple flowKey;
    private final int payloadOffset;
    private final int payloadLength;
    private final int protocolNumber;

    public ParsedPacket(RawPacket rawPacket, FiveTuple flowKey, int payloadOffset,
                         int payloadLength, int protocolNumber) {
        this.rawPacket = rawPacket;
        this.flowKey = flowKey;
        this.payloadOffset = payloadOffset;
        this.payloadLength = payloadLength;
        this.protocolNumber = protocolNumber;
    }

    public RawPacket rawPacket() {
        return rawPacket;
    }

    public FiveTuple flowKey() {
        return flowKey;
    }

    public int payloadOffset() {
        return payloadOffset;
    }

    public int payloadLength() {
        return payloadLength;
    }

    public int protocolNumber() {
        return protocolNumber;
    }

    public boolean hasPayload() {
        return payloadLength > 0;
    }

    /**
     * Returns the byte array backing the payload and lets the caller index into it starting
     * at {@link #payloadOffset()}. Avoiding an eager copy here matters: most packets are never
     * deep-inspected past the five-tuple, so paying for a copy on every packet would be wasted
     * work on a hot path.
     */
    public byte[] backingBuffer() {
        return rawPacket.data();
    }
}
