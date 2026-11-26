package campuscache;

import java.util.*;

/**
 * CampusCache - Intelligent File Cache Simulator
 * Implements strict LFU (Least Frequently Used) eviction policy with
 * LRU (Least Recently Used) tie-breaking for items with identical frequency.
 *
 * Time Complexities:
 * - GET: O(1) average
 * - PUT: O(1) average
 * - STATE: O(n)
 */
public class CampusCache {

    public static class OpResult {
        public final String operation;
        public final String key;
        public final String value;
        public final boolean hit;
        public final int oldFrequency;
        public final int newFrequency;
        public final String evictedKey;
        public final String evictedValue;
        public final int evictedFrequency;
        public final String reason;
        public final String message;

        public OpResult(String operation, String key, String value, boolean hit,
                        int oldFrequency, int newFrequency,
                        String evictedKey, String evictedValue, int evictedFrequency,
                        String reason, String message) {
            this.operation = operation;
            this.key = key;
            this.value = value;
            this.hit = hit;
            this.oldFrequency = oldFrequency;
            this.newFrequency = newFrequency;
            this.evictedKey = evictedKey;
            this.evictedValue = evictedValue;
            this.evictedFrequency = evictedFrequency;
            this.reason = reason;
            this.message = message;
        }
    }

    public static class NodeSnapshot {
        public final String key;
        public final String value;
        public final int frequency;
        public final boolean isLruInGroup;
        public final boolean isNextEvictionCandidate;

        public NodeSnapshot(String key, String value, int frequency, boolean isLruInGroup, boolean isNextEvictionCandidate) {
            this.key = key;
            this.value = value;
            this.frequency = frequency;
            this.isLruInGroup = isLruInGroup;
            this.isNextEvictionCandidate = isNextEvictionCandidate;
        }
    }

    public static class FrequencyGroupSnapshot {
        public final int frequency;
        public final List<NodeSnapshot> nodes; // ordered MRU to LRU

        public FrequencyGroupSnapshot(int frequency, List<NodeSnapshot> nodes) {
            this.frequency = frequency;
            this.nodes = nodes;
        }
    }

    public static class CacheState {
        public final int capacity;
        public final int currentSize;
        public final int minFrequency;
        public final List<FrequencyGroupSnapshot> frequencyGroups;
        public final String nextEvictionKey;
        public final Map<String, Object> stats;

        public CacheState(int capacity, int currentSize, int minFrequency,
                          List<FrequencyGroupSnapshot> frequencyGroups,
                          String nextEvictionKey, Map<String, Object> stats) {
            this.capacity = capacity;
            this.currentSize = currentSize;
            this.minFrequency = minFrequency;
            this.frequencyGroups = frequencyGroups;
            this.nextEvictionKey = nextEvictionKey;
            this.stats = stats;
        }
    }

    private int capacity;
    private final Map<String, CacheNode> keyNodeMap;
    private final Map<Integer, DoublyLinkedList> freqListMap;
    private int minFrequency;

    // Statistics
    private int totalGetRequests;
    private int cacheHits;
    private int cacheMisses;
    private int totalPutRequests;
    private int totalEvictions;

    public CampusCache(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be non-negative");
        }
        this.capacity = capacity;
        this.keyNodeMap = new HashMap<>();
        this.freqListMap = new HashMap<>();
        this.minFrequency = 0;
        this.totalGetRequests = 0;
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.totalPutRequests = 0;
        this.totalEvictions = 0;
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized int getSize() {
        return keyNodeMap.size();
    }

    public synchronized int getMinFrequency() {
        return minFrequency;
    }

    /**
     * Resets or changes cache capacity.
     */
    public synchronized void reinitialize(int newCapacity) {
        if (newCapacity < 0) {
            throw new IllegalArgumentException("Capacity must be non-negative");
        }
        this.capacity = newCapacity;
        this.keyNodeMap.clear();
        this.freqListMap.clear();
        this.minFrequency = 0;
        this.totalGetRequests = 0;
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.totalPutRequests = 0;
        this.totalEvictions = 0;
    }

