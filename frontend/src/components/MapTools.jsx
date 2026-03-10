import { useState } from "react";
import { estimateRoute, searchMapPlaces } from "../api/client";
import { ReactBitsHoverText } from "./ReactBitsHoverText";

export function MapTools() {
  const [city, setCity] = useState("");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [route, setRoute] = useState(null);
  const [error, setError] = useState("");

  async function onSearch() {
    if (!city.trim() || !query.trim()) {
      setError("Enter city and prompt before searching.");
      return;
    }
    setError("");
    try {
      const items = await searchMapPlaces(city, query);
      setResults(items);
      setRoute(null);
      if (items.length >= 2) {
        const routeEstimate = await estimateRoute({
          fromLat: items[0].latitude,
          fromLng: items[0].longitude,
          toLat: items[1].latitude,
          toLng: items[1].longitude
        });
        setRoute(routeEstimate);
      }
    } catch {
      setError("Map API request failed. Try again shortly.");
    }
  }

  return (
    <div className="map-tools">
      <h2><ReactBitsHoverText text="Free Map API Tools" /></h2>
      <div className="tool-row">
        <input value={city} onChange={(e) => setCity(e.target.value)} placeholder="City" />
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search term" />
        <button type="button" onClick={onSearch}>Search</button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {route ? (
        <p className="route-hint">
          Top two results distance: {route.distanceKm} km | time: ~{route.durationMinutes} min
        </p>
      ) : null}
      <ul className="ext-results">
        {results.map((item) => (
          <li key={`${item.displayName}-${item.latitude}`}>
            <strong><ReactBitsHoverText text={item.type} /></strong>
            <span><ReactBitsHoverText text={item.displayName} /></span>
          </li>
        ))}
      </ul>
    </div>
  );
}
