import { useEffect, useState } from "react";
import anime from "animejs/lib/anime.es.js";
import { generateTrip, getTrip, regenerateDay } from "../api/client";
import { PlannerForm } from "./PlannerForm";
import { TripTimeline } from "./TripTimeline";
import { TripMap } from "./TripMap";
import { BudgetMeter } from "./BudgetMeter";
import { MapTools } from "./MapTools";
import { ReactBitsHoverText } from "./ReactBitsHoverText";

export function PlannerPage({ user, onLogout }) {
  const [loading, setLoading] = useState(false);
  const [trip, setTrip] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    anime({
      targets: ".hero-card, .panel, .day-card",
      opacity: [0, 1],
      translateY: [24, 0],
      easing: "easeOutExpo",
      delay: anime.stagger(120)
    });
  }, []);

  async function onGenerate(formPayload) {
    setLoading(true);
    setError("");
    try {
      const create = await generateTrip(formPayload);
      const fullTrip = await getTrip(create.tripId);
      setTrip(fullTrip);
    } catch (err) {
      if (err?.code === "ECONNABORTED") {
        setError("Trip generation timed out. Please try fewer cities or a shorter date range.");
      } else if (!err?.response) {
        setError(`Network/server error: ${err?.message || "Unable to reach backend"}`);
      } else {
        setError(err?.response?.data?.error || "Failed to generate trip");
      }
    } finally {
      setLoading(false);
    }
  }

  async function onRegenerateDay(dayNumber, interests, mode) {
    if (!trip) return;
    setLoading(true);
    setError("");
    try {
      await regenerateDay(trip.id, dayNumber, {
        interests,
        preferredTravelMode: mode
      });
      const updated = await getTrip(trip.id);
      setTrip(updated);
    } catch (err) {
      setError(err?.response?.data?.error || "Failed to regenerate day");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-shell page-planner">
      <header className="hero-card hero-card-planner">
        <h1><ReactBitsHoverText text="India AI Itinerary Planner" /></h1>
        <div className="auth-badge">
          <span>
            Signed in as <ReactBitsHoverText text={user.name} className="rb-name" />
          </span>
          <button type="button" onClick={onLogout}>Logout</button>
        </div>
      </header>

      <main className="grid">
        <section className="panel">
          <PlannerForm loading={loading} onGenerate={onGenerate} userId={user.userId} />
          {error ? <p className="error-text">{error}</p> : null}
        </section>

        <section className="panel">
          <BudgetMeter trip={trip} />
          <TripMap trip={trip} />
          <MapTools />
        </section>
      </main>

      <section className="panel">
        <TripTimeline trip={trip} loading={loading} onRegenerateDay={onRegenerateDay} />
      </section>
    </div>
  );
}
