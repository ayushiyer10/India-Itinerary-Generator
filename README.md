<<<<<<< HEAD
# 🇮🇳 India AI Itinerary Planner

An intelligent desktop application that automatically generates optimized travel itineraries across India.
The system combines **AI-assisted trip planning, geographic search, and route estimation** to help travelers create detailed multi-day travel plans with minimal effort.

The application uses a **React + Electron desktop frontend**, a **Spring Boot backend**, and a **PostgreSQL database** for storing trips, places, and itinerary data.

---

# ✨ Features

• Generate **multi-day travel itineraries** automatically
• Smart **place recommendations based on city and interests**
• **Route estimation and travel time calculation** between places
• **Budget-aware trip planning**
• **Day regeneration** for itinerary adjustments
• **User authentication (login/signup)**
• Interactive **map-based place search**
• Desktop application packaging using **Electron**

---

# 🏗️ Project Architecture

The system follows a **layered architecture**:

```
Frontend (React + Electron)
        ↓
REST APIs
        ↓
Spring Boot Backend
        ↓
Service Layer (Business Logic)
        ↓
Repository Layer (JdbcTemplate SQL)
        ↓
PostgreSQL Database
```

### Tech Stack

| Layer           | Technology         |
| --------------- | ------------------ |
| Frontend        | React, Vite        |
| Desktop Runtime | Electron           |
| Backend         | Spring Boot (Java) |
| Database        | PostgreSQL         |
| Database Access | JdbcTemplate       |
| Maps & Routing  | OpenStreetMap APIs |
| Packaging       | Electron Builder   |

---

# 📂 Project Structure

```
backend/
 ├── controller/        → REST API endpoints
 ├── service/           → Business logic
 ├── repository/        → Database queries
 ├── dto/               → Data transfer models
 ├── config/            → Application configuration
 └── IndiaItineraryApplication.java

frontend/
 ├── src/
 │   ├── pages
 │   ├── components
 │   └── api
 ├── electron/          → Electron main process
 └── dist/              → Production build

database/
 └── PostgreSQL tables
```

---

# 🗄️ Database Design

Main tables used in the system:

```
users
trips
trip_days
trip_day_places
places
```

Relationships:

```
users
   │
   └── trips
           │
           └── trip_days
                   │
                   └── trip_day_places
                            │
                            └── places
```

---

# ⚙️ Backend Setup (Spring Boot)

### Requirements

• Java 21
• Maven
• PostgreSQL

### Run Backend

```
cd backend
mvn clean install
mvn spring-boot:run
```

Backend will start at:

```
http://localhost:8080
```

Health check:

```
http://localhost:8080/actuator/health
```

---

# 💻 Frontend Setup

```
=======
# India AI Itinerary Planner

Production-style travel planner for India with:

- Java 21 + Spring Boot + JDBC (PostgreSQL)
- React + Vite + Anime.js interactive UI
- Free map data stack (OpenStreetMap + routing-ready design)
- Dockerized local/dev deployment

## Monorepo Layout

```text
.
|- backend/                  # Spring Boot API + JDBC
|- frontend/                 # React app with animations
|- javafx-client/            # JavaFX desktop GUI client
|- docker-compose.yml        # Optional local full-stack containers
```

## Quick Start (Local)

1. Start full stack with Docker:
```bash
docker compose up --build
```
2. Open:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080/api`

## Manual Run (Without Docker)

1. Ensure PostgreSQL database exists: `india_itinerary` (default credentials: `postgres` / `postgres`)
2. Configure env vars:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
3. Backend:
```bash
cd backend
mvn spring-boot:run
```
4. Frontend:
```bash
>>>>>>> 412c8b8 (Initial commit)
cd frontend
npm install
npm run dev
```

<<<<<<< HEAD
Vite development server:

```
http://localhost:5173
```

---

# 🖥️ Build Desktop Application

To create a portable `.exe` desktop application:

```
cd frontend
npm run dist
```

The executable will be generated inside:

```
frontend/release/
```

Example output:

```
India AI Itinerary Planner.exe
```

---

# 🔌 API Endpoints

### Authentication

```
POST /api/auth/signup
POST /api/auth/login
```

### Trip Management

```
POST /api/trips/generate
GET  /api/trips/{tripId}
POST /api/trips/{tripId}/regenerate-day/{dayNumber}
```

### Maps & Routing

```
GET  /api/maps/search
POST /api/maps/route-estimate
```

---

# 🧠 Itinerary Generation Logic

The itinerary engine:

1. Retrieves **places using Leaflet API** based on city and interests
2. Sorts locations by **rating and relevance**
3. Calculates **travel time between places**
4. Distributes places across **trip days**
5. Maintains **budget and travel pace constraints**

---

# 🔒 Security

Authentication uses:

• User login/signup APIs
• Password validation and database storage
• API response DTOs to prevent exposing internal entities

---

# 📊 Example Workflow

```
User enters trip preferences
        ↓
Frontend sends request
        ↓
POST /api/trips/generate
        ↓
Backend creates optimized itinerary
        ↓
Trip stored in PostgreSQL
        ↓
Frontend displays itinerary with maps
```

---

# 🚀 Future Improvements

• AI-based recommendation engine
• hotel and restaurant suggestions
• weather integration
• real-time transport APIs
• trip sharing and collaboration
• mobile application version

---

# 👨‍💻 Author

**Ayush Iyer**

Computer Application Student
Project: AI Travel Planner Desktop Application

---

# 📜 License

This project is licensed under the **MIT License**.
=======
5. JavaFX desktop client:
```bash
cd javafx-client
mvn javafx:run
```

The JavaFX client calls `http://localhost:8080/api` by default and lets you generate/view trips in a desktop GUI.

## Notes

- Database schema auto-creates at startup via `schema.sql` and seeds sample India places via `data.sql`.
- AI adapter is intentionally pluggable. Current implementation is deterministic so it works without paid APIs.
>>>>>>> 412c8b8 (Initial commit)
