import axios from "axios";

// Always use localhost backend in Electron / production
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

console.log("API BASE URL =", API_BASE_URL); // debug

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// ---------- TRIPS ----------
export async function generateTrip(payload) {
  const { data } = await api.post(`${API_BASE_URL}/trips/generate`, payload);
  return data;
}

export async function getTrip(tripId) {
  const { data } = await api.get(`${API_BASE_URL}/trips/${tripId}`);
  return data;
}

export async function regenerateDay(tripId, dayNumber, payload) {
  await api.post(
    `${API_BASE_URL}/trips/${tripId}/regenerate-day/${dayNumber}`,
    payload
  );
}

// ---------- MAP ----------
export async function searchMapPlaces(city, query) {
  const { data } = await api.get(`${API_BASE_URL}/maps/search`, {
    params: { city, query, limit: 5 },
  });
  return data;
}

export async function estimateRoute(payload) {
  const { data } = await api.post(`${API_BASE_URL}/maps/route-estimate`, payload);
  return data;
}

// ---------- AUTH ----------
export async function signup(payload) {
  const { data } = await api.post(`${API_BASE_URL}/auth/signup`, payload);
  return data;
}

export async function login(payload) {
  const { data } = await api.post(`${API_BASE_URL}/auth/login`, payload);
  return data;
}
