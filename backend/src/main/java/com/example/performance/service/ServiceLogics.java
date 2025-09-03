package com.example.performance.service;

import com.example.performance.entity.Customer;
import com.example.performance.entity.OrderItem;
import com.example.performance.repository.CustomerRepository;
import com.example.performance.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service containing paired "before vs after" implementations that illustrate
 * common performance pitfalls and their corresponding improvements across
 * database access, memory usage, algorithmic complexity, stream usage,
 * caching, and parallelization.
 *
 * Each pair is intentionally simple and focused on one concept so the
 * trade-offs are easy to understand. The methods are used by the REST layer to
 * demonstrate and compare outcomes.
 */
@Service
public class ServiceLogics {

    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;

    // Small in-memory cache to support the caching demo
    private final Map<Long, Customer> customerCache = new ConcurrentHashMap<>();

    // Used to consume values in demos to prevent "unused variable" warnings
    private static volatile long blackhole;

    /**
     * Construct the service with required repositories.
     *
     * Note: Repositories are thread-safe Spring proxies.
     */
    public ServiceLogics(CustomerRepository customerRepository,
                         OrderItemRepository orderItemRepository) {
        this.customerRepository = customerRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 1a) N+1 BEFORE → Loop with one query per customer id.
     *
     * Issues: triggers N DB round-trips for N ids. Becomes very slow with many
     * ids or higher latency. Shown here to contrast with the batched alternative.
     *
     * @param customerIds list of customer ids (may be null)
     * @return map from customer id to its order items; empty map if input is null
     */
    @Transactional(readOnly = true)
    public Map<Long, List<OrderItem>> nPlusOneLoopFetch(List<Long> customerIds) {
        Map<Long, List<OrderItem>> result = new HashMap<>();
        if (customerIds == null) return result;
        for (Long id : customerIds) {
            List<OrderItem> items = orderItemRepository.findByCustomerId(id);
            result.put(id, items);
        }
        return result;
    }

    /**
     * 1b) N+1 AFTER → Batch query using IN clause, then group in memory.
     * Single DB round-trip instead of N.
     *
     * Complexity: O(n) DB fetch + O(n) grouping. Good default when you need
     * many related rows by foreign key.
     *
     * @param customerIds ids to fetch (null or empty yields empty map)
     * @return grouping from customer id to order items
     */
    @Transactional(readOnly = true)
    public Map<Long, List<OrderItem>> batchFetchOrderItems(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Collections.emptyMap();
        List<OrderItem> all = orderItemRepository.findByCustomerIds(customerIds);
        return all.stream().collect(Collectors.groupingBy(oi -> oi.getCustomer().getId()));
    }

