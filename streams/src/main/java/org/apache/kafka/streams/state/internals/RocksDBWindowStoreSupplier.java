/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.state.internals;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.state.WindowStore;

import java.util.Map;

/**
 * A {@link org.apache.kafka.streams.state.KeyValueStore} that stores all entries in a local RocksDB database.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 *
 * @see org.apache.kafka.streams.state.Stores#create(String)
 */

public class RocksDBWindowStoreSupplier<K, V> extends AbstractStoreSupplier<K, V, WindowStore> implements WindowStoreSupplier<WindowStore> {
    private static final String METRICS_SCOPE = "rocksdb-window";
    public static final int MIN_SEGMENTS = 2;
    private final long retentionPeriod;
    private final boolean retainDuplicates;
    private final int numSegments;
    private final long segmentInterval;
    private final long windowSize;
    private final boolean enableCaching;

    public RocksDBWindowStoreSupplier(String name, long retentionPeriod, int numSegments, boolean retainDuplicates, Serde<K> keySerde, Serde<V> valueSerde, long windowSize, boolean logged, Map<String, String> logConfig, boolean enableCaching) {
        this(name, retentionPeriod, numSegments, retainDuplicates, keySerde, valueSerde, Time.SYSTEM, windowSize, logged, logConfig, enableCaching);
    }

    public RocksDBWindowStoreSupplier(String name, long retentionPeriod, int numSegments, boolean retainDuplicates, Serde<K> keySerde, Serde<V> valueSerde, Time time, long windowSize, boolean logged, Map<String, String> logConfig, boolean enableCaching) {
        super(name, keySerde, valueSerde, time, logged, logConfig);
        if (numSegments < MIN_SEGMENTS) {
            throw new IllegalArgumentException("numSegments must be >= " + MIN_SEGMENTS);
        }
        this.retentionPeriod = retentionPeriod;
        this.retainDuplicates = retainDuplicates;
        this.numSegments = numSegments;
        this.windowSize = windowSize;
        this.enableCaching = enableCaching;
        this.segmentInterval = Segments.segmentInterval(retentionPeriod, numSegments);
    }

    public String name() {
        return name;
    }

    public WindowStore<K, V> get() {
        final RocksDBSegmentedBytesStore segmentedBytesStore = new RocksDBSegmentedBytesStore(
                name,
                retentionPeriod,
                numSegments,
                new WindowKeySchema()
        );
        final RocksDBWindowStore<Bytes, byte[]> innerStore = RocksDBWindowStore.bytesStore(segmentedBytesStore,
                                                                                           retainDuplicates,
                                                                                           windowSize);

        return new MeteredWindowStore<>(maybeWrapCaching(maybeWrapLogged(innerStore)),
                                        METRICS_SCOPE,
                                        time,
                                        keySerde,
                                        valueSerde);

    }

    @Override
    public long retentionPeriod() {
        return retentionPeriod;
    }

    private WindowStore<Bytes, byte[]> maybeWrapLogged(final WindowStore<Bytes, byte[]> inner) {
        if (!logged) {
            return inner;
        }
        return new ChangeLoggingWindowBytesStore(inner, retainDuplicates);
    }

    private WindowStore<Bytes, byte[]> maybeWrapCaching(final WindowStore<Bytes, byte[]> inner) {
        if (!enableCaching) {
            return inner;
        }
        return new CachingWindowStore<>(inner, keySerde, valueSerde, windowSize, segmentInterval);
    }
}
