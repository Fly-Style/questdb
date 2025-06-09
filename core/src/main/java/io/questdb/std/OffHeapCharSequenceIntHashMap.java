/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2024 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.std;

import io.questdb.std.str.DirectUtf16Sink;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.Arrays;

/**
 * A hash map that stores CharSequence keys off-heap and integers as value.
 * This implementation uses a DirectUtf16Sink as view to off-heap string as key,
 * allowing for efficient memory usage and manipulation.
 */
public class OffHeapCharSequenceIntHashMap extends AbstractCharSequenceHashSet implements Closeable {
    public static final int NO_ENTRY_VALUE = -1;
    // Let's use a DirectUtf16Sink as the key type
    private static final DirectUtf16Sink noEntryKey = null;

    private final IntList keyIndices;
    private int[] values;

    public OffHeapCharSequenceIntHashMap() {
        this(8);
    }

    public OffHeapCharSequenceIntHashMap(int initialCapacity) {
        this(initialCapacity, 0.4);
    }

    public OffHeapCharSequenceIntHashMap(int initialCapacity, double loadFactor) {
        this(initialCapacity, loadFactor, NO_ENTRY_VALUE);
    }

    public OffHeapCharSequenceIntHashMap(int initialCapacity, double loadFactor, int valueNotFound) {
        super(initialCapacity, loadFactor);
        int len = Numbers.ceilPow2((int) (this.capacity / loadFactor));

        // Re-create keys with the specific type
        keys = new DirectUtf16Sink[len];
        values = new int[len];
        keyIndices = new IntList(len);

        // Initialize free variable
        Arrays.fill(keys, noEntryKey);
        Arrays.fill(values, valueNotFound);
        free = capacity;
    }

    @Override
    public void clear() {
        // Close all DirectUtf16Sink instances
        for (DirectUtf16Sink key : (DirectUtf16Sink[]) keys) {
            if (key != null) {
                key.close();
            }
        }
        Arrays.fill(keys, noEntryKey);
        Arrays.fill(values, NO_ENTRY_VALUE);
        keyIndices.clear();
        free = capacity;
    }

    public void close() {
        clear();
    }

    public int get(@NotNull CharSequence key) {
        return valueAt(keyIndex(key));
    }

    public CharSequence[] keys() {
        return keys;
    }

    public boolean put(@NotNull CharSequence key, int value) {
        return putAt(keyIndex(key), key, value);
    }

    public void putAll(@NotNull OffHeapCharSequenceIntHashMap other) {
        DirectUtf16Sink[] otherKeys = (DirectUtf16Sink[]) other.keys;
        int[] otherValues = other.values;
        for (int i = 0, n = otherKeys.length; i < n; i++) {
            if (otherKeys[i] != noEntryKey) {
                put(otherKeys[i], otherValues[i]);
            }
        }
    }

    public boolean putAt(int index, @NotNull CharSequence key, int value) {
        if (index < 0) {
            values[-index - 1] = value;
            return false;
        }
        putAt0(index, key, value);
        return true;
    }

    public void putIfAbsent(@NotNull CharSequence key, int value) {
        int index = keyIndex(key);
        if (index > -1) {
            putAt0(index, key, value);
        }
    }

    public int valueAt(int index) {
        int index1 = -index - 1;
        return index < 0 ? values[index1] : NO_ENTRY_VALUE;
    }

    public int valueQuick(int index) {
        if (index >= 0 && index < keyIndices.size()) {
            int keyIndex = keyIndices.getQuick(index);
            return values[keyIndex];
        }
        return NO_ENTRY_VALUE;
    }

    @Override
    public int size() {
        return keyIndices.size();
    }

    protected void erase(int index) {
        DirectUtf16Sink key = (DirectUtf16Sink) keys[index];
        if (key != null) {
            // Remove the index from keyIndices
            for (int i = 0; i < keyIndices.size(); i++) {
                if (keyIndices.getQuick(i) == index) {
                    keyIndices.remove(i);
                    break;
                }
            }
            // TODO: sink pooling?
            // Note: very important to close the key to release off-heap memory and prevent memory leaks
            key.close();
        }
        keys[index] = noEntryKey;
        values[index] = NO_ENTRY_VALUE;
    }

    protected void move(int from, int to) {
        keys[to] = keys[from];
        values[to] = values[from];
        keys[from] = noEntryKey;
        values[from] = NO_ENTRY_VALUE;
    }

    /**
     * Puts a key-value pair at the specified index, replacing an existing key at that index.
     */
    private DirectUtf16Sink putAt0(int index, CharSequence key, int value) {
        DirectUtf16Sink sink;

        // If there's already a key at this index, reuse it
        DirectUtf16Sink oldKey = (DirectUtf16Sink) keys[index];
        if (oldKey != null) {
            // Remove the index from keyIndices
            for (int i = 0; i < keyIndices.size(); i++) {
                if (keyIndices.getQuick(i) == index) {
                    keyIndices.remove(i);
                    break;
                }
            }
            // Reuse the existing sink instead of closing it
            sink = oldKey;
            sink.clear();
            sink.put(key);
        } else {
            // TODO: take from pool if there's any allocated already?
            sink = new DirectUtf16Sink(key.length());
            sink.put(key);
        }

        keys[index] = sink;
        keyIndices.add(index);
        values[index] = value;
        if (--free == 0) {
            rehash();
        }
        return sink;
    }

    private void rehash() {
        int[] oldValues = values;
        DirectUtf16Sink[] oldKeys = (DirectUtf16Sink[]) keys;
        int size = capacity - free;
        capacity = capacity * 2;
        free = capacity - size;
        mask = Numbers.ceilPow2((int) (capacity / loadFactor)) - 1;
        this.keys = new DirectUtf16Sink[mask + 1];
        this.values = new int[mask + 1];

        // Clear the keyIndices list before adding new indices
        keyIndices.clear();

        for (int i = oldKeys.length - 1; i > -1; i--) {
            CharSequence key = oldKeys[i];
            if (key != null) {
                final int newIndex = keyIndex(key);
                keys[newIndex] = oldKeys[i];
                values[newIndex] = oldValues[i];
                keyIndices.add(newIndex);
            }
        }
    }
}
