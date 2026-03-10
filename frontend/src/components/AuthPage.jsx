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
        <h1><ReactBitsHoverText text="India AI Itinerary Planner" /></h1>
        <p>Create an account or log in to start building your itinerary.</p>
      </header>
      <AuthPanel onAuthSuccess={onAuthSuccess} />
    </div>
  );
}
