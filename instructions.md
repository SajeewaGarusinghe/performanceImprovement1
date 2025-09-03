# 🚀 Optimized Prompt for Demo Application

## 🎯 Goal

Create a **demo application** (Spring Boot backend + minimal frontend + local PostgreSQL database) to **showcase performance issues and their optimized solutions**.

The app should be **production-quality but simple** — only implement what’s needed to demonstrate the improvements. Include:

- 📊 Before vs After execution time logs
- 🗃️ PostgreSQL schema + SQL insert scripts (for sample data)
- 🖥️ Basic frontend page(s) to trigger backend APIs and display results + timings

**Database credentials:**

```
username: postgres
password: postgres
database: performancedb

```

---

## 🐌 Problems to Demonstrate & ✅ Solutions

### 1. **N+1 Query Problem → Batch Querying**

- **Before:** Looping DB calls (`for` loop with repository calls).
- **After:** Single batch query with `IN` clause.
- **Impact:** From 50+ queries → 1 query.

### 2. **Memory Leaks → Explicit Cleanup**

- **Before:** Large lists retained unnecessarily.
- **After:** Nullify references after processing.
- **Impact:** 50% reduction in memory footprint.

### 3. **Inefficient Lookups → HashMap Optimization**

- **Before:** `O(n)` stream filtering.
- **After:** Pre-built `HashMap` for `O(1)` lookups.
- **Impact:** 90% faster lookups.

### 4. **Stream Side-Effects → Clean For-Loop**

- **Before:** Using `.peek()` with side effects.
- **After:** Simple `for` loop with explicit operations.
- **Impact:** Improved readability & performance.

### 5. **Caching → Workflow Context Pre-Fetch**

- **Before:** Fetching repeatedly from DB.
- **After:** Pre-fetch data + cache lookups.
- **Impact:** Faster repeated access.

### 6. **Parallel Processing**

- **Before:** Sequential stream processing.
- **After:** `parallelStream()` for CPU-bound workloads.
- **Impact:** Reduced processing time.

---

## 📊 Performance Tracking

- Add logging to **measure execution time** before and after each operation.

```java
long start = System.currentTimeMillis();
// do work
logger.info("Execution Time: " + (System.currentTimeMillis() - start) + " ms");

```

---

## 📦 Deliverables

The demo application should include:

1. **Backend (Spring Boot)**
    - REST APIs for each problem/solution pair
    - Logging execution times
    - Service methods showing "Before" and "After" versions
2. **Frontend (minimal, e.g., React  )**
    - Buttons to trigger each API ("Run Before", "Run After")
    - Display results + execution times side by side
3. **Database (PostgreSQL)**
    - SQL script to create schema + insert sample test data
    - Ensure enough rows to make performance differences visible
4. **Documentation**
    - README with instructions to run backend, frontend, and DB setup
    - Short explanation of each problem + solution

---

✅ Keep the app **small, focused, and demo-ready** — no extra business logic, just enough to showcase the improvements.

include .gitignore file as well
no need to consider about dockerisation

---

Would you like me to **draft a full structure** (folders + sample SQL + Spring Boot entities, repository, service stubs, and React/Thymeleaf frontend skeleton) so you can directly start building this demo?