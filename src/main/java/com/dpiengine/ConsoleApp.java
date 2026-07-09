package com.dpiengine;

import com.dpiengine.config.EngineOptions;
import com.dpiengine.exception.PcapFormatException;
import com.dpiengine.model.AppType;
import com.dpiengine.pipeline.DpiPipeline;
import com.dpiengine.service.BlockRuleSet;
import com.dpiengine.service.ReportPrinter;
import com.dpiengine.util.IpAddressFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Scanner;

/**
 * Interactive front door for people who would rather answer a few prompts than remember CLI
 * flags. Both {@link DpiEngineSingleThreaded} and {@link DpiEngineMultiThreaded} remain
 * available as direct, scriptable entry points; this class just builds the same
 * {@link EngineOptions} from validated console input instead of argv.
 */
public final class ConsoleApp {

    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        new ConsoleApp().start();
    }

    private void start() {
        printBanner();

        Path inputFile = promptExistingFile("Path to input .pcap file: ");
        Path outputFile = promptOutputFile("Path to output .pcap file: ");
        boolean multiThreaded = promptYesNo("Use the multi-threaded pipeline? (y/n): ");

        EngineOptions.Builder builder = EngineOptions.builder()
                .inputFile(inputFile)
                .outputFile(outputFile);

        configureBlockRules(builder);

        if (multiThreaded) {
            int loadBalancers = promptPositiveInt("Number of load balancer threads [2]: ", 2);
            int fastPathsPerLb = promptPositiveInt("Fast path threads per load balancer [2]: ", 2);
            builder.loadBalancerCount(loadBalancers).fastPathsPerLoadBalancer(fastPathsPerLb);
        }

        EngineOptions options = builder.build();
        BlockRuleSet ruleSet = BlockRuleSet.fromOptions(options);

        try {
            System.out.println();
            System.out.println("Processing... this may take a moment for large captures.");

            Duration elapsed;
            com.dpiengine.model.ProcessingStatistics statistics;
            if (multiThreaded) {
                DpiPipeline pipeline = new DpiPipeline(options, ruleSet);
                elapsed = pipeline.run();
                statistics = pipeline.statistics();
            } else {
                DpiEngineSingleThreaded engine = new DpiEngineSingleThreaded(options, ruleSet);
                elapsed = engine.run();
                statistics = engine.statistics();
            }

            System.out.println();
            new ReportPrinter().print(statistics, elapsed);
            System.out.println("Done. Filtered capture written to: " + outputFile);
        } catch (PcapFormatException e) {
            System.err.println("The input file is not a valid PCAP capture: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error while processing the capture: " + e.getMessage());
        }
    }

    private void configureBlockRules(EngineOptions.Builder builder) {
        if (!promptYesNo("Configure any blocking rules? (y/n): ")) {
            return;
        }

        System.out.println("Enter app types to block, one per line (blank line to stop).");
        System.out.println("Options: " + String.join(", ", appTypeNames()));
        while (true) {
            System.out.print("  Block app type: ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                break;
            }
            try {
                builder.addBlockedAppType(AppType.valueOf(line.toUpperCase(Locale.ROOT)));
                System.out.println("  Added.");
            } catch (IllegalArgumentException e) {
                System.out.println("  Not a recognized app type, skipped.");
            }
        }

        System.out.println("Enter domain fragments to block, one per line (blank line to stop).");
        while (true) {
            System.out.print("  Block domain containing: ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                break;
            }
            builder.addBlockedDomainFragment(line);
            System.out.println("  Added.");
        }

        System.out.println("Enter IPv4 addresses to block, one per line (blank line to stop).");
        while (true) {
            System.out.print("  Block IP address: ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                break;
            }
            try {
                builder.addBlockedAddress(IpAddressFormatter.fromDottedQuad(line));
                System.out.println("  Added.");
            } catch (IllegalArgumentException e) {
                System.out.println("  Not a valid IPv4 address, skipped.");
            }
        }
    }

    private String[] appTypeNames() {
        AppType[] values = AppType.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    private Path promptExistingFile(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            Path path = Paths.get(line);
            if (Files.isRegularFile(path)) {
                return path;
            }
            System.out.println("  That file does not exist, try again.");
        }
    }

    private Path promptOutputFile(String prompt) {
        System.out.print(prompt);
        return Paths.get(scanner.nextLine().trim());
    }

    private boolean promptYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            if (line.equals("y") || line.equals("yes")) {
                return true;
            }
            if (line.equals("n") || line.equals("no")) {
                return false;
            }
            System.out.println("  Please answer y or n.");
        }
    }

    private int promptPositiveInt(String prompt, int defaultValue) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                return defaultValue;
            }
            try {
                int value = Integer.parseInt(line);
                if (value > 0) {
                    return value;
                }
                System.out.println("  Must be a positive integer.");
            } catch (NumberFormatException e) {
                System.out.println("  Not a number, try again.");
            }
        }
    }

    private void printBanner() {
        System.out.println("=".repeat(56));
        System.out.println("      DPI Packet Analyzer - Interactive Console");
        System.out.println("=".repeat(56));
        System.out.println();
    }
}