    /**
     * GET(key) in O(1):
     * - If key does not exist, return CACHE MISS (do NOT increase frequency).
     * - Otherwise return its value.
     * - Increase frequency by 1.
     * - Move node to corresponding frequency list.
     * - Update minFrequency if required.
     */
    public synchronized OpResult get(String key) {
        totalGetRequests++;
        if (key == null || !keyNodeMap.containsKey(key)) {
            cacheMisses++;
            return new OpResult(
                    "GET", key, null, false,
                    0, 0,
                    null, null, 0,
                    "Key not found in cache",
                    "CACHE MISS: Document '" + key + "' is not in fast memory."
            );
        }

        cacheHits++;
        CacheNode node = keyNodeMap.get(key);
        int oldFreq = node.frequency;
        updateNodeFrequency(node);

        return new OpResult(
                "GET", key, node.value, true,
                oldFreq, node.frequency,
                null, null, 0,
                "Cache hit: access frequency incremented from " + oldFreq + " to " + node.frequency,
                "CACHE HIT: Document '" + key + "' found with value '" + node.value + "'."
        );
    }

    /**
     * PUT(key, value) in O(1):
     * - If capacity is 0, do not insert.
     * - If key already exists:
     *     - Update value.
     *     - Count as an access.
     *     - Increase frequency by 1.
     * - If key is new:
     *     - If cache is full, evict LFU item (LRU tie-break).
     *     - Insert new key with frequency 1.
     *     - Update minFrequency to 1.
     */
    public synchronized OpResult put(String key, String value) {
        totalPutRequests++;
        if (key == null) {
            return new OpResult(
                    "PUT", null, value, false,
                    0, 0,
                    null, null, 0,
                    "Invalid null key",
                    "Ignored: Key cannot be null."
            );
        }

        if (capacity == 0) {
            return new OpResult(
                    "PUT", key, value, false,
                    0, 0,
                    null, null, 0,
                    "Cache capacity is 0",
                    "Ignored: Cache capacity is 0. Document '" + key + "' cannot be stored."
            );
        }

        // Existing key update
        if (keyNodeMap.containsKey(key)) {
            CacheNode existingNode = keyNodeMap.get(key);
            int oldFreq = existingNode.frequency;
            existingNode.value = value;
            updateNodeFrequency(existingNode);

            return new OpResult(
                    "PUT", key, value, true,
                    oldFreq, existingNode.frequency,
                    null, null, 0,
                    "Existing key updated; access frequency increased from " + oldFreq + " to " + existingNode.frequency,
                    "UPDATED: Document '" + key + "' updated to '" + value + "'."
            );
        }

        // New key insertion
        String evictedKey = null;
        String evictedVal = null;
        int evictedFreq = 0;
        String reason = "Inserted new file with initial frequency 1";

        if (keyNodeMap.size() >= capacity) {
            // Eviction needed
            DoublyLinkedList minList = freqListMap.get(minFrequency);
            if (minList != null && !minList.isEmpty()) {
                CacheNode toEvict = minList.removeLast();
                if (toEvict != null) {
                    keyNodeMap.remove(toEvict.key);
                    totalEvictions++;
                    evictedKey = toEvict.key;
                    evictedVal = toEvict.value;
                    evictedFreq = toEvict.frequency;

                    if (minList.isEmpty()) {
                        freqListMap.remove(minFrequency);
                    }

                    reason = "Evicted '" + evictedKey + "' (LFU frequency=" + evictedFreq +
                            ", tie-broken by LRU recency). Inserted '" + key + "'.";
                }
            }
        }

        CacheNode newNode = new CacheNode(key, value);
        keyNodeMap.put(key, newNode);
        DoublyLinkedList list1 = freqListMap.computeIfAbsent(1, k -> new DoublyLinkedList());
        list1.addFirst(newNode);
        minFrequency = 1;

        String msg = "INSERTED: Document '" + key + "' cached (value='" + value + "', freq=1).";
        if (evictedKey != null) {
            msg += " Evicted '" + evictedKey + "' to free fast memory.";
        }

        return new OpResult(
                "PUT", key, value, false,
                0, 1,
                evictedKey, evictedVal, evictedFreq,
                reason, msg
        );
    }

