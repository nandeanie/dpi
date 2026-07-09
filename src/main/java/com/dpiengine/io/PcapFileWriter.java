package com.dpiengine.io;

import com.dpiengine.model.PcapGlobalHeader;
import com.dpiengine.model.RawPacket;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes packets back out to a new PCAP file, preserving the original global header's
 * snap length and link-layer type. Buffered output keeps this efficient even when the
 * multi-threaded pipeline funnels every worker's output through a single writer.
 */
public final class PcapFileWriter implements Closeable {

    private final OutputStream output;
    private final ByteOrder byteOrder;

    private PcapFileWriter(OutputStream output, ByteOrder byteOrder) {
        this.output = output;
        this.byteOrder = byteOrder;
    }

    public static PcapFileWriter create(Path path, PcapGlobalHeader sourceHeader) throws IOException {
        OutputStream output = new BufferedOutputStream(Files.newOutputStream(path), 1 << 16);
        ByteBuffer header = ByteBuffer.allocate(PcapGlobalHeader.BYTE_LENGTH).order(sourceHeader.byteOrder());
        header.putInt(0, (int) PcapGlobalHeader.MAGIC_LITTLE_ENDIAN);
        header.putShort(4, (short) sourceHeader.versionMajor());
        header.putShort(6, (short) sourceHeader.versionMinor());
        header.putInt(8, 0);
        header.putInt(12, 0);
        header.putInt(16, sourceHeader.snapLength());
        header.putInt(20, sourceHeader.network());
        output.write(header.array());
        return new PcapFileWriter(output, sourceHeader.byteOrder());
    }

    public synchronized void writePacket(RawPacket packet) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(16).order(byteOrder);
        header.putInt(0, (int) packet.header().timestampSeconds());
        header.putInt(4, (int) packet.header().timestampMicros());
        header.putInt(8, (int) packet.header().includedLength());
        header.putInt(12, (int) packet.header().originalLength());
        output.write(header.array());
        output.write(packet.data());
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
