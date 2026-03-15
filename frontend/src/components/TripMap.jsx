import { MapContainer, Marker, Polyline, Popup, TileLayer } from "react-leaflet";

function flattenPlaces(trip) {
  if (!trip) return [];
  return trip.days.flatMap((d) =>
    d.places.map((p) => ({
      key: `${d.dayNumber}-${p.placeId}`,
      dayNumber: d.dayNumber,
      name: p.name,
      lat: p.latitude,
      lng: p.longitude
    }))
  );
}

function resolveTileConfig() {
  const customUrl = import.meta.env.VITE_TILE_URL;
  const customAttribution = import.meta.env.VITE_TILE_ATTRIBUTION;
  const stadiaApiKey = import.meta.env.VITE_STADIA_API_KEY;
  if (customUrl) {
    return {
      url: customUrl,
      attribution:
        customAttribution ||
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    };
  }

  const isFileProtocol = typeof window !== "undefined" && window.location?.protocol === "file:";
  if (isFileProtocol) {
    if (stadiaApiKey) {
      return {
        url: `https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png?api_key=${stadiaApiKey}`,
        attribution:
          '&copy; <a href="https://stadiamaps.com/">Stadia Maps</a>, &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      };
    }

    return {
      url: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
    };
  }

  return {
    url: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  };
}

export function TripMap({ trip }) {
  const points = flattenPlaces(trip);
  const center = points.length ? [points[0].lat, points[0].lng] : [22.9734, 78.6569];
  const route = points.map((p) => [p.lat, p.lng]);
  const tileConfig = resolveTileConfig();

  return (
    <div className="map-wrap">
      <h2>Route View</h2>
      <MapContainer center={center} zoom={5} scrollWheelZoom style={{ height: "360px", width: "100%" }}>
        <TileLayer attribution={tileConfig.attribution} url={tileConfig.url} />
        {points.map((point) => (
          <Marker key={point.key} position={[point.lat, point.lng]}>
            <Popup>Day {point.dayNumber}: {point.name}</Popup>
          </Marker>
        ))}
        {route.length > 1 ? <Polyline positions={route} /> : null}
      </MapContainer>
    </div>
  );
}
