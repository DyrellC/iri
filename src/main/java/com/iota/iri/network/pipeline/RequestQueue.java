package com.iota.iri.network.pipeline;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A queue for transactions intended to be submitted to the {@link BroadcastStage}
 * for processing
 */
public class RequestQueue {

    /** A blocking queue to store transactions for broadcasting */
    private BlockingQueue<ProcessingContext> requestStageQueue = new ArrayBlockingQueue<>(100);

    /**
     * Add transactions to the Broadcast Queue
     * @param context Transaction context to be passed to the {@link BroadcastStage}
     * @return True if added properly, False if not
     */
    public boolean add(ProcessingContext context) {
        try {
            this.requestStageQueue.put(context);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Getter for the current Broadcast Queue
     * @return BlockingQueue of all transactions left to be broadcasted
     */
    public BlockingQueue<ProcessingContext> get(){
        return this.requestStageQueue;
    }
}