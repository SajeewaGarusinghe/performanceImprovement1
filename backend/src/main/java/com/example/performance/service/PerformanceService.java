package com.example.performance.service;

import com.example.performance.entity.Customer;
import com.example.performance.entity.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * PerformanceService contains small, self-contained demos that showcase a
 * performance pitfall ("Before") and an optimized version ("After").
 *
 * Each pair focuses on a single idea (DB access pattern, memory usage,
 * algorithmic complexity, streams vs loops, caching, and parallelism).
 *
 * All methods log execution time and most return a tiny result so the
 * frontend can display timings and outcomes side-by-side.
 *
 * Tip for junior developers:
 * - Read the Javadoc above each method to understand the trade-offs.
 * - Prefer the "After" approach in real projects unless there is a clear
 *   reason not to (e.g., tiny datasets, readability concerns, or special
 *   constraints).
 */
@Service
public class PerformanceService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceService.class);

    private final ServiceLogics optimized;

    public PerformanceService( ServiceLogics optimized) {
        this.optimized = optimized;
    }

    /**
     * N+1 BEFORE: Demonstrates the "N+1 query" problem.
     *
     * What happens: For each customer id, we fire an individual query to
     * fetch that customer's order items. If there are N customers, we do N
     * round-trips to the database (plus 1 to read the list).
     *
     * Why it's bad: Many small queries are slow and amplify network latency.
     *
     * Complexity: O(N) database round-trips.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> nPlusOneBefore(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        Map<Long, List<OrderItem>> result = optimized.nPlusOneLoopFetch(customerIds);
        long duration = System.currentTimeMillis() - start;
        log.info("N+1 Before Execution Time: {} ms", duration);
        return response(duration, result.size());
    }

    /**
     * N+1 AFTER: Performs a single batch query using an IN (...) clause and
     * groups results in memory.
     *
     * Why it's better: Only one database round-trip; the DB executes one
     * query and we group results in Java, reducing latency drastically.
     *
     * Complexity: O(1) round-trips; grouping in-memory.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> nPlusOneAfter(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        Map<Long, List<OrderItem>> grouped = optimized.batchFetchOrderItems(customerIds);
        long duration = System.currentTimeMillis() - start;
        log.info("N+1 After Execution Time: {} ms", duration);
        return response(duration, grouped.size());
    }

    /**
     * MEMORY BEFORE: Allocates many arrays and keeps references in a list.
     *
     * What this shows: Holding onto references prevents garbage collection,
     * so the heap footprint increases and remains high.
     *
     * We measure memory used (approximate) before/after allocation and
     * return the delta.
     */
    public Map<String, Object> memoryBefore(int size) {
        long start = System.currentTimeMillis();
        ServiceLogics.MemoryResult r = optimized.performWorkNoCleanup(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Memory Before: time={} ms, deltaBytes={} (sum={})", duration, r.memoryUsedBytes, r.sum);
        return responseMemoryOnly(r.sum, r.memoryUsedBytes);
    }

    /**
     * MEMORY AFTER: Allocates memory similarly, but explicitly releases
     * references (clear + null) and nudges the GC. This reduces retained
     * memory after processing.
     *
     * Note: System.gc() is only a hint, but good enough for a demo. In real
     * services you rarely call GC directly; instead, ensure objects become
     * unreachable as soon as possible.
     */
    public Map<String, Object> memoryAfter(int size) {
        long start = System.currentTimeMillis();
        ServiceLogics.MemoryResult r = optimized.performWorkAndCleanup(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Memory After: time={} ms, deltaBytes={} (sum={})", duration, r.memoryUsedBytes, r.sum);
        return responseMemoryOnly(r.sum, r.memoryUsedBytes);
    }

    /**
     * LOOKUP BEFORE: Performs repeated membership checks using a list stream
     * (anyMatch). Each lookup scans the list linearly, so doing it many times
     * is expensive.
     *
     * Complexity: O(size * repeats).
     */
    public Map<String, Object> lookupBefore(int size, int target, int repeats) {
        long start = System.currentTimeMillis();
        int hits = optimized.lookupUsingListAnyMatch(size, target, repeats);
        long duration = System.currentTimeMillis() - start;
        log.info("Lookup Before Execution Time: {} ms, hits={}", duration, hits);
        return response(duration, hits);
    }

    /**
     * LOOKUP AFTER: Pre-builds a HashMap for O(1) average-time membership
     * checks. We pay a one-time O(size) cost to build the map, then perform
     * O(repeats) O(1) checks.
     *
     * Complexity: O(size + repeats) vs O(size * repeats) before.
     */
    public Map<String, Object> lookupAfter(int size, int target, int repeats) {
        long start = System.currentTimeMillis();
        int hits = optimized.lookupUsingHashMap(size, target, repeats);
        long duration = System.currentTimeMillis() - start;
        log.info("Lookup After Execution Time: {} ms, hits={}", duration, hits);
        return response(duration, hits);
    }

    /**
     * STREAMS BEFORE: Uses a stream with peek() that introduces side-effects
     * (mutating an external list). This is harder to read and can be slower
     * than a simple loop for large inputs.
     */
    public Map<String, Object> streamSideEffectsBefore(int size) {
        long start = System.currentTimeMillis();
        int outSize = optimized.streamWithPeekSideEffects(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Stream Before Execution Time: {} ms, outSize={}", duration, outSize);
        return response(duration, outSize);
    }

    /**
     * STREAMS AFTER: A straightforward for-loop that does the same work.
     *
     * Why it's better: More explicit, easier to reason about, and avoids the
     * overhead and subtle bugs of side-effects inside stream operations.
     */
    public Map<String, Object> streamSideEffectsAfter(int size) {
        long start = System.currentTimeMillis();
        int outSize = optimized.streamFreeOfSideEffects(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Stream After Execution Time: {} ms, outSize={}", duration, outSize);
        return response(duration, outSize);
    }

    // 5. Caching / Prefetch

    /**
     * CACHE BEFORE: Fetch each customer by id with a separate repository call
     * (which typically hits the database). Repeating this across requests is
     * wasteful when data changes infrequently.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> cacheBefore(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        List<Customer> customers = optimized.fetchCustomersIndividually(customerIds);
        long duration = System.currentTimeMillis() - start;
        log.info("Cache Before Execution Time: {} ms, size={}", duration, customers.size());
        return response(duration, customers.size());
    }

    /**
     * CACHE AFTER: Prefetch missing customers in one batch and store them in
     * a thread-safe in-memory cache. Subsequent reads are served from memory
     * (very fast) and we avoid redundant DB calls.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> cacheAfter(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        List<Customer> customers = optimized.cachePrefetch(customerIds);
        long duration = System.currentTimeMillis() - start;
        log.info("Cache After Execution Time: {} ms, size={}", duration, customers.size());
        return response(duration, customers.size());
    }

    /**
     * PARALLEL BEFORE: Sequentially performs a CPU-bound computation across
     * a large list. Uses only one CPU core.
     */
    public Map<String, Object> parallelBefore(int size) {
        long start = System.currentTimeMillis();
        long sum = optimized.sequentialComputeSum(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Parallel Before Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    /**
     * PARALLEL AFTER: Uses parallelStream() to spread CPU-bound work across
     * multiple cores.
     *
     * When to use: Parallel streams can speed up large, CPU-bound tasks.
     * Avoid for IO-bound work or tiny collections where overhead dominates.
     */
    public Map<String, Object> parallelAfter(int size) {
        long start = System.currentTimeMillis();
        long sum = optimized.parallelComputeSum(size);
        long duration = System.currentTimeMillis() - start;
        log.info("Parallel After Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    /**
     * Small artificial workload to simulate a CPU-bound task so the
     * parallel vs sequential difference is visible.
     */
    private long heavyCompute(int x) {
        long r = 0;
        for (int i = 0; i < 1000; i++) r += (x + i) % 7;
        return r;
    }

    /**
     * Helper to build a standard response structure consumed by the
     * frontend. Includes execution time and a tiny result summary.
     */
    private Map<String, Object> response(long ms, Object resultSize) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executionTimeMs", ms);
        map.put("result", resultSize);
        return map;
    }

    /**
     * Same as {@link #response(long, Object)} but also adds memoryUsedBytes
     * so the UI can visualize memory footprint changes.
     */
    private Map<String, Object> responseWithMemory(long ms, Object resultSize, long memoryBytes) {
        Map<String, Object> map = response(ms, resultSize);
        map.put("memoryUsedBytes", memoryBytes);
        return map;
    }

    private Map<String, Object> responseMemoryOnly(Object resultSize, long memoryBytes) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("result", resultSize);
        map.put("memoryUsedBytes", memoryBytes);
        return map;
    }

    /**
     * Rough estimate of current heap usage: total - free. Good enough for
     * demos, but not a precise profiler.
     */
    private long currentUsedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}


