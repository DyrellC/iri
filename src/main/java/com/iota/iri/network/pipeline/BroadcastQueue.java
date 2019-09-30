package com.iota.iri.network.pipeline;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BroadcastQueue {
    private BlockingQueue<ProcessingContext> broadcastStageQueue = new ArrayBlockingQueue<>(1000);
    private final Object cascadeSync = new Object();

    public boolean add(ProcessingContext context) {
        synchronized (cascadeSync) {
            try {
                this.broadcastStageQueue.put(context);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

    }

    public BlockingQueue<ProcessingContext> get(){
        synchronized (cascadeSync) {
            return this.broadcastStageQueue;
        }
    }


}
