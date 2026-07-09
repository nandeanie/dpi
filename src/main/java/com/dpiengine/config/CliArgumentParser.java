package com.dpiengine.config;

import com.dpiengine.exception.EngineConfigurationException;
import com.dpiengine.model.AppType;
import com.dpiengine.util.IpAddressFormatter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Parses the engine's command-line interface:
 * <pre>
 *   --in &lt;file.pcap&gt;              input capture (required)
 *   --out &lt;file.pcap&gt;             output capture (required)
 *   --block-app &lt;name&gt;            repeatable; blocks an AppType by name (e.g. youtube)
 *   --block-domain &lt;fragment&gt;     repeatable; blocks any hostname containing this fragment
 *   --block-ip &lt;a.b.c.d&gt;          repeatable; blocks a specific IPv4 address
 *   --load-balancers &lt;n&gt;          multi-threaded mode only, default 2
 *   --fast-paths-per-lb &lt;n&gt;       multi-threaded mode only, default 2
 * </pre>
 */
public final class CliArgumentParser {

    public EngineOptions parse(String[] args) {
        EngineOptions.Builder builder = EngineOptions.builder();

        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            switch (flag) {
                case "--in":
                    builder.inputFile(requirePath(args, ++i, flag));
                    break;
                case "--out":
                    builder.outputFile(requirePath(args, ++i, flag));
                    break;
                case "--block-app":
                    builder.addBlockedAppType(parseAppType(requireValue(args, ++i, flag)));
                    break;
                case "--block-domain":
                    builder.addBlockedDomainFragment(requireValue(args, ++i, flag).toLowerCase(Locale.ROOT));
                    break;
                case "--block-ip":
                    builder.addBlockedAddress(IpAddressFormatter.fromDottedQuad(requireValue(args, ++i, flag)));
                    break;
                case "--load-balancers":
                    builder.loadBalancerCount(requirePositiveInt(args, ++i, flag));
                    break;
                case "--fast-paths-per-lb":
                    builder.fastPathsPerLoadBalancer(requirePositiveInt(args, ++i, flag));
                    break;
                default:
                    throw new EngineConfigurationException("Unrecognized argument: " + flag);
            }
            i++;
        }

        EngineOptions options = builder.build();
        if (options.inputFile() == null) {
            throw new EngineConfigurationException("Missing required argument: --in <file.pcap>");
        }
        if (options.outputFile() == null) {
            throw new EngineConfigurationException("Missing required argument: --out <file.pcap>");
        }
        return options;
    }

    private AppType parseAppType(String name) {
        try {
            return AppType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EngineConfigurationException("Unknown app type for --block-app: " + name);
        }
    }

    private Path requirePath(String[] args, int index, String flag) {
        return Paths.get(requireValue(args, index, flag));
    }

    private int requirePositiveInt(String[] args, int index, String flag) {
        String value = requireValue(args, index, flag);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new EngineConfigurationException(flag + " must be a positive integer, got: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new EngineConfigurationException(flag + " must be an integer, got: " + value);
        }
    }

    private String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new EngineConfigurationException("Missing value for argument: " + flag);
        }
        return args[index];
    }
}
