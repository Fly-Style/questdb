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

package io.questdb.test.std;

import io.questdb.std.ObjList;
import io.questdb.std.OffHeapCharSequenceIntHashMap;
import io.questdb.std.Rnd;
import io.questdb.std.str.DirectUtf16Sink;
import io.questdb.std.str.StringSink;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class OffHeapCharSequenceIntHashMapTest {
    private OffHeapCharSequenceIntHashMap map;

    private DirectUtf16Sink sink;

    @After
    public void tearDown() {
        if (map != null) {
            map.close();
        }

        if (sink != null) {
            sink.close();
        }
    }

    @Test
    public void testInitialization() {
        // Create a map with default parameters
        map = new OffHeapCharSequenceIntHashMap();

        // Create a sink to generate a string
        sink = new DirectUtf16Sink(100);
        for (int i = 0; i < 10; i++) {
            sink.put('a');
        }
        sink.put(1);

        // This should not throw an exception
        map.put(sink.toString(), 42);

        // Verify the value was stored correctly
        Assert.assertEquals(42, map.get(sink.toString()));
    }

    @Test
    public void testInitializationWithSmallCapacity() {
        map = new OffHeapCharSequenceIntHashMap();

        // Create a map with a small initial capacity
        map = new OffHeapCharSequenceIntHashMap(1);

        // Create a sink to generate a string
        sink = new DirectUtf16Sink(100);
        for (int i = 0; i < 10; i++) {
            sink.put('a');
        }
        sink.put(1);

        // This should not throw an exception
        map.put(sink.toString(), 42);

        // Verify the value was stored correctly
        Assert.assertEquals(42, map.get(sink.toString()));
    }

    @Test
    public void testInitializationWithLargeCapacity() {
        map = new OffHeapCharSequenceIntHashMap();

        // Create a map with a large initial capacity
        map = new OffHeapCharSequenceIntHashMap(1000);

        // Create a sink to generate a string
        sink = new DirectUtf16Sink(100);
        for (int i = 0; i < 10; i++) {
            sink.put('a');
        }
        sink.put(1);

        // This should not throw an exception
        map.put(sink.toString(), 42);

        // Verify the value was stored correctly
        Assert.assertEquals(42, map.get(sink.toString()));
    }

    @Test
    public void testGet() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test getting values from an empty map
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("nonexistent"));

        // Test getting values for existing keys
        map.put("key1", 100);
        map.put("key2", 200);
        map.put("key3", 300);

        Assert.assertEquals(100, map.get("key1"));
        Assert.assertEquals(200, map.get("key2"));
        Assert.assertEquals(300, map.get("key3"));

        // Test getting values for non-existing keys
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("nonexistent"));

        // Test getting values after keys have been removed
        map.remove("key2");
        Assert.assertEquals(100, map.get("key1"));
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("key2"));
        Assert.assertEquals(300, map.get("key3"));
    }

    @Test
    public void testClear() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test clearing an empty map
        map.clear();
        Assert.assertEquals(0, map.size());

        // Add some entries to the map
        map.put("key1", 100);
        map.put("key2", 200);
        map.put("key3", 300);
        Assert.assertEquals(3, map.size());

        // Test clearing the map
        map.clear();
        Assert.assertEquals(0, map.size());

        // Verify all keys are removed
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("key1"));
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("key2"));
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("key3"));
    }

    @Test
    public void testRemove() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test removing from an empty map
        Assert.assertEquals(-1, map.remove("nonexistent"));
        Assert.assertEquals(0, map.size());

        // Add some entries to the map
        map.put("key1", 100);
        map.put("key2", 200);
        map.put("key3", 300);
        Assert.assertEquals(3, map.size());

        // Test removing an existing key
        int index = map.remove("key2");
        Assert.assertTrue("Remove should return a valid index for an existing key", index >= 0);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(OffHeapCharSequenceIntHashMap.NO_ENTRY_VALUE, map.get("key2"));

        // Test removing a non-existing key
        Assert.assertEquals(-1, map.remove("nonexistent"));
        Assert.assertEquals(2, map.size());

        // Test removing the same key again
        Assert.assertEquals(-1, map.remove("key2"));
        Assert.assertEquals(2, map.size());
    }

    @Test
    public void testPartialLookup() {
        map = new OffHeapCharSequenceIntHashMap();

        Rnd rnd = new Rnd();
        final int N = 1000;

        for (int i = 0; i < N; i++) {
            String s = rnd.nextString(10).substring(1, 9);
            map.put(s, i);
        }

        rnd.reset();

        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextString(10);
            int index = map.keyIndex(cs, 1, 9);
            Assert.assertEquals(i, map.valueAt(index));
        }

        rnd.reset();
        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextString(10);
            Assert.assertFalse(map.excludes(cs, 1, 9));
        }
    }

    @Test
    public void testPutMutableCharSequence() {
        map = new OffHeapCharSequenceIntHashMap();

        StringSink ss = new StringSink();
        ss.put("a");

        map.putIfAbsent(ss, 1);

        ss.clear();
        ss.put("bb");

        map.putIfAbsent(ss, 2);

        Assert.assertEquals(1, map.get("a"));
        Assert.assertEquals(2, map.get("bb"));

        Assert.assertEquals(2, map.size());
        Assert.assertEquals("a", map.getKeyQuick(0).toString());
        Assert.assertEquals("bb", map.getKeyQuick(1).toString());
    }

    @Test
    public void testMemoryManagement() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test that the map properly manages off-heap memory
        final int N = 10000;
        Rnd rnd = new Rnd();

        // Add a large number of entries to force rehashing
        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextChars(50); // Larger strings to use more memory
            map.put(cs, i);
        }

        // Verify all entries are accessible
        rnd.reset();
        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextChars(50);
            Assert.assertEquals(i, map.get(cs));
        }

        // Clear the map and verify it's empty
        map.clear();
        Assert.assertEquals(0, map.size());

        // Add entries again to verify memory is reused
        rnd.reset();
        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextChars(50);
            map.put(cs, i * 2);
        }

        // Verify all entries are accessible with new values
        rnd.reset();
        for (int i = 0; i < N; i++) {
            CharSequence cs = rnd.nextChars(50);
            Assert.assertEquals(i * 2, map.get(cs));
        }
    }

    @Test
    public void testCloseAndRehash() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test proper cleanup after close and behavior during rehashing
        try (OffHeapCharSequenceIntHashMap tempMap = new OffHeapCharSequenceIntHashMap(16, 0.5)) {
            // Fill the map to trigger rehashing
            Rnd rnd = new Rnd();
            for (int i = 0; i < 100; i++) {
                tempMap.put(rnd.nextChars(20), i);
            }

            // Verify entries
            rnd.reset();
            for (int i = 0; i < 100; i++) {
                Assert.assertEquals(i, tempMap.get(rnd.nextChars(20)));
            }
        }

        // Create a new map after closing the previous one
        try (OffHeapCharSequenceIntHashMap tempMap = new OffHeapCharSequenceIntHashMap()) {
            Rnd rnd = new Rnd();
            for (int i = 0; i < 50; i++) {
                tempMap.put(rnd.nextChars(10), i);
            }

            // Verify entries
            rnd.reset();
            for (int i = 0; i < 50; i++) {
                Assert.assertEquals(i, tempMap.get(rnd.nextChars(10)));
            }
        }
    }

    @Test
    public void testPut() {
        map = new OffHeapCharSequenceIntHashMap();

        // Test putting a new key-value pair
        boolean result = map.put("key1", 100);
        Assert.assertTrue("Put should return true for a new key", result);
        Assert.assertEquals(100, map.get("key1"));
        Assert.assertEquals(1, map.size());

        // Test updating an existing key with a new value
        result = map.put("key1", 200);
        Assert.assertFalse("Put should return false for an existing key", result);
        Assert.assertEquals(200, map.get("key1"));
        Assert.assertEquals(1, map.size());

        // Test putting multiple key-value pairs
        map.put("key2", 300);
        map.put("key3", 400);
        Assert.assertEquals(300, map.get("key2"));
        Assert.assertEquals(400, map.get("key3"));
        Assert.assertEquals(3, map.size());

        // Test putting many key-value pairs to trigger rehashing
        final int N = 1000;
        for (int i = 0; i < N; i++) {
            String key = "newkey" + i;
            result = map.put(key, i);
            Assert.assertTrue("Put should return true for a new key", result);
            Assert.assertEquals(i, map.get(key));
        }
        Assert.assertEquals(N + 3, map.size()); // 1000 new keys + 3 existing keys
    }
}
