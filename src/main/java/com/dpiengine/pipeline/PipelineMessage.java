package com.dpiengine.pipeline;

/**
 * Wraps a payload traveling through one of the pipeline's BlockingQueues, or represents the
 * poison pill that tells a downstream worker there is no more work coming and it should stop
 * pulling from the queue and return.
 */
public final class PipelineMessage<T> {

    private static final PipelineMessage<?> POISON = new PipelineMessage<>(null, true);

    private final T payload;
    private final boolean poison;

    private PipelineMessage(T payload, boolean poison) {
        this.payload = payload;
        this.poison = poison;
    }

    public static <T> PipelineMessage<T> of(T payload) {
        return new PipelineMessage<>(payload, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> PipelineMessage<T> poisonPill() {
        return (PipelineMessage<T>) POISON;
    }

    public boolean isPoison() {
        return poison;
    }

    public T payload() {
        return payload;
    }
}
