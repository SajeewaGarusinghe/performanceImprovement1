package com.example.performance.service;

import com.example.performance.entity.Customer;
import com.example.performance.entity.OrderItem;
import com.example.performance.repository.CustomerRepository;
import com.example.performance.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PerformanceService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceService.class);

    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;

    public PerformanceService(CustomerRepository customerRepository, OrderItemRepository orderItemRepository) {
        this.customerRepository = customerRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // 1. N+1 Problem vs Batch Querying
    @Transactional(readOnly = true)
    public Map<String, Object> nPlusOneBefore(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        Map<Long, List<OrderItem>> result = new HashMap<>();
        for (Long id : customerIds) {
            List<OrderItem> items = orderItemRepository.findByCustomerId(id);
            result.put(id, items);
        }
        long duration = System.currentTimeMillis() - start;
        log.info("N+1 Before Execution Time: {} ms", duration);
        return response(duration, result.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> nPlusOneAfter(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        List<OrderItem> all = orderItemRepository.findByCustomerIds(customerIds);
        Map<Long, List<OrderItem>> grouped = all.stream()
                .collect(Collectors.groupingBy(oi -> oi.getCustomer().getId()));
        long duration = System.currentTimeMillis() - start;
        log.info("N+1 After Execution Time: {} ms", duration);
        return response(duration, grouped.size());
    }

    // 2. Memory Leak vs Cleanup
    public Map<String, Object> memoryBefore(int size) {
        long start = System.currentTimeMillis();
        List<int[]> heavy = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            heavy.add(new int[1024]);
        }
        // simulate processing without cleanup
        int sum = heavy.stream().mapToInt(arr -> arr.length).sum();
        long duration = System.currentTimeMillis() - start;
        log.info("Memory Before Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    public Map<String, Object> memoryAfter(int size) {
        long start = System.currentTimeMillis();
        List<int[]> heavy = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            heavy.add(new int[1024]);
        }
        int sum = heavy.stream().mapToInt(arr -> arr.length).sum();
        // cleanup
        heavy.clear();
        // help GC
        heavy = null;
        long duration = System.currentTimeMillis() - start;
        log.info("Memory After Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    // 3. Inefficient Lookups vs HashMap Optimization
    public Map<String, Object> lookupBefore(int size, int target) {
        long start = System.currentTimeMillis();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        boolean exists = list.stream().anyMatch(x -> x == target);
        long duration = System.currentTimeMillis() - start;
        log.info("Lookup Before Execution Time: {} ms, exists={}", duration, exists);
        return response(duration, exists ? 1 : 0);
    }

    public Map<String, Object> lookupAfter(int size, int target) {
        long start = System.currentTimeMillis();
        Map<Integer, Boolean> map = new HashMap<>();
        for (int i = 0; i < size; i++) map.put(i, Boolean.TRUE);
        boolean exists = map.containsKey(target);
        long duration = System.currentTimeMillis() - start;
        log.info("Lookup After Execution Time: {} ms, exists={}", duration, exists);
        return response(duration, exists ? 1 : 0);
    }

    // 4. Stream Side-Effects vs For-Loop
    public Map<String, Object> streamSideEffectsBefore(int size) {
        long start = System.currentTimeMillis();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        List<Integer> out = new ArrayList<>();
        list.stream()
                .peek(out::add)
                .map(i -> i * 2)
                .toList();
        long duration = System.currentTimeMillis() - start;
        log.info("Stream Before Execution Time: {} ms, outSize={}", duration, out.size());
        return response(duration, out.size());
    }

    public Map<String, Object> streamSideEffectsAfter(int size) {
        long start = System.currentTimeMillis();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(i);
            int x = i * 2;
        }
        long duration = System.currentTimeMillis() - start;
        log.info("Stream After Execution Time: {} ms, outSize={}", duration, out.size());
        return response(duration, out.size());
    }

    // 5. Caching / Prefetch
    private final Map<Long, Customer> customerCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public Map<String, Object> cacheBefore(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        List<Customer> customers = new ArrayList<>();
        for (Long id : customerIds) {
            customers.add(customerRepository.findById(id).orElse(null));
        }
        long duration = System.currentTimeMillis() - start;
        log.info("Cache Before Execution Time: {} ms, size={}", duration, customers.size());
        return response(duration, customers.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cacheAfter(List<Long> customerIds) {
        long start = System.currentTimeMillis();
        List<Long> missing = customerIds.stream()
                .filter(id -> !customerCache.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            List<Customer> found = customerRepository.findAllById(missing);
            for (Customer c : found) customerCache.put(c.getId(), c);
        }
        List<Customer> customers = customerIds.stream()
                .map(customerCache::get)
                .filter(Objects::nonNull)
                .toList();
        long duration = System.currentTimeMillis() - start;
        log.info("Cache After Execution Time: {} ms, size={}", duration, customers.size());
        return response(duration, customers.size());
    }

    // 6. Parallel Processing
    public Map<String, Object> parallelBefore(int size) {
        long start = System.currentTimeMillis();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        long sum = list.stream().mapToLong(i -> heavyCompute(i)).sum();
        long duration = System.currentTimeMillis() - start;
        log.info("Parallel Before Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    public Map<String, Object> parallelAfter(int size) {
        long start = System.currentTimeMillis();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(i);
        long sum = list.parallelStream().mapToLong(this::heavyCompute).sum();
        long duration = System.currentTimeMillis() - start;
        log.info("Parallel After Execution Time: {} ms, sum={}", duration, sum);
        return response(duration, sum);
    }

    private long heavyCompute(int x) {
        long r = 0;
        for (int i = 0; i < 1000; i++) r += (x + i) % 7;
        return r;
    }

    private Map<String, Object> response(long ms, Object resultSize) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executionTimeMs", ms);
        map.put("result", resultSize);
        return map;
    }
}


