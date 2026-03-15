import { useState } from "react";
import anime from "animejs/lib/anime.es.js";
import { login, signup } from "../api/client";
import { useEffect } from "react";

export function AuthPanel({ onAuthSuccess }) {
  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    anime({
      targets: ".auth-panel",
      opacity: [0, 1],
      scale: [0.98, 1],
      easing: "easeOutCubic",
      duration: 360
    });
  }, [mode]);

  async function submit(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const payload = { email, password };
      const user = mode === "signup"
        ? await signup({ ...payload, name })
        : await login(payload);
      onAuthSuccess(user);
    } catch (err) {
      setError(err?.response?.data?.error || "Authentication failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="panel auth-panel auth-panel-shell">
      <p className="auth-kicker">Secure Access</p>
      <h2 className="auth-title">{mode === "login" ? "Welcome Back" : "Create Your Account"}</h2>
      <p className="auth-copy">Sign in to generate and manage your itinerary.</p>
      <div className="auth-switch auth-switch-pill" role="tablist" aria-label="Authentication mode">
        <button
          type="button"
          className={mode === "login" ? "active" : ""}
          onClick={() => setMode("login")}
          role="tab"
          aria-selected={mode === "login"}
        >
          Login
        </button>
        <button
          type="button"
          className={mode === "signup" ? "active" : ""}
          onClick={() => setMode("signup")}
          role="tab"
          aria-selected={mode === "signup"}
        >
          Sign Up
        </button>
      </div>
      <form onSubmit={submit} className="auth-form">
        {mode === "signup" ? (
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
        ) : null}
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={6} required />
        </label>
        <button type="submit" disabled={loading}>
          {loading ? "Please wait..." : (mode === "login" ? "Login" : "Create Account")}
        </button>
      </form>
      {error ? <p className="error-text">{error}</p> : null}
    </section>
  );
}
