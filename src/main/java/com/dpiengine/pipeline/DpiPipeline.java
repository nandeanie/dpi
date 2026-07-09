package com.dpiengine.pipeline;

import com.dpiengine.config.EngineOptions;
import com.dpiengine.exception.PcapFormatException;
import com.dpiengine.io.PcapFileReader;
import com.dpiengine.io.PcapFileWriter;
import com.dpiengine.model.ParsedPacket;
import com.dpiengine.model.ProcessingStatistics;
import com.dpiengine.model.RawPacket;
import com.dpiengine.protocol.PacketParser;
import com.dpiengine.service.BlockRuleSet;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Builds and drives the concurrent reader -&gt; load-balancer -&gt; fast-path -&gt; writer pipeline
 * described in the original engine's multi-threaded design. Every worker runs on Java's
 * {@link ExecutorService} rather than a hand-rolled Thread, and every queue is a bounded
 * {@link BlockingQueue} so a slow writer naturally applies backpressure instead of letting
 * memory grow without limit.
 */
public final class DpiPipeline {

    private static final int QUEUE_CAPACITY = 4096;

    private final EngineOptions options;
    private final BlockRuleSet ruleSet;
    private final PacketParser packetParser = new PacketParser();
    private final ProcessingStatistics statistics = new ProcessingStatistics();

    public DpiPipeline(EngineOptions options, BlockRuleSet ruleSet) {
        this.options = options;
        this.ruleSet = ruleSet;
    }

    public ProcessingStatistics statistics() {
        return statistics;
    }

    public Duration run() throws IOException, PcapFormatException {
        Instant start = Instant.now();

        int loadBalancerCount = options.loadBalancerCount();
        int fastPathsPerLb = options.fastPathsPerLoadBalancer();
        int totalFastPaths = loadBalancerCount * fastPathsPerLb;

        List<BlockingQueue<PipelineMessage<ParsedPacket>>> lbInputQueues = newQueues(loadBalancerCount);
        List<BlockingQueue<PipelineMessage<ParsedPacket>>> fpInputQueues = newQueues(totalFastPaths);
        BlockingQueue<PipelineMessage<RawPacket>> outputQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        ExecutorService executor = Executors.newFixedThreadPool(loadBalancerCount + totalFastPaths + 1);

        try (PcapFileReader reader = PcapFileReader.open(options.inputFile());
             PcapFileWriter writer = PcapFileWriter.create(options.outputFile(), reader.globalHeader())) {

            OutputWriterWorker writerWorker = new OutputWriterWorker(outputQueue, writer);
            Future<?> writerFuture = executor.submit(writerWorker);

            List<Future<?>> fastPathFutures = new ArrayList<>(totalFastPaths);
            for (int i = 0; i < totalFastPaths; i++) {
                FastPathWorker worker = new FastPathWorker(i, fpInputQueues.get(i), outputQueue, ruleSet, statistics);
                fastPathFutures.add(executor.submit(worker));
            }

            List<Future<?>> loadBalancerFutures = new ArrayList<>(loadBalancerCount);
            for (int i = 0; i < loadBalancerCount; i++) {
                List<BlockingQueue<PipelineMessage<ParsedPacket>>> slice =
                        fpInputQueues.subList(i * fastPathsPerLb, (i + 1) * fastPathsPerLb);
                LoadBalancerWorker worker = new LoadBalancerWorker(i, lbInputQueues.get(i), slice);
                loadBalancerFutures.add(executor.submit(worker));
            }

            readAndDispatch(reader, lbInputQueues);

            for (BlockingQueue<PipelineMessage<ParsedPacket>> queue : lbInputQueues) {
                queue.put(PipelineMessage.poisonPill());
            }

            awaitAll(loadBalancerFutures, "load balancer");
            awaitAll(fastPathFutures, "fast path");

            outputQueue.put(PipelineMessage.poisonPill());
            awaitAll(List.of(writerFuture), "output writer");

            if (writerWorker.failure() != null) {
                throw writerWorker.failure();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pipeline interrupted while shutting down", e);
        } finally {
            executor.shutdown();
            awaitTermination(executor);
        }

        return Duration.between(start, Instant.now());
    }

    private void readAndDispatch(PcapFileReader reader,
                                  List<BlockingQueue<PipelineMessage<ParsedPacket>>> lbInputQueues)
            throws IOException, PcapFormatException, InterruptedException {
        Optional<RawPacket> next;
        while ((next = reader.readNextPacket()).isPresent()) {
            RawPacket rawPacket = next.get();
            statistics.recordPacketRead(rawPacket.length());
            try {
                ParsedPacket parsed = packetParser.parse(rawPacket);
                int lbIndex = Math.floorMod(parsed.flowKey().routingHash(), lbInputQueues.size());
                lbInputQueues.get(lbIndex).put(PipelineMessage.of(parsed));
            } catch (Exception unparsablePacket) {
                statistics.recordTransportProtocol(-1);
            }
        }
    }

    private List<BlockingQueue<PipelineMessage<ParsedPacket>>> newQueues(int count) {
        List<BlockingQueue<PipelineMessage<ParsedPacket>>> queues = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            queues.add(new ArrayBlockingQueue<>(QUEUE_CAPACITY));
        }
        return queues;
    }

    private void awaitAll(List<Future<?>> futures, String stageName) throws InterruptedException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Pipeline stage failed: " + stageName, e.getCause());
            }
        }
    }

    private void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
