import { Navigate } from "react-router-dom";
import { AuthPanel } from "./AuthPanel";
import { ReactBitsHoverText } from "./ReactBitsHoverText";

export function AuthPage({ user, onAuthSuccess }) {
  if (user) {
    return <Navigate to="/planner" replace />;
  }

  return (
    <div className="app-shell page-auth">
      <header className="hero-card hero-card-auth">
        <p className="hero-kicker">Trip Engine Access</p>
        <h1 className="hero-title"><ReactBitsHoverText text="India Itinerary Planner" /></h1>
        <p className="hero-sub">Sign in to unlock adaptive routes, budget intelligence, and cinematic day planning.</p>
      </header>

      <section className="auth-layout">
        <AuthPanel onAuthSuccess={onAuthSuccess} />

        <aside className="panel auth-showcase" aria-hidden>
          <p className="showcase-kicker">Why it hits different</p>
          <h2>Plan like a travel studio, not a spreadsheet.</h2>
          <ul className="showcase-list">
            <li>City-to-city itinerary arcs with pace-aware day structuring.</li>
            <li>Live route visualization and map-first decision flow.</li>
            <li>Budget-aware recommendations tuned for your travel mode.</li>
          </ul>
          <div className="showcase-tiles">
            <div className="showcase-tile">
              <span>Routes computed</span>
              <strong>Realtime</strong>
            </div>
            <div className="showcase-tile">
              <span>Planning depth</span>
              <strong>Multi-city</strong>
            </div>
            <div className="showcase-tile">
              <span>Experience style</span>
              <strong>Cinematic</strong>
            </div>
          </div>
        </aside>
      </section>
    </div>
  );
}
