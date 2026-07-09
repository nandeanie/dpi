package com.dpiengine.service;

import com.dpiengine.model.AppType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Holds the set of blocked applications, domains, and IPv4 addresses and decides whether a
 * given flow should be dropped. Built once from CLI configuration and shared read-only across
 * every FastPath worker, so it needs no synchronization after construction.
 */
public final class BlockRuleSet {

    private final Set<AppType> blockedAppTypes;
    private final Set<String> blockedDomainFragments;
    private final Set<Integer> blockedAddresses;

    public BlockRuleSet(Set<AppType> blockedAppTypes, Set<String> blockedDomainFragments, Set<Integer> blockedAddresses) {
        this.blockedAppTypes = Collections.unmodifiableSet(new HashSet<>(blockedAppTypes));
        this.blockedDomainFragments = Collections.unmodifiableSet(toLowerCase(blockedDomainFragments));
        this.blockedAddresses = Collections.unmodifiableSet(new HashSet<>(blockedAddresses));
    }

    public static BlockRuleSet empty() {
        return new BlockRuleSet(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    public static BlockRuleSet fromOptions(com.dpiengine.config.EngineOptions options) {
        return new BlockRuleSet(options.blockedAppTypes(), options.blockedDomainFragments(), options.blockedAddresses());
    }

    public boolean isAppTypeBlocked(AppType appType) {
        return blockedAppTypes.contains(appType);
    }

    public boolean isHostnameBlocked(String hostname) {
        if (hostname == null || blockedDomainFragments.isEmpty()) {
            return false;
        }
        String lower = hostname.toLowerCase(Locale.ROOT);
        for (String fragment : blockedDomainFragments) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAddressBlocked(int address) {
        return blockedAddresses.contains(address);
    }

    private static Set<String> toLowerCase(Set<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
