CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(180) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    budget_inr INT NOT NULL,
    pace VARCHAR(50) NOT NULL,
    preferred_travel_mode VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trip_days (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    city VARCHAR(120) NOT NULL,
    theme VARCHAR(120) NOT NULL,
    notes TEXT,
    estimated_cost_inr INT NOT NULL,
    total_travel_minutes INT NOT NULL,
    UNIQUE (trip_id, day_number)
);

CREATE TABLE IF NOT EXISTS places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    average_cost_inr INT NOT NULL,
    visit_minutes INT NOT NULL,
    rating DECIMAL(2,1) NOT NULL,
    description TEXT NOT NULL,
    UNIQUE (name, city)
);

CREATE TABLE IF NOT EXISTS trip_day_places (
    id BIGSERIAL PRIMARY KEY,
    trip_day_id BIGINT NOT NULL REFERENCES trip_days(id) ON DELETE CASCADE,
    place_id BIGINT NOT NULL REFERENCES places(id),
    sort_order INT NOT NULL,
    arrival_time TIME NOT NULL,
    departure_time TIME NOT NULL,
    transport_mode VARCHAR(50) NOT NULL,
    travel_minutes INT NOT NULL,
    UNIQUE (trip_day_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_places_city ON places(city);
CREATE INDEX IF NOT EXISTS idx_places_category ON places(category);
