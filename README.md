# Performance Demo Application

This repository contains a Spring Boot backend and a React frontend to demonstrate performance issues and optimizations.

## Project Structure
- `backend/`: Spring Boot project (APIs, entities, services, SQL, Postman collection)
- `frontend/`: React + Vite app

## Backend Quick Start
1. Ensure PostgreSQL is running with database `performancedb` and credentials `postgres/postgres`.
2. Run the backend:
   - `mvn -f backend/pom.xml spring-boot:run`
3. Import `backend/POSTMAN_collection.json` into Postman to test APIs.

## Frontend Quick Start
1. Open `frontend/` and install dependencies:
   - `npm install`
2. Start dev server:
   - `npm run dev`
3. App is available at `http://localhost:5173` and talks to the backend at `http://localhost:8080`.

For details, see `backend/README.md` and `frontend/README.md`.
