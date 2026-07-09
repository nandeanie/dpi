package com.dpiengine.protocol;

import com.dpiengine.model.ParsedPacket;

import java.util.Optional;

/**
 * Extracts the Server Name Indication hostname from a TLS ClientHello, without a full TLS
 * stack. Walks the record layer -&gt; handshake -&gt; extensions chain far enough to find
 * extension type 0x0000 (server_name) and read the hostname out of it. Every offset is
 * bounds-checked against the payload length before use, since these bytes come straight off
 * the wire and a truncated capture must never crash the pipeline.
 */
public final class TlsSniExtractor {

    private static final int CONTENT_TYPE_HANDSHAKE = 0x16;
    private static final int HANDSHAKE_TYPE_CLIENT_HELLO = 0x01;
    private static final int EXTENSION_TYPE_SERVER_NAME = 0x0000;
    private static final int SERVER_NAME_TYPE_HOST_NAME = 0x00;

    public Optional<String> extract(ParsedPacket packet) {
        byte[] buffer = packet.backingBuffer();
        int base = packet.payloadOffset();
        int length = packet.payloadLength();

        if (length < 6) {
            return Optional.empty();
        }
        if (unsigned(buffer, base) != CONTENT_TYPE_HANDSHAKE) {
            return Optional.empty();
        }
        if (unsigned(buffer, base + 5) != HANDSHAKE_TYPE_CLIENT_HELLO) {
            return Optional.empty();
        }

        // TLS record header (5) + handshake header (4) + protocol version (2) + random (32)
        int cursor = base + 5 + 4 + 2 + 32;
        if (!withinBounds(base, length, cursor, 1)) {
            return Optional.empty();
        }

        int sessionIdLength = unsigned(buffer, cursor);
        cursor += 1 + sessionIdLength;
        if (!withinBounds(base, length, cursor, 2)) {
            return Optional.empty();
        }

        int cipherSuitesLength = readUnsignedShort(buffer, cursor);
        cursor += 2 + cipherSuitesLength;
        if (!withinBounds(base, length, cursor, 1)) {
            return Optional.empty();
        }

        int compressionMethodsLength = unsigned(buffer, cursor);
        cursor += 1 + compressionMethodsLength;
        if (!withinBounds(base, length, cursor, 2)) {
            return Optional.empty();
        }

        int extensionsTotalLength = readUnsignedShort(buffer, cursor);
        cursor += 2;
        int extensionsEnd = cursor + extensionsTotalLength;
        if (extensionsEnd > base + length) {
            return Optional.empty();
        }

        while (cursor + 4 <= extensionsEnd) {
            int extensionType = readUnsignedShort(buffer, cursor);
            int extensionLength = readUnsignedShort(buffer, cursor + 2);
            int extensionDataStart = cursor + 4;
            if (extensionDataStart + extensionLength > extensionsEnd) {
                return Optional.empty();
            }

            if (extensionType == EXTENSION_TYPE_SERVER_NAME) {
                return parseServerNameExtension(buffer, extensionDataStart, extensionLength);
            }
            cursor = extensionDataStart + extensionLength;
        }

        return Optional.empty();
    }

    private Optional<String> parseServerNameExtension(byte[] buffer, int start, int extensionLength) {
        if (extensionLength < 5) {
            return Optional.empty();
        }
        // server_name_list length (2) + name type (1) + host name length (2)
        int nameType = unsigned(buffer, start + 2);
        if (nameType != SERVER_NAME_TYPE_HOST_NAME) {
            return Optional.empty();
        }
        int hostNameLength = readUnsignedShort(buffer, start + 3);
        int hostNameStart = start + 5;
        if (hostNameLength <= 0 || hostNameStart + hostNameLength > start + extensionLength) {
            return Optional.empty();
        }
        return Optional.of(new String(buffer, hostNameStart, hostNameLength, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private boolean withinBounds(int base, int length, int cursor, int need) {
        return cursor + need <= base + length;
    }

    private int unsigned(byte[] buffer, int index) {
        return buffer[index] & 0xFF;
    }

    private int readUnsignedShort(byte[] buffer, int index) {
        return ((buffer[index] & 0xFF) << 8) | (buffer[index + 1] & 0xFF);
    }
}
