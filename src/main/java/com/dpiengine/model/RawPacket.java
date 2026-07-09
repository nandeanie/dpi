package com.dpiengine.model;

/**
 * A packet exactly as captured: the per-packet PCAP header plus the raw link-layer bytes.
 * Immutable so it can be handed between pipeline threads without defensive copying at every hop.
 */
public final class RawPacket {

    private final PcapPacketHeader header;
    private final byte[] data;

    public RawPacket(PcapPacketHeader header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    public PcapPacketHeader header() {
        return header;
    }

    public byte[] data() {
        return data;
    }

    public int length() {
        return data.length;
    }
}
