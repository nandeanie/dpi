package com.dpiengine;

import com.dpiengine.config.CliArgumentParser;
import com.dpiengine.config.EngineOptions;
import com.dpiengine.exception.EngineConfigurationException;
import com.dpiengine.exception.PcapFormatException;
import com.dpiengine.pipeline.DpiPipeline;
import com.dpiengine.service.BlockRuleSet;
import com.dpiengine.service.ReportPrinter;

import java.io.IOException;
import java.time.Duration;

/**
 * Entry point for the concurrent reader -&gt; load-balancer -&gt; fast-path -&gt; writer pipeline.
 * Functionally equivalent to {@link DpiEngineSingleThreaded}; the difference is entirely in
 * how the work is scheduled, not in what gets classified or blocked.
 */
public final class DpiEngineMultiThreaded {

    public static void main(String[] args) {
        try {
            EngineOptions options = new CliArgumentParser().parse(args);
            BlockRuleSet ruleSet = BlockRuleSet.fromOptions(options);
            DpiPipeline pipeline = new DpiPipeline(options, ruleSet);

            System.out.println("DPI Engine (multi-threaded) starting...");
            System.out.println("Input:  " + options.inputFile());
            System.out.println("Output: " + options.outputFile());
            System.out.println("Load balancers: " + options.loadBalancerCount()
                    + " | Fast paths per LB: " + options.fastPathsPerLoadBalancer()
                    + " | Total fast paths: " + options.totalFastPathCount());

            Duration elapsed = pipeline.run();
            new ReportPrinter().print(pipeline.statistics(), elapsed);
        } catch (EngineConfigurationException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println("Usage: --in <file.pcap> --out <file.pcap> "
                    + "[--block-app <APPTYPE>] [--block-domain <fragment>] [--block-ip <a.b.c.d>] "
                    + "[--load-balancers <n>] [--fast-paths-per-lb <n>]");
            System.exit(1);
        } catch (PcapFormatException e) {
            System.err.println("Malformed PCAP file: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(3);
        }
    }
}
