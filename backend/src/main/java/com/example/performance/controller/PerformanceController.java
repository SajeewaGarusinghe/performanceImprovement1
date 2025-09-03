package com.example.performance.controller;

import com.example.performance.service.PerformanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    // 1. N+1
    @PostMapping("/nplus1/before")
    public ResponseEntity<Map<String, Object>> nplus1Before(@RequestBody List<Long> customerIds) {
        return ResponseEntity.ok(performanceService.nPlusOneBefore(customerIds));
    }

    @PostMapping("/nplus1/after")
    public ResponseEntity<Map<String, Object>> nplus1After(@RequestBody List<Long> customerIds) {
        return ResponseEntity.ok(performanceService.nPlusOneAfter(customerIds));
    }

    // 2. Memory
    @GetMapping("/memory/before")
    public ResponseEntity<Map<String, Object>> memoryBefore(@RequestParam(defaultValue = "10000") int size) {
        return ResponseEntity.ok(performanceService.memoryBefore(size));
    }

    @GetMapping("/memory/after")
    public ResponseEntity<Map<String, Object>> memoryAfter(@RequestParam(defaultValue = "10000") int size) {
        return ResponseEntity.ok(performanceService.memoryAfter(size));
    }

    // 3. Lookup
    @GetMapping("/lookup/before")
    public ResponseEntity<Map<String, Object>> lookupBefore(@RequestParam(defaultValue = "100000") int size,
                                                            @RequestParam(defaultValue = "99999") int target) {
        return ResponseEntity.ok(performanceService.lookupBefore(size, target));
    }

    @GetMapping("/lookup/after")
    public ResponseEntity<Map<String, Object>> lookupAfter(@RequestParam(defaultValue = "100000") int size,
                                                           @RequestParam(defaultValue = "99999") int target) {
        return ResponseEntity.ok(performanceService.lookupAfter(size, target));
    }

    // 4. Streams vs loops
    @GetMapping("/stream/before")
    public ResponseEntity<Map<String, Object>> streamBefore(@RequestParam(defaultValue = "100000") int size) {
        return ResponseEntity.ok(performanceService.streamSideEffectsBefore(size));
    }

    @GetMapping("/stream/after")
    public ResponseEntity<Map<String, Object>> streamAfter(@RequestParam(defaultValue = "100000") int size) {
        return ResponseEntity.ok(performanceService.streamSideEffectsAfter(size));
    }

    // 5. Cache
    @PostMapping("/cache/before")
    public ResponseEntity<Map<String, Object>> cacheBefore(@RequestBody List<Long> customerIds) {
        return ResponseEntity.ok(performanceService.cacheBefore(customerIds));
    }

    @PostMapping("/cache/after")
    public ResponseEntity<Map<String, Object>> cacheAfter(@RequestBody List<Long> customerIds) {
        return ResponseEntity.ok(performanceService.cacheAfter(customerIds));
    }

    // 6. Parallel
    @GetMapping("/parallel/before")
    public ResponseEntity<Map<String, Object>> parallelBefore(@RequestParam(defaultValue = "200000") int size) {
        return ResponseEntity.ok(performanceService.parallelBefore(size));
    }

    @GetMapping("/parallel/after")
    public ResponseEntity<Map<String, Object>> parallelAfter(@RequestParam(defaultValue = "200000") int size) {
        return ResponseEntity.ok(performanceService.parallelAfter(size));
    }
}


