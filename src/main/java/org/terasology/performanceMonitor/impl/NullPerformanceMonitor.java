/*
 * Copyright 2013 Moving Blocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.performanceMonitor.impl;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * @author Immortius <immortius@gmail.com>
 */
public class NullPerformanceMonitor implements IPerformanceMonitor {
    private TObjectDoubleMap<String> metrics = new TObjectDoubleHashMap<>();
    private TObjectIntMap<String> threads = new TObjectIntHashMap<>();

    public void startThread(String name) {
    }

    public void endThread(String name) {
    }

    public void rollCycle() {
    }

    public void startActivity(String activity) {
    }

    public void endActivity() {
    }

    public TObjectDoubleMap<String> getRunningMean() {
        return metrics;
    }

    public TObjectDoubleMap<String> getDecayingSpikes() {
        return metrics;
    }

    public TObjectIntMap<String> getRunningThreads() {
        return threads;
    }

}
