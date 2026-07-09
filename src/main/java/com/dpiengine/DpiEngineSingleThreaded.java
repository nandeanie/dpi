package com.dpiengine;

import com.dpiengine.config.CliArgumentParser;
import com.dpiengine.config.EngineOptions;
import com.dpiengine.exception.EngineConfigurationException;
import com.dpiengine.exception.PcapFormatException;
import com.dpiengine.io.PcapFileReader;
import com.dpiengine.io.PcapFileWriter;
import com.dpiengine.model.AppType;
import com.dpiengine.model.Flow;
import com.dpiengine.model.ParsedPacket;
import com.dpiengine.model.ProcessingStatistics;
import com.dpiengine.model.RawPacket;
import com.dpiengine.protocol.AppTypeClassifier;
import com.dpiengine.protocol.HttpHostExtractor;
import com.dpiengine.protocol.PacketParser;
import com.dpiengine.protocol.TlsSniExtractor;
import com.dpiengine.service.BlockRuleSet;
import com.dpiengine.service.FlowTable;
import com.dpiengine.service.ReportPrinter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Single-threaded reference engine: reads one packet at a time, inspects it, and writes
 * whatever is not blocked straight back out. This is the direct port of the original
 * project's simple, non-parallel processing path and is a useful correctness baseline for
 * the multi-threaded {@link com.dpiengine.pipeline.DpiPipeline}.
 */
public final class DpiEngineSingleThreaded {

    private final EngineOptions options;
    private final BlockRuleSet ruleSet;
    private final PacketParser packetParser = new PacketParser();
    private final TlsSniExtractor sniExtractor = new TlsSniExtractor();
    private final HttpHostExtractor httpHostExtractor = new HttpHostExtractor();
    private final AppTypeClassifier appTypeClassifier = new AppTypeClassifier();
    private final FlowTable flowTable = new FlowTable();
    private final ProcessingStatistics statistics = new ProcessingStatistics();

    public DpiEngineSingleThreaded(EngineOptions options, BlockRuleSet ruleSet) {
        this.options = options;
        this.ruleSet = ruleSet;
    }

    public ProcessingStatistics statistics() {
        return statistics;
    }

    public Duration run() throws IOException, PcapFormatException {
        Instant start = Instant.now();

        try (PcapFileReader reader = PcapFileReader.open(options.inputFile());
             PcapFileWriter writer = PcapFileWriter.create(options.outputFile(), reader.globalHeader())) {

            Optional<RawPacket> next;
            while ((next = reader.readNextPacket()).isPresent()) {
                RawPacket rawPacket = next.get();
                statistics.recordPacketRead(rawPacket.length());
                processPacket(rawPacket, writer);
            }
        }

        return Duration.between(start, Instant.now());
    }

    private void processPacket(RawPacket rawPacket, PcapFileWriter writer) throws IOException {
        ParsedPacket parsed;
        try {
            parsed = packetParser.parse(rawPacket);
        } catch (Exception unparsable) {
            return;
        }

        statistics.recordTransportProtocol(parsed.protocolNumber());

        Flow flow = flowTable.getOrCreate(parsed.flowKey(), rawPacket.header().timestampSeconds());
        flow.recordPacket(rawPacket.header().timestampSeconds(), rawPacket.length());

        classify(parsed, flow);

        boolean blocked = flow.isBlocked() || evaluateBlockRules(parsed, flow);
        flow.setBlocked(blocked);

        if (blocked) {
            statistics.recordBlocked();
        } else {
            statistics.recordPassed();
            writer.writePacket(rawPacket);
        }
    }

    private void classify(ParsedPacket packet, Flow flow) {
        if (!packet.hasPayload()) {
            return;
        }

        Optional<String> sni = sniExtractor.extract(packet);
        if (sni.isPresent()) {
            flow.setServerName(sni.get());
            flow.setAppType(appTypeClassifier.classifyByHostname(sni.get()));
            statistics.recordSniMatch();
            return;
        }

        Optional<String> host = httpHostExtractor.extract(packet);
        if (host.isPresent()) {
            flow.setServerName(host.get());
            flow.setAppType(appTypeClassifier.classifyByHostname(host.get()));
            statistics.recordHttpHostMatch();
            return;
        }

        if (flow.appType() == AppType.UNKNOWN) {
            AppType byPort = appTypeClassifier.classifyByWellKnownPort(
                    packet.flowKey().destinationPort(), packet.flowKey().sourcePort());
            if (byPort != AppType.UNKNOWN) {
                flow.setAppType(byPort);
            }
        }
    }

    private boolean evaluateBlockRules(ParsedPacket packet, Flow flow) {
        if (ruleSet.isAppTypeBlocked(flow.appType())) {
            return true;
        }
        if (ruleSet.isHostnameBlocked(flow.serverName())) {
            return true;
        }
        return ruleSet.isAddressBlocked(packet.flowKey().sourceAddress())
                || ruleSet.isAddressBlocked(packet.flowKey().destinationAddress());
    }

    public static void main(String[] args) {
        try {
            EngineOptions options = new CliArgumentParser().parse(args);
            BlockRuleSet ruleSet = BlockRuleSet.fromOptions(options);
            DpiEngineSingleThreaded engine = new DpiEngineSingleThreaded(options, ruleSet);

            System.out.println("DPI Engine (single-threaded) starting...");
            System.out.println("Input:  " + options.inputFile());
            System.out.println("Output: " + options.outputFile());

            Duration elapsed = engine.run();
            new ReportPrinter().print(engine.statistics(), elapsed);
        } catch (EngineConfigurationException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println("Usage: --in <file.pcap> --out <file.pcap> "
                    + "[--block-app <APPTYPE>] [--block-domain <fragment>] [--block-ip <a.b.c.d>]");
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
