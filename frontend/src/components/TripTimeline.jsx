import { useEffect } from "react";
import anime from "animejs/lib/anime.es.js";
import { ReactBitsHoverText } from "./ReactBitsHoverText";

export function TripTimeline({ trip, loading, onRegenerateDay }) {
  useEffect(() => {
    anime({
      targets: ".day-card",
      opacity: [0, 1],
      translateX: [20, 0],
      easing: "easeOutCubic",
      delay: anime.stagger(100)
    });
  }, [trip]);

  if (!trip) {
    return <p className="placeholder">Generate a trip to see the interactive timeline.</p>;
  }

  return (
    <div>
      <h2>Day-by-Day Plan</h2>
      <div className="day-grid">
        {trip.days.map((day) => (
          <article key={day.dayNumber} className="day-card reactbits-card">
            <header className="day-card-top">
              <div>
                <h3>Day {day.dayNumber}</h3>
                <p><ReactBitsHoverText text={`${day.city} • ${day.theme}`} /></p>
              </div>
              <button
                disabled={loading}
                onClick={() => onRegenerateDay(day.dayNumber, [day.theme], trip.preferredTravelMode)}
              >
                Regenerate
              </button>
            </header>
            <p className="notes">{day.notes}</p>
            <ul>
              {day.places.map((place) => (
                <li key={`${day.dayNumber}-${place.placeId}`}>
                  <span>{place.arrivalTime} - {place.departureTime}</span>
                  <strong><ReactBitsHoverText text={place.name} /></strong>
                  <small>{place.category} • {place.travelMinutes} min transfer</small>
                </li>
              ))}
            </ul>
          </article>
        ))}
      </div>
    </div>
  );
}
