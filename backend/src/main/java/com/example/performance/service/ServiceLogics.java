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

@Service
public class ServiceLogics {

    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;

    // Small in-memory cache to support the caching demo
    private final Map<Long, Customer> customerCache = new ConcurrentHashMap<>();

    public ServiceLogics(CustomerRepository customerRepository,
                         OrderItemRepository orderItemRepository) {
        this.customerRepository = customerRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 1a) N+1 BEFORE → Loop with one query per customer id.
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
     */
    @Transactional(readOnly = true)
    public Map<Long, List<OrderItem>> batchFetchOrderItems(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Collections.emptyMap();
        List<OrderItem> all = orderItemRepository.findByCustomerIds(customerIds);
        return all.stream().collect(Collectors.groupingBy(oi -> oi.getCustomer().getId()));
    }

    /**
     * 2a) Memory BEFORE: keep references so memory stays high.
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
     * 2b) Memory AFTER: release references and hint GC so memory footprint drops.
     * Returns memory delta and a tiny result count to keep the demo deterministic.
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
     * 3a) BEFORE: repeated O(n) scans using list stream anyMatch.
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
     * 3b) AFTER: O(n) → O(1) lookups via HashMap.
     * Build once (O(size)) and reuse for many lookups (O(repeats)).
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
     * 4a) BEFORE: Streams with side-effects via peek().
     */
    public int streamWithPeekSideEffects(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        List<Integer> out = new ArrayList<>();
        list.stream().peek(out::add).map(i -> i * 2).toList();
        return out.size();
    }

    /**
     * 4b) AFTER: stream side-effects → simple for-loop.
     * No hidden mutation, very explicit, often faster for large inputs.
     */
    public int streamFreeOfSideEffects(int size) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(i);
            int x = i * 2; // simulates downstream processing
        }
        return out.size();
    }

    /**
     * 5a) BEFORE: Fetch customers individually (likely many DB round-trips).
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
     * 5b) AFTER: Cache prefetch: read missing customers in one batch and serve from cache.
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
     * 6a) BEFORE: Sequential compute (single core).
     */
    public long sequentialComputeSum(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        return list.stream().mapToLong(this::heavyCompute).sum();
    }

    /**
     * 6b) AFTER: Parallelize CPU-bound work across cores for faster throughput.
     */
    public long parallelComputeSum(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        return list.parallelStream().mapToLong(this::heavyCompute).sum();
    }

    // ===== helpers =====

    private long heavyCompute(int x) {
        long r = 0;
        for (int i = 0; i < 1000; i++) r += (x + i) % 7;
        return r;
    }

    private long currentUsedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Simple value object for the memory demo. */
    public static class MemoryResult {
        public final int sum;
        public final long memoryUsedBytes;
        public MemoryResult(int sum, long memoryUsedBytes) {
            this.sum = sum;
            this.memoryUsedBytes = memoryUsedBytes;
        }
    }
}


