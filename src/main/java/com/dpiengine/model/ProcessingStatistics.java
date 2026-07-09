package com.dpiengine.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregate counters updated concurrently by every FastPath worker. Plain AtomicLong fields
 * are sufficient here and avoid the overhead and complexity of a lock: each counter is
 * updated independently and read only after the pipeline has finished.
 */
public final class ProcessingStatistics {

    private final AtomicLong totalPacketsRead = new AtomicLong();
    private final AtomicLong totalBytesRead = new AtomicLong();
    private final AtomicLong tcpPackets = new AtomicLong();
    private final AtomicLong udpPackets = new AtomicLong();
    private final AtomicLong otherPackets = new AtomicLong();
    private final AtomicLong sniMatchedPackets = new AtomicLong();
    private final AtomicLong httpHostMatchedPackets = new AtomicLong();
    private final AtomicLong blockedPackets = new AtomicLong();
    private final AtomicLong passedPackets = new AtomicLong();

    public void recordPacketRead(long byteLength) {
        totalPacketsRead.incrementAndGet();
        totalBytesRead.addAndGet(byteLength);
    }

    public void recordTransportProtocol(int protocolNumber) {
        if (protocolNumber == 6) {
            tcpPackets.incrementAndGet();
        } else if (protocolNumber == 17) {
            udpPackets.incrementAndGet();
        } else {
            otherPackets.incrementAndGet();
        }
    }

    public void recordSniMatch() {
        sniMatchedPackets.incrementAndGet();
    }

    public void recordHttpHostMatch() {
        httpHostMatchedPackets.incrementAndGet();
    }

    public void recordBlocked() {
        blockedPackets.incrementAndGet();
    }

    public void recordPassed() {
        passedPackets.incrementAndGet();
    }

    public long totalPacketsRead() {
        return totalPacketsRead.get();
    }

    public long totalBytesRead() {
        return totalBytesRead.get();
    }

    public long tcpPackets() {
        return tcpPackets.get();
    }

    public long udpPackets() {
        return udpPackets.get();
    }

    public long otherPackets() {
        return otherPackets.get();
    }

    public long sniMatchedPackets() {
        return sniMatchedPackets.get();
    }

    public long httpHostMatchedPackets() {
        return httpHostMatchedPackets.get();
    }

    public long blockedPackets() {
        return blockedPackets.get();
    }

    public long passedPackets() {
        return passedPackets.get();
    }
}
