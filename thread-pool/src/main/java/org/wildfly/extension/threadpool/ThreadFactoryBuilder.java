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

import org.jboss.threads.JBossThreadFactory;

/**
 */
final class ThreadFactoryBuilder {
    private final String defaultThreadNamePattern;
    private ThreadGroup threadGroup;
    private Boolean daemon;
    private Integer initialPriority;
    private String threadNamePattern;
    private Thread.UncaughtExceptionHandler exceptionHandler;
    private Long stackSize;

    ThreadFactoryBuilder(final String defaultThreadNamePattern) {
        this.defaultThreadNamePattern = defaultThreadNamePattern;
    }

    public String getDefaultThreadNamePattern() {
        return defaultThreadNamePattern;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public ThreadFactoryBuilder setThreadGroup(final ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
        return this;
    }

    public Boolean getDaemon() {
        return daemon;
    }

    public ThreadFactoryBuilder setDaemon(final Boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    public Integer getInitialPriority() {
        return initialPriority;
    }

    public ThreadFactoryBuilder setInitialPriority(final Integer initialPriority) {
        this.initialPriority = initialPriority;
        return this;
    }

    public String getThreadNamePattern() {
        return threadNamePattern == null ? defaultThreadNamePattern : threadNamePattern;
    }

    public ThreadFactoryBuilder setThreadNamePattern(final String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
        return this;
    }

    public Thread.UncaughtExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public ThreadFactoryBuilder setExceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public Long getStackSize() {
        return stackSize;
    }

    public ThreadFactoryBuilder setStackSize(final Long stackSize) {
        this.stackSize = stackSize;
        return this;
    }

    public JBossThreadFactory build() {
        return new JBossThreadFactory(
            getThreadGroup(),
            getDaemon(),
            getInitialPriority(),
            getThreadNamePattern(),
            getExceptionHandler(),
            getStackSize()
        );
    }
}