    /**
     * 2a) Memory BEFORE: keeps references so memory usage remains elevated
     * after the work is done. Demonstrates how lingering references prevent
     * garbage collection.
     *
     * @param size number of arrays to allocate
     * @return sum of allocated array lengths and memory delta from baseline
     */
    public MemoryResult performWorkNoCleanup(int size) {
        long baseline = currentUsedMemoryBytes();
        List<int[]> heavy = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            heavy.add(new int[1024]);
        }
        int sum = heavy.stream().mapToInt(arr -> arr.length).sum();
        long used = currentUsedMemoryBytes();
        long delta = Math.max(0, used - baseline);
        return new MemoryResult(sum, delta);
    }

    /**
     * 2b) Memory AFTER: explicitly releases references and hints the GC,
     * allowing memory to be reclaimed. System.gc() is not guaranteed to run,
     * but is adequate for demo purposes.
     *
     * @param size number of arrays to allocate
     * @return sum of allocated array lengths and memory delta from baseline
     */
    public MemoryResult performWorkAndCleanup(int size) {
        long baseline = currentUsedMemoryBytes();
        List<int[]> heavy = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            heavy.add(new int[1024]);
        }
        int sum = heavy.stream().mapToInt(arr -> arr.length).sum();
        // critical: drop references so GC can reclaim memory
        heavy.clear();
        heavy = null;
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        long used = currentUsedMemoryBytes();
        long delta = Math.max(0, used - baseline);
        return new MemoryResult(sum, delta);
    }

    /**
     * 3a) BEFORE: performs repeated O(n) scans via anyMatch on a List.
     * Builds the list and scans it 'repeats' times.
     *
     * @param size list size to build
     * @param target value to search for
     * @param repeats number of repeated lookups
     * @return number of times the value was found (0..repeats)
     */
    public int lookupUsingListAnyMatch(int size, int target, int repeats) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        int hits = 0;
        for (int i = 0; i < repeats; i++) {
            if (list.stream().anyMatch(x -> x == target)) hits++;
        }
        return hits;
    }

    /**
     * 3b) AFTER: Promotes O(n) lookups to O(1) average-case via HashMap.
     * Build once (O(size)) then reuse for many lookups (O(repeats)).
     *
     * @param size count of keys to prefill in the map
     * @param target key to look up repeatedly
     * @param repeats number of repeated lookups
     * @return number of times the key was found
     */
    public int lookupUsingHashMap(int size, int target, int repeats) {
        Map<Integer, Boolean> map = new HashMap<>(Math.max(16, size * 2));
        for (int i = 0; i < size; i++) map.put(i, Boolean.TRUE);
        int hits = 0;
        for (int i = 0; i < repeats; i++) {
            if (map.containsKey(target)) hits++;
        }
        return hits;
    }

    /**
     * 4a) BEFORE: Uses a stream with side-effects via peek(). This is harder
     * to reason about and may be slower for large inputs.
     *
     * @param size number of elements to process
     * @return number of elements observed via side-effect list
     */
    public int streamWithPeekSideEffects(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        List<Integer> out = new ArrayList<>();
        list.stream().peek(out::add).map(i -> i * 2).toList();
        return out.size();
    }

    /**
     * 4b) AFTER: Replaces stream-side effects with an explicit for-loop.
     * No hidden mutation; often clearer and faster at scale.
     *
     * @param size number of elements to process
     * @return number of elements produced in the output list
     */
    public int streamFreeOfSideEffects(int size) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(i);
            // simulates downstream processing without allocating extra collections
            blackhole ^= (long) (i * 2);
        }
        // Consume the field in a no-op way so static analysis sees a read
        return out.size() + (int) (blackhole & 0L);
    }

    /**
     * 5a) BEFORE: Fetches customers individually, causing many DB round-trips
     * for many ids (N queries). Shown as a baseline.
     *
     * @param customerIds ids to fetch
     * @return list of customers in the order they were found
     */
    @Transactional(readOnly = true)
    public List<Customer> fetchCustomersIndividually(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Collections.emptyList();
        List<Customer> out = new ArrayList<>();
        for (Long id : customerIds) {
            customerRepository.findById(id).ifPresent(out::add);
        }
        return out;
    }

    /**
     * 5b) AFTER: Cache prefetch. Reads missing customers in a single batch and
     * serves from a small in-memory cache. Greatly reduces repeated DB calls
     * across requests within the same JVM.
     *
     * Caution: This is a tiny demo cache with no invalidation/TTL. In a real
     * app, prefer a proper cache (Caffeine/Redis) and an invalidation strategy.
     *
     * @param customerIds ids to fetch
     * @return list of customers corresponding to the provided ids that exist
     */
    @Transactional(readOnly = true)
    public List<Customer> cachePrefetch(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Collections.emptyList();
        List<Long> missing = customerIds.stream()
                .filter(id -> !customerCache.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            List<Customer> found = customerRepository.findAllById(missing);
            for (Customer c : found) customerCache.put(c.getId(), c);
        }
        return customerIds.stream()
                .map(customerCache::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 6a) BEFORE: Sequential compute on a single core.
     *
     * @param size number of items to compute over
     * @return sum of heavyCompute across the sequence
     */
    public long sequentialComputeSum(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        return list.stream().mapToLong(this::heavyCompute).sum();
    }

    /**
     * 6b) AFTER: Parallelizes CPU-bound work across cores for faster throughput.
     *
     * Note: Parallel streams use the common ForkJoinPool. Ensure CPU-bound work
     * and large-enough data sizes to benefit. Consider custom pools for
     * isolation in real systems.
     *
     * @param size number of items to compute over
     * @return sum of heavyCompute across the sequence
     */
    public long parallelComputeSum(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        return list.parallelStream().mapToLong(this::heavyCompute).sum();
    }

    // ===== helpers =====

    /**
     * CPU-bound toy function to simulate per-element work.
     */
    private long heavyCompute(int x) {
        long r = 0;
        for (int i = 0; i < 1000; i++) r += (x + i) % 7;
        return r;
    }

    /**
     * Best-effort snapshot of process heap usage at call time.
     */
    private long currentUsedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * Simple value object for the memory demo. Carries a small deterministic
     * result ('sum') along with the observed change in used heap memory.
     */
    public static class MemoryResult {
        /** Sum of lengths of allocated arrays, acts as a deterministic payload. */
        public final int sum;
        /** Observed delta in used heap bytes from before to after the work. */
        public final long memoryUsedBytes;
        public MemoryResult(int sum, long memoryUsedBytes) {
            this.sum = sum;
            this.memoryUsedBytes = memoryUsedBytes;
        }
    }
}


