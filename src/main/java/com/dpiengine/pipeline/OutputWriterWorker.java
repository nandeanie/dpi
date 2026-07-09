package com.dpiengine.pipeline;

import com.dpiengine.io.PcapFileWriter;
import com.dpiengine.model.RawPacket;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * The single writer at the end of the pipeline. Keeping exactly one writer avoids needing to
 * interleave output from multiple FastPath threads and keeps packet write order close to
 * arrival order without requiring an explicit re-sequencing step.
 */
public final class OutputWriterWorker implements Runnable {

    private final BlockingQueue<PipelineMessage<RawPacket>> outputQueue;
    private final PcapFileWriter writer;
    private volatile IOException failure;

    public OutputWriterWorker(BlockingQueue<PipelineMessage<RawPacket>> outputQueue, PcapFileWriter writer) {
        this.outputQueue = outputQueue;
        this.writer = writer;
    }

    @Override
    public void run() {
        try {
            while (true) {
                PipelineMessage<RawPacket> message = outputQueue.take();
                if (message.isPoison()) {
                    return;
                }
                writer.writePacket(message.payload());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Output writer interrupted", e);
        } catch (IOException e) {
            failure = e;
            throw new IllegalStateException("Output writer failed", e);
        }
    }

    public IOException failure() {
        return failure;
    }
}
