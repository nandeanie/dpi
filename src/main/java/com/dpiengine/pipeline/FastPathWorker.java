package com.dpiengine.pipeline;

import com.dpiengine.model.AppType;
import com.dpiengine.model.Flow;
import com.dpiengine.model.ParsedPacket;
import com.dpiengine.model.ProcessingStatistics;
import com.dpiengine.model.RawPacket;
import com.dpiengine.protocol.AppTypeClassifier;
import com.dpiengine.protocol.HttpHostExtractor;
import com.dpiengine.protocol.TlsSniExtractor;
import com.dpiengine.service.BlockRuleSet;
import com.dpiengine.service.FlowTable;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;

/**
 * Owns exactly one FlowTable and performs the actual deep inspection: extracting a TLS SNI
 * or HTTP Host header, classifying the flow's AppType, and deciding whether the flow should
 * be blocked. Passing (non-blocked) packets are forwarded to the shared output queue for the
 * writer worker; blocked packets are dropped here and only counted.
 */
public final class FastPathWorker implements Runnable {

    private final int fastPathIndex;
    private final BlockingQueue<PipelineMessage<ParsedPacket>> inputQueue;
    private final BlockingQueue<PipelineMessage<RawPacket>> outputQueue;
    private final BlockRuleSet ruleSet;
    private final ProcessingStatistics statistics;
    private final FlowTable flowTable = new FlowTable();
    private final TlsSniExtractor sniExtractor = new TlsSniExtractor();
    private final HttpHostExtractor httpHostExtractor = new HttpHostExtractor();
    private final AppTypeClassifier appTypeClassifier = new AppTypeClassifier();

    public FastPathWorker(int fastPathIndex,
                           BlockingQueue<PipelineMessage<ParsedPacket>> inputQueue,
                           BlockingQueue<PipelineMessage<RawPacket>> outputQueue,
                           BlockRuleSet ruleSet,
                           ProcessingStatistics statistics) {
        this.fastPathIndex = fastPathIndex;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.ruleSet = ruleSet;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        try {
            while (true) {
                PipelineMessage<ParsedPacket> message = inputQueue.take();
                if (message.isPoison()) {
                    return;
                }
                process(message.payload());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FastPath " + fastPathIndex + " interrupted", e);
        }
    }

    private void process(ParsedPacket packet) throws InterruptedException {
        statistics.recordTransportProtocol(packet.protocolNumber());

        long epochSeconds = packet.rawPacket().header().timestampSeconds();
        Flow flow = flowTable.getOrCreate(packet.flowKey(), epochSeconds);
        flow.recordPacket(epochSeconds, packet.rawPacket().length());

        classify(packet, flow);

        boolean blocked = flow.isBlocked() || evaluateBlockRules(packet, flow);
        flow.setBlocked(blocked);

        if (blocked) {
            statistics.recordBlocked();
        } else {
            statistics.recordPassed();
            outputQueue.put(PipelineMessage.of(packet.rawPacket()));
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
}