    /**
     * Helper to promote node frequency in O(1).
     */
    private void updateNodeFrequency(CacheNode node) {
        int oldFreq = node.frequency;
        DoublyLinkedList oldList = freqListMap.get(oldFreq);
        if (oldList != null) {
            oldList.remove(node);
            if (oldList.isEmpty()) {
                freqListMap.remove(oldFreq);
                if (minFrequency == oldFreq) {
                    minFrequency++;
                }
            }
        }

        node.frequency = oldFreq + 1;
        DoublyLinkedList newList = freqListMap.computeIfAbsent(node.frequency, k -> new DoublyLinkedList());
        newList.addFirst(node);
    }

    /**
     * Identifies the current candidate that would be evicted on next insertion.
     */
    public synchronized CacheNode getNextEvictionCandidate() {
        if (keyNodeMap.isEmpty() || minFrequency == 0) {
            return null;
        }
        DoublyLinkedList minList = freqListMap.get(minFrequency);
        if (minList == null || minList.isEmpty()) {
            return null;
        }
        return minList.peekLast();
    }

    /**
     * STATE: Returns full cache snapshot grouped by frequency in O(n).
     * Must be requested explicitly on demand.
     */
    public synchronized CacheState getState() {
        CacheNode nextEvict = getNextEvictionCandidate();
        String nextEvictionKey = (nextEvict != null) ? nextEvict.key : null;

        List<Integer> sortedFreqs = new ArrayList<>(freqListMap.keySet());
        Collections.sort(sortedFreqs);

        List<FrequencyGroupSnapshot> groups = new ArrayList<>();
        for (int freq : sortedFreqs) {
            DoublyLinkedList list = freqListMap.get(freq);
            if (list == null || list.isEmpty()) continue;

            List<CacheNode> nodesInList = list.toList();
            List<NodeSnapshot> nodeSnapshots = new ArrayList<>();
            int count = nodesInList.size();

            for (int i = 0; i < count; i++) {
                CacheNode node = nodesInList.get(i);
                boolean isLruInGroup = (i == count - 1); // tail is LRU
                boolean isNextEvict = node.key.equals(nextEvictionKey);
                nodeSnapshots.add(new NodeSnapshot(
                        node.key,
                        node.value,
                        node.frequency,
                        isLruInGroup,
                        isNextEvict
                ));
            }
            groups.add(new FrequencyGroupSnapshot(freq, nodeSnapshots));
        }

        Map<String, Object> stats = getStats();
        return new CacheState(capacity, keyNodeMap.size(), minFrequency, groups, nextEvictionKey, stats);
    }

    public synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("capacity", capacity);
        stats.put("currentSize", keyNodeMap.size());
        stats.put("totalGetRequests", totalGetRequests);
        stats.put("cacheHits", cacheHits);
        stats.put("cacheMisses", cacheMisses);
        stats.put("totalPutRequests", totalPutRequests);
        stats.put("totalEvictions", totalEvictions);

        double hitRatio = totalGetRequests > 0 ? ((double) cacheHits / totalGetRequests) * 100.0 : 0.0;
        double utilization = capacity > 0 ? ((double) keyNodeMap.size() / capacity) * 100.0 : 0.0;

        stats.put("hitRatioPercent", Math.round(hitRatio * 10.0) / 10.0);
        stats.put("utilizationPercent", Math.round(utilization * 10.0) / 10.0);
        return stats;
    }

    public synchronized Map<String, String> getDirectKeyValues() {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, CacheNode> entry : keyNodeMap.entrySet()) {
            map.put(entry.getKey(), entry.getValue().value);
        }
        return map;
    }
}
