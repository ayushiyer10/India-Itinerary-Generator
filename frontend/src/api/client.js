import axios from "axios";

// Local-only mode: always use local Spring Boot backend
const API_BASE_URL = "http://localhost:8080/api";

console.log("API BASE URL =", API_BASE_URL); // debug

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 90000,
});

function getFallbackBaseUrl() {
  if (API_BASE_URL.endsWith("/api")) {
    return API_BASE_URL.slice(0, -4);
  }
  return `${API_BASE_URL.replace(/\/+$/, "")}/api`;
}

async function postWithFallback(path, payload) {
  try {
    const { data } = await api.post(path, payload);
    return data;
  } catch (error) {
    if (error?.response?.status !== 404) {
      throw error;
    }
    const fallbackBaseUrl = getFallbackBaseUrl();
    const { data } = await axios.post(`${fallbackBaseUrl}${path}`, payload, {
      timeout: 90000,
    });
    return data;
  }
}

async function getWithFallback(path, config) {
  try {
    const { data } = await api.get(path, config);
    return data;
  } catch (error) {
    if (error?.response?.status !== 404) {
      throw error;
    }
    const fallbackBaseUrl = getFallbackBaseUrl();
    const { data } = await axios.get(`${fallbackBaseUrl}${path}`, {
      timeout: 90000,
      ...(config || {}),
    });
    return data;
  }
}

// ---------- TRIPS ----------
export async function generateTrip(payload) {
  return postWithFallback("/trips/generate", payload);
}

export async function getTrip(tripId) {
  return getWithFallback(`/trips/${tripId}`);
}

export async function regenerateDay(tripId, dayNumber, payload) {
  await postWithFallback(`/trips/${tripId}/regenerate-day/${dayNumber}`, payload);
}

// ---------- MAP ----------
export async function searchMapPlaces(city, query) {
  return getWithFallback("/maps/search", {
    params: { city, query, limit: 5 },
  });
}

export async function estimateRoute(payload) {
  return postWithFallback("/maps/route-estimate", payload);
}

// ---------- AUTH ----------
export async function signup(payload) {
  return postWithFallback("/auth/signup", payload);
}

export async function login(payload) {
  return postWithFallback("/auth/login", payload);
}
