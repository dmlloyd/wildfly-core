/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.threadpool;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.management.StandardThreadPoolMXBean;

/**
 */
final class ThreadPoolService implements Service {
    private final Supplier<EnhancedQueueExecutor.Builder> builderSupplier;
    private final Consumer<ExecutorService> esConsumer;
    private final Consumer<StandardThreadPoolMXBean> mbeanConsumer;

    // run time state
    private EnhancedQueueExecutor executor;

    ThreadPoolService(final Supplier<EnhancedQueueExecutor.Builder> builderSupplier, final Consumer<ExecutorService> esConsumer, final Consumer<StandardThreadPoolMXBean> mbeanConsumer) {
        this.builderSupplier = builderSupplier;
        this.esConsumer = esConsumer;
        this.mbeanConsumer = mbeanConsumer;
    }

    public void start(final StartContext startContext) {
        final EnhancedQueueExecutor.Builder builder = builderSupplier.get();
        // this sync is necessary to avoid a race between manual service manipulation and container operations...
        // we can remove it if/when we get proper MSC transactions
        synchronized (builder) {
            executor = builder.build();
            esConsumer.accept(executor);
            mbeanConsumer.accept(executor.getThreadPoolMXBean());
        }
    }

    public void stop(final StopContext context) {
        context.asynchronous();
        executor.setTerminationTask(new Runnable() {
            public void run() {
                esConsumer.accept(null);
                mbeanConsumer.accept(null);
                context.complete();
            }
        });
        executor.shutdown();
    }
}
