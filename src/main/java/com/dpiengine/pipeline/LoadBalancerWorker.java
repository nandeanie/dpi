package com.dpiengine.pipeline;

import com.dpiengine.model.ParsedPacket;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Consumes parsed packets from the reader and fans each one out to exactly one of its own
 * FastPath queues, chosen by hashing the packet's FiveTuple. Because the hash is deterministic,
 * every packet belonging to the same flow always reaches the same FastPath worker, which is
 * what lets FastPath keep per-flow state without any locking.
 */
public final class LoadBalancerWorker implements Runnable {

    private final int loadBalancerIndex;
    private final BlockingQueue<PipelineMessage<ParsedPacket>> inputQueue;
    private final List<BlockingQueue<PipelineMessage<ParsedPacket>>> fastPathQueues;

    public LoadBalancerWorker(int loadBalancerIndex,
                               BlockingQueue<PipelineMessage<ParsedPacket>> inputQueue,
                               List<BlockingQueue<PipelineMessage<ParsedPacket>>> fastPathQueues) {
        this.loadBalancerIndex = loadBalancerIndex;
        this.inputQueue = inputQueue;
        this.fastPathQueues = fastPathQueues;
    }

    @Override
    public void run() {
        try {
            while (true) {
                PipelineMessage<ParsedPacket> message = inputQueue.take();
                if (message.isPoison()) {
                    for (BlockingQueue<PipelineMessage<ParsedPacket>> queue : fastPathQueues) {
                        queue.put(PipelineMessage.poisonPill());
                    }
                    return;
                }
                ParsedPacket packet = message.payload();
                int fastPathIndex = Math.floorMod(packet.flowKey().routingHash(), fastPathQueues.size());
                fastPathQueues.get(fastPathIndex).put(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Load balancer " + loadBalancerIndex + " interrupted", e);
        }
    }
}
