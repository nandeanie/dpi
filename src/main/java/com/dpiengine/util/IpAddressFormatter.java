package com.dpiengine.util;

/**
 * Converts the 32-bit integer representation of an IPv4 address used throughout the engine
 * into the familiar dotted-quad string, and back.
 */
public final class IpAddressFormatter {

    private IpAddressFormatter() {
    }

    public static String toDottedQuad(int address) {
        return ((address >>> 24) & 0xFF) + "."
                + ((address >>> 16) & 0xFF) + "."
                + ((address >>> 8) & 0xFF) + "."
                + (address & 0xFF);
    }

    public static int fromDottedQuad(String dottedQuad) {
        String[] parts = dottedQuad.trim().split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Not a valid IPv4 address: " + dottedQuad);
        }
        int result = 0;
        for (String part : parts) {
            int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Octet out of range in address: " + dottedQuad);
            }
            result = (result << 8) | octet;
        }
        return result;
    }
}
