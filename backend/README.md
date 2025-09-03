# Performance Demo - Backend

Spring Boot backend exposing endpoints to demonstrate common performance issues and optimized solutions.

## Requirements
- Java 17+
- Maven 3.9+
- PostgreSQL running locally with DB `performancedb` and credentials `postgres/postgres`

## Setup
1. Create database:
   - createdb performancedb (or use pgAdmin)
2. Start application:
   - mvn -f backend/pom.xml spring-boot:run
3. Data will be loaded from `schema.sql` and `data.sql` automatically on first start.

## Endpoints
Base URL: `http://localhost:8080/api/performance`

- N+1
  - POST `/nplus1/before` body: `[1,2,3]`
  - POST `/nplus1/after` body: `[1,2,3]`
- Memory
  - GET `/memory/before?size=20000`
  - GET `/memory/after?size=20000`
- Lookup
  - GET `/lookup/before?size=200000&target=199999`
  - GET `/lookup/after?size=200000&target=199999`
- Streams
  - GET `/stream/before?size=200000`
  - GET `/stream/after?size=200000`
- Cache
  - POST `/cache/before` body: `[1,2,3]`
  - POST `/cache/after` body: `[1,2,3]`
- Parallel
  - GET `/parallel/before?size=400000`
  - GET `/parallel/after?size=400000`

Responses include `executionTimeMs` and a small `result` summary. Detailed SQL logs available at INFO level.

## Postman
Import `backend/POSTMAN_collection.json` into Postman. Update host/port if needed.

## Notes
- JPA `open-in-view` is disabled.
- JDBC batching enabled for better insert/update ordering.
