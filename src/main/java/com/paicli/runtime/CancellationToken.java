package com.paicli.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
//并发取消系统里的“取消标志位”
public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }
}
