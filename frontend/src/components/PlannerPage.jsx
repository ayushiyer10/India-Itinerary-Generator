import { useEffect, useMemo, useState } from "react";
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

  const plannerStats = useMemo(() => {
    const days = trip?.days?.length || 0;
    const places = trip?.days?.reduce((sum, day) => sum + (day.places?.length || 0), 0) || 0;
    const cities = trip
      ? new Set(trip.days.map((day) => day.city).filter(Boolean)).size
      : 0;
    const budgetLoad = trip?.budgetInr
      ? `${Math.min(100, Math.round((trip.days.reduce((sum, day) => sum + day.estimatedCostInr, 0) / trip.budgetInr) * 100))}%`
      : "--";

    return [
      { label: "Trip State", value: loading ? "Generating" : trip ? "Ready" : "Draft" },
      { label: "Cities", value: cities || "--" },
      { label: "Planned Stops", value: places || "--" },
      { label: "Budget Load", value: budgetLoad }
    ];
  }, [trip, loading]);

  useEffect(() => {
    anime.timeline({ easing: "easeOutExpo" })
      .add({
        targets: ".hero-card-planner",
        opacity: [0, 1],
        translateY: [26, 0],
        scale: [0.98, 1],
        duration: 560
      })
      .add({
        targets: ".planner-metric",
        opacity: [0, 1],
        translateY: [20, 0],
        duration: 460,
        delay: anime.stagger(80)
      }, "-=220")
      .add({
        targets: ".page-planner .panel",
        opacity: [0, 1],
        translateY: [30, 0],
        rotateX: [8, 0],
        duration: 520,
        delay: anime.stagger(120)
      }, "-=340");
  }, []);

  useEffect(() => {
    const page = document.querySelector(".page-planner");
    if (!page) return undefined;

    function onPointerMove(event) {
      const rect = page.getBoundingClientRect();
      const x = ((event.clientX - rect.left) / rect.width) * 100;
      const y = ((event.clientY - rect.top) / rect.height) * 100;
      page.style.setProperty("--mx", `${x.toFixed(2)}%`);
      page.style.setProperty("--my", `${y.toFixed(2)}%`);
    }

    page.addEventListener("pointermove", onPointerMove);
    return () => page.removeEventListener("pointermove", onPointerMove);
  }, []);

  useEffect(() => {
    if (!trip) return;
    anime({
      targets: ".timeline-shell .day-card",
      opacity: [0, 1],
      translateY: [34, 0],
      rotateY: [8, 0],
      scale: [0.96, 1],
      easing: "easeOutExpo",
      delay: anime.stagger(90),
      duration: 620
    });
  }, [trip]);

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

  async function onRegenerateDay(dayNumber, mode) {
    if (!trip) return;
    setLoading(true);
    setError("");
    try {
      await regenerateDay(trip.id, dayNumber, {
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
        <p className="hero-kicker">Plan. Glide. Explore.</p>
        <h1 className="hero-title"><ReactBitsHoverText text="India Itinerary Planner" /></h1>
        <p className="hero-sub">Cinematic travel planning with smart day routing across cities.</p>
        <div className="hero-meta" aria-hidden>
          <span className="meta-chip">Live Route Intelligence</span>
          <span className="meta-chip">Adaptive Budget Insights</span>
          <span className="meta-chip">Immersive Timeline Motion</span>
        </div>
        <div className="auth-badge">
          <span>
            Signed in as <ReactBitsHoverText text={user.name} className="rb-name" />
          </span>
          <button type="button" onClick={onLogout}>Logout</button>
        </div>
      </header>

      <section className="planner-dashboard" aria-label="Planner overview">
        {plannerStats.map((stat) => (
          <article key={stat.label} className="planner-metric">
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
          </article>
        ))}
      </section>

      <main className="grid planner-grid">
        <section className="panel panel-form panel-stack">
          <header className="panel-heading">
            <p>Trip Configuration</p>
            <h2>Build Your Itinerary</h2>
          </header>
          <PlannerForm loading={loading} onGenerate={onGenerate} userId={user.userId} />
          {error ? <p className="error-text">{error}</p> : null}
        </section>

        <section className="panel panel-map panel-stack">
          <header className="panel-heading">
            <p>Live Route Intelligence</p>
            <h2>Map + Budget Studio</h2>
          </header>
          <BudgetMeter trip={trip} />
          <TripMap trip={trip} />
          <MapTools />
        </section>
      </main>

      <section className="panel timeline-shell panel-stack">
        <header className="panel-heading timeline-head">
          <p>Execution Timeline</p>
          <h2>Daily Experience Flow</h2>
        </header>
        <TripTimeline trip={trip} loading={loading} onRegenerateDay={onRegenerateDay} />
      </section>
    </div>
  );
}
