/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.k8s.api.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

public class Reflector<T extends HasMetadata, LT extends KubernetesResourceList<T>> implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Reflector.class.getName());
    private static final Duration minWatchTimeout = Duration.ofMinutes(5);

    private final Duration resyncInterval;
    private final ListerWatcher<T, LT> listerWatcher;
    private final Processor<T> processor;
    private final Class<T> expectedType;
    private final WorkQueue<T> queue;
    private final Clock clock;
    private volatile Watch watch;
    private volatile Instant nextResync = Instant.MIN;

    private volatile String lastSyncResourceVersion;

    public Reflector(Config<T, LT> config) {
        this.resyncInterval = config.resyncInterval;
        this.expectedType = config.expectedType;
        this.listerWatcher = config.listerWatcher;
        this.processor = config.processor;
        this.queue = config.queue;
        this.clock = config.clock;
    }

    public Class<T> getExpectedType() {
        return this.expectedType;
    }

    @Override
    public void run() {
        try {
            Instant now = Instant.now(clock);
            if (now.isAfter(nextResync)) {
                while (true) {
                    try {
                        resync();
                        nextResync = Instant.now(clock).plus(resyncInterval);
                        break;
                    } catch (KubernetesClientException e) {
                        log.warn("KubernetesClientException when triggering resync. Pausing for a few seconds before retrying.",
                                e);
                        Thread.sleep(5000);
                    }
                }
            }
            long sleepTime = Math.max(1, nextResync.toEpochMilli() - now.toEpochMilli());
            log.debug("Waiting on event queue for {} ms unless notified", sleepTime);
            queue.pop(processor, sleepTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Exception doing resource update", e);
            // TODO: We can attempt to requeue here
        }
    }

    public String getLastSyncResourceVersion() {
        return lastSyncResourceVersion;
    }

    private void resync() throws InterruptedException {
        log.info("Resync: {}", this.expectedType);
        if (watch != null) {
            log.info("Closing existing watch");
            watch.close();
        }
        Instant start = clock.instant();
        LT list = listerWatcher.list(new ListOptions().setResourceVersion(null));
        String resourceVersion = Optional.ofNullable(list)
                .map(KubernetesResourceList::getMetadata)
                .map(ListMeta::getResourceVersion).orElse("");

        syncWith(list.getItems(), resourceVersion);
        lastSyncResourceVersion = resourceVersion;

        ListOptions watchOptions = new ListOptions()
                .setResourceVersion(resourceVersion)
                .setTimeoutSeconds((int) minWatchTimeout.getSeconds());

        AtomicLong eventCount = new AtomicLong(0);

        watch = listerWatcher.watch(new Watcher<T>() {
            @Override
            public void eventReceived(Action action, T t) {
                log.debug("Event received - action: {}, element: {}", action, t);

                if (!t.getClass().equals(expectedType)) {
                    log.warn("Got unexpected type {}", t.getClass());
                    return;
                }

                try {
                    String newResourceVersion = t.getMetadata().getResourceVersion();

                    long before = System.nanoTime();
                    switch (action) {
                        case ADDED:
                            queue.add(t);
                            break;
                        case MODIFIED:
                            queue.update(t);
                            break;
                        case DELETED:
                            queue.delete(t);
                            break;
                        case ERROR:
                            log.warn("Got error event {}", action);
                            break;
                    }
                    if (log.isDebugEnabled()) {
                        long after = System.nanoTime();
                        log.debug("{} {} version {} took {} ns", action, t.getMetadata().getName(), t.getMetadata().getResourceVersion(), after - before);
                    }
                    lastSyncResourceVersion = newResourceVersion;
                    eventCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error handling watch event", e);
                }
            }

            @Override
            public void onClose(WatcherException e) {
                Instant now = clock.instant();
                if (now.minusMillis(start.toEpochMilli()).toEpochMilli() < 1000 && eventCount.get() == 0) {
                    log.warn("Very short watch: Unexpected watch close - watch lasted less than a second and no items received");
                } else {
                    log.info("Watch closed");
                }
                if (e != null) {
                    log.warn("Unexpected watch close - isShouldRetry{}", e.isShouldRetry(), e);
                    if (e.isShouldRetry()) {
                        doWakeup();
                    }
                }
            }

            @Override
            public void onClose() {
                log.info("Watch closed");
                doWakeup();
            }

            private void doWakeup() {
                try {
                    nextResync = Instant.MIN;
                    queue.wakeup();
                } catch (InterruptedException ex) {
                    log.warn("Interrupted when waking up queue processor", ex);
                    Thread.currentThread().interrupt();
                }
            }
        }, watchOptions);
    }

    private void syncWith(List<T> items, String resourceVersion) throws InterruptedException {
        queue.replace(items, resourceVersion);
    }

    public void shutdown() {
        if (watch != null) {
            watch.close();
        }
    }

    public static class Config<T extends HasMetadata, LT extends KubernetesResourceList<T>> {
        private Clock clock;
        private Duration resyncInterval;
        private ListerWatcher<T, LT> listerWatcher;
        private Processor<T> processor;
        private WorkQueue<T> queue;
        private Class<T> expectedType;

        public Config<T, LT> setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Config<T, LT> setResyncInterval(Duration resyncInterval) {
            this.resyncInterval = resyncInterval;
            return this;
        }

        public Config<T, LT> setListerWatcher(ListerWatcher<T, LT> listerWatcher) {
            this.listerWatcher = listerWatcher;
            return this;
        }

        public Config<T, LT> setProcessor(Processor<T> processor) {
            this.processor = processor;
            return this;
        }

        public Config<T, LT> setWorkQueue(WorkQueue<T> queue) {
            this.queue = queue;
            return this;
        }

        public Config<T, LT> setExpectedType(Class<T> expectedType) {
            this.expectedType = expectedType;
            return this;
        }
    }
}
