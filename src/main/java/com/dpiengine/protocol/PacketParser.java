package com.dpiengine.protocol;

import com.dpiengine.exception.PacketParseException;
import com.dpiengine.model.FiveTuple;
import com.dpiengine.model.ParsedPacket;
import com.dpiengine.model.RawPacket;
import com.dpiengine.util.NetworkByteReader;

/**
 * Walks a captured frame's Ethernet, IPv4, and TCP/UDP headers to build the FiveTuple and
 * locate the transport payload. Only IPv4 over Ethernet is supported, matching the original
 * engine; anything else is reported as unparseable rather than guessed at.
 */
public final class PacketParser {

    private static final int ETHERNET_HEADER_LENGTH = 14;
    private static final int ETHERTYPE_OFFSET = 12;
    private static final int ETHERTYPE_IPV4 = 0x0800;

    private static final int IP_PROTOCOL_TCP = 6;
    private static final int IP_PROTOCOL_UDP = 17;

    public ParsedPacket parse(RawPacket rawPacket) throws PacketParseException {
        byte[] data = rawPacket.data();
        if (data.length < ETHERNET_HEADER_LENGTH) {
            throw new PacketParseException("Frame shorter than an Ethernet header: " + data.length + " bytes");
        }

        int etherType = NetworkByteReader.readUnsignedShortBigEndian(data, ETHERTYPE_OFFSET);
        if (etherType != ETHERTYPE_IPV4) {
            throw new PacketParseException("Unsupported EtherType 0x" + Integer.toHexString(etherType));
        }

        int ipStart = ETHERNET_HEADER_LENGTH;
        if (data.length < ipStart + 20) {
            throw new PacketParseException("Frame too short for a minimal IPv4 header");
        }

        int versionAndIhl = NetworkByteReader.readUnsignedByte(data, ipStart);
        int version = (versionAndIhl >>> 4) & 0x0F;
        if (version != 4) {
            throw new PacketParseException("Unsupported IP version " + version);
        }
        int ipHeaderLength = (versionAndIhl & 0x0F) * 4;
        if (ipHeaderLength < 20 || data.length < ipStart + ipHeaderLength) {
            throw new PacketParseException("Invalid or truncated IPv4 header length: " + ipHeaderLength);
        }

        int protocolNumber = NetworkByteReader.readUnsignedByte(data, ipStart + 9);
        int sourceAddress = (int) NetworkByteReader.readUnsignedIntBigEndian(data, ipStart + 12);
        int destinationAddress = (int) NetworkByteReader.readUnsignedIntBigEndian(data, ipStart + 16);

        int transportStart = ipStart + ipHeaderLength;
        int sourcePort;
        int destinationPort;
        int payloadOffset;

        if (protocolNumber == IP_PROTOCOL_TCP) {
            if (data.length < transportStart + 20) {
                throw new PacketParseException("Frame too short for a minimal TCP header");
            }
            sourcePort = NetworkByteReader.readUnsignedShortBigEndian(data, transportStart);
            destinationPort = NetworkByteReader.readUnsignedShortBigEndian(data, transportStart + 2);
            int dataOffsetByte = NetworkByteReader.readUnsignedByte(data, transportStart + 12);
            int tcpHeaderLength = ((dataOffsetByte >>> 4) & 0x0F) * 4;
            if (tcpHeaderLength < 20 || data.length < transportStart + tcpHeaderLength) {
                throw new PacketParseException("Invalid or truncated TCP header length: " + tcpHeaderLength);
            }
            payloadOffset = transportStart + tcpHeaderLength;
        } else if (protocolNumber == IP_PROTOCOL_UDP) {
            if (data.length < transportStart + 8) {
                throw new PacketParseException("Frame too short for a UDP header");
            }
            sourcePort = NetworkByteReader.readUnsignedShortBigEndian(data, transportStart);
            destinationPort = NetworkByteReader.readUnsignedShortBigEndian(data, transportStart + 2);
            payloadOffset = transportStart + 8;
        } else {
            sourcePort = 0;
            destinationPort = 0;
            payloadOffset = transportStart;
        }

        int payloadLength = Math.max(0, data.length - payloadOffset);
        FiveTuple flowKey = new FiveTuple(sourceAddress, destinationAddress, sourcePort, destinationPort, protocolNumber);
        return new ParsedPacket(rawPacket, flowKey, payloadOffset, payloadLength, protocolNumber);
    }
}
