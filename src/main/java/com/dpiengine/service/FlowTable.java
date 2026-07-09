package com.dpiengine.service;

import com.dpiengine.model.FiveTuple;
import com.dpiengine.model.Flow;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks in-progress flows for exactly one FastPath worker. Deliberately backed by a plain
 * HashMap rather than a ConcurrentHashMap: consistent hashing in the pipeline guarantees every
 * packet for a given FiveTuple always lands on the same FastPath thread, so this table is
 * only ever touched by one thread and a concurrent map would just add overhead for nothing.
 */
public final class FlowTable {

    private final Map<FiveTuple, Flow> flows = new HashMap<>();

    public Flow getOrCreate(FiveTuple key, long epochSeconds) {
        return flows.computeIfAbsent(key, k -> new Flow(k, epochSeconds));
    }

    public int activeFlowCount() {
        return flows.size();
    }
}
