package com.dpiengine.model;

/**
 * Per-flow state owned exclusively by a single FastPath worker. Because the pipeline routes
 * every packet of a given FiveTuple to the same FastPath (consistent hashing), this class
 * needs no synchronization at all.
 */
public final class Flow {

    private final FiveTuple key;
    private AppType appType = AppType.UNKNOWN;
    private String serverName;
    private boolean blocked;
    private long packetCount;
    private long byteCount;
    private long firstSeenEpochSeconds;
    private long lastSeenEpochSeconds;

    public Flow(FiveTuple key, long firstSeenEpochSeconds) {
        this.key = key;
        this.firstSeenEpochSeconds = firstSeenEpochSeconds;
        this.lastSeenEpochSeconds = firstSeenEpochSeconds;
    }

    public void recordPacket(long epochSeconds, long packetBytes) {
        packetCount++;
        byteCount += packetBytes;
        lastSeenEpochSeconds = epochSeconds;
    }

    public FiveTuple key() {
        return key;
    }

    public AppType appType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType;
    }

    public String serverName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public long packetCount() {
        return packetCount;
    }

    public long byteCount() {
        return byteCount;
    }

    public long firstSeenEpochSeconds() {
        return firstSeenEpochSeconds;
    }

    public long lastSeenEpochSeconds() {
        return lastSeenEpochSeconds;
    }
}
