package com.dpiengine.model;

import java.nio.ByteOrder;

/**
 * The 24-byte global header at the start of every PCAP file.
 */
public final class PcapGlobalHeader {

    public static final int BYTE_LENGTH = 24;
    public static final long MAGIC_LITTLE_ENDIAN = 0xa1b2c3d4L;
    public static final long MAGIC_SWAPPED = 0xd4c3b2a1L;

    private final long magicNumber;
    private final int versionMajor;
    private final int versionMinor;
    private final int snapLength;
    private final int network;
    private final ByteOrder byteOrder;

    public PcapGlobalHeader(long magicNumber, int versionMajor, int versionMinor,
                             int snapLength, int network, ByteOrder byteOrder) {
        this.magicNumber = magicNumber;
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.snapLength = snapLength;
        this.network = network;
        this.byteOrder = byteOrder;
    }

    public long magicNumber() {
        return magicNumber;
    }

    public int versionMajor() {
        return versionMajor;
    }

    public int versionMinor() {
        return versionMinor;
    }

    public int snapLength() {
        return snapLength;
    }

    public int network() {
        return network;
    }

    public ByteOrder byteOrder() {
        return byteOrder;
    }
}
