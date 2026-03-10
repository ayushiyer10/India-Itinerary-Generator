import { MapContainer, Marker, Polyline, Popup, TileLayer } from "react-leaflet";
import { ReactBitsHoverText } from "./ReactBitsHoverText";

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

export function TripMap({ trip }) {
  const points = flattenPlaces(trip);
  const center = points.length ? [points[0].lat, points[0].lng] : [22.9734, 78.6569];
  const route = points.map((p) => [p.lat, p.lng]);

  return (
    <div className="map-wrap">
      <h2><ReactBitsHoverText text="Route View" /></h2>
      <MapContainer center={center} zoom={5} scrollWheelZoom style={{ height: "360px", width: "100%" }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
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
