package com.dpiengine.config;

import com.dpiengine.model.AppType;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Fully resolved engine configuration. Instances are built by {@link CliArgumentParser} and
 * passed down to both the single-threaded and multi-threaded engines, so both entry points
 * stay in sync on what "the same run" means.
 */
public final class EngineOptions {

    private final Path inputFile;
    private final Path outputFile;
    private final Set<AppType> blockedAppTypes;
    private final Set<String> blockedDomainFragments;
    private final Set<Integer> blockedAddresses;
    private final int loadBalancerCount;
    private final int fastPathsPerLoadBalancer;

    private EngineOptions(Builder builder) {
        this.inputFile = builder.inputFile;
        this.outputFile = builder.outputFile;
        this.blockedAppTypes = Collections.unmodifiableSet(new HashSet<>(builder.blockedAppTypes));
        this.blockedDomainFragments = Collections.unmodifiableSet(new HashSet<>(builder.blockedDomainFragments));
        this.blockedAddresses = Collections.unmodifiableSet(new HashSet<>(builder.blockedAddresses));
        this.loadBalancerCount = builder.loadBalancerCount;
        this.fastPathsPerLoadBalancer = builder.fastPathsPerLoadBalancer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path inputFile() {
        return inputFile;
    }

    public Path outputFile() {
        return outputFile;
    }

    public Set<AppType> blockedAppTypes() {
        return blockedAppTypes;
    }

    public Set<String> blockedDomainFragments() {
        return blockedDomainFragments;
    }

    public Set<Integer> blockedAddresses() {
        return blockedAddresses;
    }

    public int loadBalancerCount() {
        return loadBalancerCount;
    }

    public int fastPathsPerLoadBalancer() {
        return fastPathsPerLoadBalancer;
    }

    public int totalFastPathCount() {
        return loadBalancerCount * fastPathsPerLoadBalancer;
    }

    public static final class Builder {
        private Path inputFile;
        private Path outputFile;
        private final Set<AppType> blockedAppTypes = new HashSet<>();
        private final Set<String> blockedDomainFragments = new HashSet<>();
        private final Set<Integer> blockedAddresses = new HashSet<>();
        private int loadBalancerCount = 2;
        private int fastPathsPerLoadBalancer = 2;

        public Builder inputFile(Path inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder outputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder addBlockedAppType(AppType appType) {
            this.blockedAppTypes.add(appType);
            return this;
        }

        public Builder addBlockedDomainFragment(String fragment) {
            this.blockedDomainFragments.add(fragment);
            return this;
        }

        public Builder addBlockedAddress(int address) {
            this.blockedAddresses.add(address);
            return this;
        }

        public Builder loadBalancerCount(int loadBalancerCount) {
            this.loadBalancerCount = loadBalancerCount;
            return this;
        }

        public Builder fastPathsPerLoadBalancer(int fastPathsPerLoadBalancer) {
            this.fastPathsPerLoadBalancer = fastPathsPerLoadBalancer;
            return this;
        }

        public EngineOptions build() {
            return new EngineOptions(this);
        }
    }
}
