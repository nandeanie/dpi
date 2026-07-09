package com.dpiengine.model;

import java.util.Objects;

/**
 * Identifies a single network flow by source/destination address, source/destination
 * port and transport protocol. Two packets with an equal FiveTuple belong to the same
 * conversation and must always be routed to the same FastPath worker so that flow state
 * (SNI, block decision, byte counters) stays consistent.
 */
public final class FiveTuple {

    private final int sourceAddress;
    private final int destinationAddress;
    private final int sourcePort;
    private final int destinationPort;
    private final int protocolNumber;

    public FiveTuple(int sourceAddress, int destinationAddress, int sourcePort,
                      int destinationPort, int protocolNumber) {
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.protocolNumber = protocolNumber;
    }

    public int sourceAddress() {
        return sourceAddress;
    }

    public int destinationAddress() {
        return destinationAddress;
    }

    public int sourcePort() {
        return sourcePort;
    }

    public int destinationPort() {
        return destinationPort;
    }

    public int protocolNumber() {
        return protocolNumber;
    }

    /**
     * Stable hash used for consistent hashing across the reader -> load-balancer -> fast-path
     * pipeline. Deliberately independent of {@link #hashCode()} so pipeline routing never
     * silently changes if the JVM's hashCode implementation changes.
     */
    public int routingHash() {
        int result = 17;
        result = 31 * result + sourceAddress;
        result = 31 * result + destinationAddress;
        result = 31 * result + sourcePort;
        result = 31 * result + destinationPort;
        result = 31 * result + protocolNumber;
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FiveTuple)) {
            return false;
        }
        FiveTuple that = (FiveTuple) other;
        return sourceAddress == that.sourceAddress
                && destinationAddress == that.destinationAddress
                && sourcePort == that.sourcePort
                && destinationPort == that.destinationPort
                && protocolNumber == that.protocolNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceAddress, destinationAddress, sourcePort, destinationPort, protocolNumber);
    }
}
