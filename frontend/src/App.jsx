import { useEffect, useMemo, useState } from "react";
import anime from "animejs/lib/anime.es.js";
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { AuthPage } from "./components/AuthPage";
import { PlannerPage } from "./components/PlannerPage";
import { SplashScreen } from "./components/SplashScreen";
import { AnimatedBackdrop } from "./components/AnimatedBackdrop";

const AUTH_KEY = "india_itinerary_user";

function AppRoutes() {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem(AUTH_KEY);
    return saved ? JSON.parse(saved) : null;
  });

  useEffect(() => {
    anime({
      targets: ".route-shell .hero-card, .route-shell .panel",
      opacity: [0, 1],
      translateY: [16, 0],
      easing: "easeOutQuad",
      delay: anime.stagger(80),
      duration: 420
    });
  }, [location.pathname]);

  const actions = useMemo(() => ({
    onAuthSuccess(authUser) {
      setUser(authUser);
      localStorage.setItem(AUTH_KEY, JSON.stringify(authUser));
      navigate("/planner", { replace: true });
    },
    onLogout() {
      setUser(null);
      localStorage.removeItem(AUTH_KEY);
      navigate("/auth", { replace: true });
    }
  }), [navigate]);

  return (
    <Routes>
      <Route path="/" element={<Navigate to={user ? "/planner" : "/auth"} replace />} />
      <Route path="/auth" element={<AuthPage user={user} onAuthSuccess={actions.onAuthSuccess} />} />
      <Route
        path="/planner"
        element={user ? <PlannerPage user={user} onLogout={actions.onLogout} /> : <Navigate to="/auth" replace />}
      />
      <Route path="*" element={<Navigate to={user ? "/planner" : "/auth"} replace />} />
    </Routes>
  );
}

export default function App() {
  const [showSplash, setShowSplash] = useState(true);

  if (showSplash) {
    return <SplashScreen onDone={() => setShowSplash(false)} />;
  }

  return (
    <HashRouter>
      <AnimatedBackdrop />
      <div className="route-shell">
        <AppRoutes />
      </div>
    </HashRouter>
  );
}
