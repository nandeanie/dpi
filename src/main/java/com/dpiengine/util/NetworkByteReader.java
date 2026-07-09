package com.dpiengine.util;

/**
 * Small helpers for reading multi-byte network header fields. Network byte order (big-endian)
 * applies to every field inside Ethernet/IP/TCP/UDP headers regardless of the PCAP file's own
 * byte order, which only governs the PCAP record headers themselves.
 */
public final class NetworkByteReader {

    private NetworkByteReader() {
    }

    public static int readUnsignedShortBigEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static long readUnsignedIntBigEndian(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    public static int readUnsignedByte(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }
}
