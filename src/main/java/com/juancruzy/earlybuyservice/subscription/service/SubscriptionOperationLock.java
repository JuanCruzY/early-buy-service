package com.juancruzy.earlybuyservice.subscription.service;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

@Component
public class SubscriptionOperationLock {
    private final ReentrantLock[] locks = new ReentrantLock[256];

    public SubscriptionOperationLock() {
        Arrays.setAll(locks, index -> new ReentrantLock());
    }

    public void run(String subscriptionId, Runnable action) {
        var lock = locks[Math.floorMod(subscriptionId.hashCode(), locks.length)];
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
