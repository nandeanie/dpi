package com.dpiengine.protocol;

import com.dpiengine.model.ParsedPacket;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Looks for a plaintext HTTP request line followed by a Host header. Used as a fallback for
 * unencrypted traffic where {@link TlsSniExtractor} does not apply.
 */
public final class HttpHostExtractor {

    private static final String[] REQUEST_METHODS = {
            "GET ", "POST ", "PUT ", "HEAD ", "DELETE ", "OPTIONS ", "PATCH "
    };
    private static final String HOST_HEADER_PREFIX = "Host: ";
    private static final int MAX_SEARCH_BYTES = 2048;

    public Optional<String> extract(ParsedPacket packet) {
        byte[] buffer = packet.backingBuffer();
        int base = packet.payloadOffset();
        int length = Math.min(packet.payloadLength(), MAX_SEARCH_BYTES);

        if (length <= 0 || !looksLikeHttpRequest(buffer, base, length)) {
            return Optional.empty();
        }

        String text = new String(buffer, base, length, StandardCharsets.US_ASCII);
        int hostIndex = text.indexOf(HOST_HEADER_PREFIX);
        if (hostIndex < 0) {
            return Optional.empty();
        }

        int valueStart = hostIndex + HOST_HEADER_PREFIX.length();
        int lineEnd = text.indexOf('\r', valueStart);
        if (lineEnd < 0) {
            lineEnd = text.indexOf('\n', valueStart);
        }
        if (lineEnd < 0) {
            lineEnd = text.length();
        }

        String host = text.substring(valueStart, lineEnd).trim();
        return host.isEmpty() ? Optional.empty() : Optional.of(host);
    }

    private boolean looksLikeHttpRequest(byte[] buffer, int base, int length) {
        for (String method : REQUEST_METHODS) {
            byte[] methodBytes = method.getBytes(StandardCharsets.US_ASCII);
            if (length < methodBytes.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < methodBytes.length; i++) {
                if (buffer[base + i] != methodBytes[i]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
