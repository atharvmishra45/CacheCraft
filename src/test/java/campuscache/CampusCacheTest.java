package campuscache;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Comprehensive Test Suite for CampusCache.
 * Covers all 10 mandatory test scenarios from the specification:
 * 1. Basic GET and PUT.
 * 2. GET on a missing key.
 * 3. LFU eviction.
 * 4. LRU tie-breaking.
 * 5. Updating an existing key.
 * 6. Repeated updates.
 * 7. Capacity zero.
 * 8. Capacity one.
 * 9. Multiple frequency groups.
 * 10. Repeated operations.
 */
public class CampusCacheTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    private static void assertEquals(Object expected, Object actual, String message) {
        testsTotal++;
        if (Objects.equals(expected, actual)) {
            testsPassed++;
            System.out.println("  [PASS] " + message);
        } else {
            System.err.println("  [FAIL] " + message + " -> Expected: <" + expected + ">, Actual: <" + actual + ">");
            throw new AssertionError("Failed: " + message + " (Expected: " + expected + ", got: " + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertEquals(true, condition, message);
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  CampusCache: Executing Core Unit Tests");
        System.out.println("=================================================");

        test1_BasicGetAndPut();
        test2_GetMissingKey();
        test3_LfuEviction();
        test4_LruTieBreaking();
        test5_UpdateExistingKey();
        test6_RepeatedUpdates();
        test7_CapacityZero();
        test8_CapacityOne();
        test9_MultipleFrequencyGroups();
        test10_RepeatedOperations();
        testDemo1_HackathonSpecification();
        testDemo2_LruTieBreakingSpecification();

        System.out.println("=================================================");
        System.out.println("  TEST RESULTS: " + testsPassed + " / " + testsTotal + " assertions passed!");
        System.out.println("=================================================");
    }

    // 1. Basic GET and PUT
    public static void test1_BasicGetAndPut() {
        System.out.println("\nTest 1: Basic GET and PUT");
        CampusCache cache = new CampusCache(3);
        CampusCache.OpResult r1 = cache.put("Syllabus", "CS101_v1.pdf");
        CampusCache.OpResult r2 = cache.put("Timetable", "Fall2026.pdf");

        assertEquals(2, cache.getSize(), "Cache size should be 2");
        CampusCache.OpResult g1 = cache.get("Syllabus");
        assertTrue(g1.hit, "GET Syllabus should be a HIT");
        assertEquals("CS101_v1.pdf", g1.value, "Syllabus value should match");
        assertEquals(2, g1.newFrequency, "Frequency of Syllabus should now be 2");

        CampusCache.OpResult g2 = cache.get("Timetable");
        assertTrue(g2.hit, "GET Timetable should be a HIT");
        assertEquals("Fall2026.pdf", g2.value, "Timetable value should match");
        assertEquals(2, g2.newFrequency, "Frequency of Timetable should now be 2");
    }

    // 2. GET on a missing key
    public static void test2_GetMissingKey() {
        System.out.println("\nTest 2: GET on a missing key");
        CampusCache cache = new CampusCache(2);
        cache.put("DocA", "100");

        CampusCache.OpResult gMiss = cache.get("DocMissing");
        assertEquals(false, gMiss.hit, "DocMissing should be a CACHE MISS");
        assertEquals(null, gMiss.value, "Value for missing key should be null");
        assertEquals(1, cache.getSize(), "Cache size should remain 1");

        // Verify that miss does not increment missing key frequency or corrupt minFrequency
        assertEquals(1, cache.getMinFrequency(), "minFrequency must remain 1");
    }

    // 3. LFU Eviction
    public static void test3_LfuEviction() {
        System.out.println("\nTest 3: LFU Eviction");
        CampusCache cache = new CampusCache(2);
        cache.put("A", "10"); // freq 1
        cache.put("B", "20"); // freq 1
        cache.get("A");       // freq 2

        // Cache is full [A: freq 2, B: freq 1]. Next insertion of C must evict B (lowest frequency)
        CampusCache.OpResult p3 = cache.put("C", "30");
        assertEquals("B", p3.evictedKey, "Should evict B because it has frequency 1 < 2");
        assertEquals(2, cache.getSize(), "Cache size must be 2");

        CampusCache.OpResult gB = cache.get("B");
        assertEquals(false, gB.hit, "Evicted key B should result in CACHE MISS");

        CampusCache.OpResult gA = cache.get("A");
        assertEquals(true, gA.hit, "A must still exist in cache");
        assertEquals("10", gA.value, "A's value must remain 10");
    }

    // 4. LRU Tie-Breaking
    public static void test4_LruTieBreaking() {
        System.out.println("\nTest 4: LRU Tie-breaking when frequencies are tied");
        CampusCache cache = new CampusCache(2);
        cache.put("X", "100"); // freq 1
        cache.put("Y", "200"); // freq 1
        // Both X and Y have frequency 1.
        // Insertion order: X first, then Y. Y is MRU, X is LRU in frequency group 1.
        // Inserting Z must evict X (the least recently used among frequency 1 ties).
        CampusCache.OpResult pZ = cache.put("Z", "300");
        assertEquals("X", pZ.evictedKey, "X must be evicted as LRU tie-breaker");

        // Now cache has Y and Z, both freq 1.
        // Access Y to promote to freq 2
        cache.get("Y"); // Y freq 2
        // Access Z to promote to freq 2
        cache.get("Z"); // Z freq 2, Y is now LRU in freq 2!
        // Insert W (freq 1) -> cache has capacity 2, so one must be evicted.
        // Wait, cache has Y (freq 2) and Z (freq 2). Both freq 2.
        // Y was accessed before Z, so Y is LRU among freq 2!
        CampusCache.OpResult pW = cache.put("W", "400");
        assertEquals("Y", pW.evictedKey, "Y must be evicted as LRU tie-breaker among freq 2");
    }

    // 5. Updating an existing key
    public static void test5_UpdateExistingKey() {
        System.out.println("\nTest 5: Updating an existing key");
        CampusCache cache = new CampusCache(2);
        cache.put("GradeReport", "Grade_A"); // freq 1
        CampusCache.OpResult updateResult = cache.put("GradeReport", "Grade_A_Plus"); // updates value & freq to 2

        assertEquals(1, cache.getSize(), "Cache size should still be 1");
        assertEquals(2, updateResult.newFrequency, "Updating existing key counts as access (freq becomes 2)");

        CampusCache.OpResult getResult = cache.get("GradeReport");
        assertEquals("Grade_A_Plus", getResult.value, "Value must be updated to new value");
        assertEquals(3, getResult.newFrequency, "Subsequent GET increments frequency to 3");
    }

    // 6. Repeated updates
    public static void test6_RepeatedUpdates() {
        System.out.println("\nTest 6: Repeated updates on same key");
        CampusCache cache = new CampusCache(2);
        cache.put("Sensor", "1");
        cache.put("Sensor", "2");
        cache.put("Sensor", "3");
        cache.put("Sensor", "4");

        assertEquals(1, cache.getSize(), "Cache size must be 1");
        CampusCache.OpResult res = cache.get("Sensor");
        assertEquals("4", res.value, "Sensor value should be 4");
        assertEquals(5, res.newFrequency, "Frequency should be 5 after 4 puts and 1 get");
    }

    // 7. Capacity zero
    public static void test7_CapacityZero() {
        System.out.println("\nTest 7: Capacity zero edge case");
        CampusCache cache = new CampusCache(0);
        CampusCache.OpResult p1 = cache.put("Doc", "Data");
        assertEquals(0, cache.getSize(), "Size must remain 0");

        CampusCache.OpResult g1 = cache.get("Doc");
        assertEquals(false, g1.hit, "Must be a miss");
    }

    // 8. Capacity one
    public static void test8_CapacityOne() {
        System.out.println("\nTest 8: Capacity one edge case");
        CampusCache cache = new CampusCache(1);
        cache.put("File1", "Data1");
        assertEquals(1, cache.getSize(), "Size must be 1");

        // Next PUT must evict File1
        CampusCache.OpResult p2 = cache.put("File2", "Data2");
        assertEquals("File1", p2.evictedKey, "File1 must be evicted");
        assertEquals(1, cache.getSize(), "Size must be 1");

        CampusCache.OpResult g2 = cache.get("File2");
        assertEquals(true, g2.hit, "File2 must be present");
        assertEquals("Data2", g2.value, "File2 data matches");
    }

    // 9. Multiple frequency groups
    public static void test9_MultipleFrequencyGroups() {
        System.out.println("\nTest 9: Multiple frequency groups and state tracking");
        CampusCache cache = new CampusCache(4);
        cache.put("Doc1", "Val1"); // freq 1
        cache.put("Doc2", "Val2"); // freq 1
        cache.put("Doc3", "Val3"); // freq 1
        cache.put("Doc4", "Val4"); // freq 1

        // Promote Doc1 to freq 4
        cache.get("Doc1");
        cache.get("Doc1");
        cache.get("Doc1");

        // Promote Doc2 to freq 2
        cache.get("Doc2");

        // Promote Doc3 to freq 2
        cache.get("Doc3");

        // Groups now:
        // freq 4: Doc1
        // freq 2: Doc3 (MRU), Doc2 (LRU)
        // freq 1: Doc4
        // minFrequency must be 1. Next eviction candidate must be Doc4!
        CacheNode nextCandidate = cache.getNextEvictionCandidate();
        assertEquals("Doc4", nextCandidate.key, "Next eviction candidate must be Doc4 (freq 1)");

        CampusCache.CacheState state = cache.getState();
        assertEquals(3, state.frequencyGroups.size(), "Should have 3 distinct frequency groups (1, 2, 4)");
    }

    // 10. Repeated operations
    public static void test10_RepeatedOperations() {
        System.out.println("\nTest 10: Repeated operations and deterministic behavior");
        CampusCache cache = new CampusCache(3);
        for (int i = 0; i < 50; i++) {
            cache.put("Key" + (i % 5), "Val" + i);
            cache.get("Key" + (i % 3));
        }
        assertTrue(cache.getSize() <= 3, "Size must never exceed capacity");
        CampusCache.CacheState state = cache.getState();
        assertTrue(state.frequencyGroups.size() > 0, "Frequency groups should be maintained");
    }

    // Mandatory Demonstration 1 from Problem Statement:
    // Capacity = 2
    // PUT(A,10)
    // PUT(B,20)
    // GET(A)
    // PUT(C,30)
    // STATE
    // Expected: GET(A) -> 10, B evicted (B freq 1 vs A freq 2), Final: A(10, freq 2), C(30, freq 1)
    public static void testDemo1_HackathonSpecification() {
        System.out.println("\nTest Demo 1: Hackathon Problem Statement Sequence");
        CampusCache cache = new CampusCache(2);
        cache.put("A", "10");
        cache.put("B", "20");
        CampusCache.OpResult getA = cache.get("A");
        assertEquals("10", getA.value, "GET(A) must return 10");
        assertEquals(2, getA.newFrequency, "A's frequency must be 2");

        CampusCache.OpResult putC = cache.put("C", "30");
        assertEquals("B", putC.evictedKey, "B must be evicted");
        assertEquals(1, putC.evictedFrequency, "Evicted item B had frequency 1");

        CampusCache.CacheState state = cache.getState();
        assertEquals(2, state.currentSize, "Final cache size must be 2");

        Map<String, String> kv = cache.getDirectKeyValues();
        assertEquals("10", kv.get("A"), "A must have value 10");
        assertEquals("30", kv.get("C"), "C must have value 30");
        assertEquals(false, kv.containsKey("B"), "B must not be in cache");
    }

    // Demonstration 2: Frequency Ties & LRU Tie-breaking
    // Capacity = 2
    // PUT(A, 100) -> freq 1
    // PUT(B, 200) -> freq 1 (MRU=B, LRU=A)
    // PUT(C, 300) -> Both A and B have freq 1. A was inserted first (LRU), so A must be evicted!
    // Final cache: B(200, freq 1), C(300, freq 1)
    public static void testDemo2_LruTieBreakingSpecification() {
        System.out.println("\nTest Demo 2: LRU Tie-Breaking Verification");
        CampusCache cache = new CampusCache(2);
        cache.put("A", "100");
        cache.put("B", "200");
        CampusCache.OpResult putC = cache.put("C", "300");

        assertEquals("A", putC.evictedKey, "A must be evicted because of LRU recency tie-breaking");
        Map<String, String> kv = cache.getDirectKeyValues();
        assertEquals(true, kv.containsKey("B"), "B must remain");
        assertEquals(true, kv.containsKey("C"), "C must remain");
        assertEquals(false, kv.containsKey("A"), "A must be gone");
    }
}
