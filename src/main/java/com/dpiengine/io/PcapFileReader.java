package com.dpiengine.io;

import com.dpiengine.exception.PcapFormatException;
import com.dpiengine.model.PcapGlobalHeader;
import com.dpiengine.model.PcapPacketHeader;
import com.dpiengine.model.RawPacket;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Streams packets out of a PCAP file one at a time. Buffered so the pipeline's reader thread
 * never pays for a syscall per packet, and closeable so callers can rely on try-with-resources
 * instead of remembering to release the file handle by hand.
 */
public final class PcapFileReader implements Closeable {

    private final InputStream input;
    private final PcapGlobalHeader globalHeader;

    private PcapFileReader(InputStream input, PcapGlobalHeader globalHeader) {
        this.input = input;
        this.globalHeader = globalHeader;
    }

    public static PcapFileReader open(Path path) throws IOException, PcapFormatException {
        InputStream input = new BufferedInputStream(Files.newInputStream(path), 1 << 16);
        byte[] headerBytes;
        try {
            headerBytes = readFully(input, PcapGlobalHeader.BYTE_LENGTH);
        } catch (EOFException tooShort) {
            input.close();
            throw new PcapFormatException("File is smaller than a PCAP global header: " + path);
        }
        if (headerBytes == null) {
            input.close();
            throw new PcapFormatException("File is smaller than a PCAP global header: " + path);
        }

        long magicLittleEndian = ByteBuffer.wrap(headerBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        ByteOrder order;
        if (magicLittleEndian == PcapGlobalHeader.MAGIC_LITTLE_ENDIAN) {
            order = ByteOrder.LITTLE_ENDIAN;
        } else if (magicLittleEndian == PcapGlobalHeader.MAGIC_SWAPPED) {
            order = ByteOrder.BIG_ENDIAN;
        } else {
            input.close();
            throw new PcapFormatException("Not a PCAP file (unrecognized magic number) at: " + path);
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(order);
        long magicNumber = buffer.getInt(0) & 0xFFFFFFFFL;
        int versionMajor = buffer.getShort(4) & 0xFFFF;
        int versionMinor = buffer.getShort(6) & 0xFFFF;
        int snapLength = buffer.getInt(16);
        int network = buffer.getInt(20);

        PcapGlobalHeader header = new PcapGlobalHeader(magicNumber, versionMajor, versionMinor, snapLength, network, order);
        return new PcapFileReader(input, header);
    }

    public PcapGlobalHeader globalHeader() {
        return globalHeader;
    }

    /**
     * Reads the next packet, or returns empty at a clean end of file. A file that ends in the
     * middle of a header or a packet body is reported as a format error rather than silently
     * truncating the capture.
     */
    public Optional<RawPacket> readNextPacket() throws IOException, PcapFormatException {
        byte[] headerBytes = readFully(input, PcapPacketHeader.BYTE_LENGTH);
        if (headerBytes == null) {
            return Optional.empty();
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(globalHeader.byteOrder());
        long timestampSeconds = buffer.getInt(0) & 0xFFFFFFFFL;
        long timestampMicros = buffer.getInt(4) & 0xFFFFFFFFL;
        long includedLength = buffer.getInt(8) & 0xFFFFFFFFL;
        long originalLength = buffer.getInt(12) & 0xFFFFFFFFL;

        if (includedLength > globalHeader.snapLength() && globalHeader.snapLength() > 0) {
            throw new PcapFormatException("Packet claims " + includedLength
                    + " captured bytes, exceeding the file's snap length of " + globalHeader.snapLength());
        }

        byte[] packetData = readFully(input, (int) includedLength);
        if (packetData == null) {
            throw new PcapFormatException("Unexpected end of file while reading a packet body");
        }

        PcapPacketHeader packetHeader = new PcapPacketHeader(timestampSeconds, timestampMicros, includedLength, originalLength);
        return Optional.of(new RawPacket(packetHeader, packetData));
    }

    /**
     * Reads exactly {@code length} bytes, or returns null if the stream is already at end of
     * file before any byte is read. Throws if the stream ends partway through, since a partial
     * header or packet indicates a corrupt or truncated capture rather than a clean EOF.
     */
    private static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = input.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                if (totalRead == 0) {
                    return null;
                }
                throw new EOFException("Stream ended after " + totalRead + " of " + length + " expected bytes");
            }
            totalRead += read;
        }
        return buffer;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
