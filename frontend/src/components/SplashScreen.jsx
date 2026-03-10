import { useEffect, useRef } from "react";
import anime from "animejs/lib/anime.es.js";

export function SplashScreen({ onDone }) {
  const doneRef = useRef(false);
  const title = "India AI Itinerary Planner";
  const subtitle = "Smart routes across India, generated in seconds.";

  function renderChars(text, cls) {
    return [...text].map((ch, idx) => (
      <span key={`${cls}-${idx}`} className={cls}>
        {ch === " " ? "\u00A0" : ch}
      </span>
    ));
  }

  useEffect(() => {
    const timer = setTimeout(() => {
      if (!doneRef.current) {
        doneRef.current = true;
        anime({
          targets: ".splash-root",
          opacity: [1, 0],
          scale: [1, 1.02],
          duration: 780,
          easing: "easeInOutQuad",
          complete: onDone
        });
      }
    }, 3600);

    anime.timeline({ easing: "easeOutExpo" })
      .add({
        targets: ".splash-char",
        opacity: [0, 1],
        translateY: [36, 0],
        rotateX: [35, 0],
        delay: anime.stagger(24),
        duration: 620
      })
      .add({
        targets: ".splash-sub-char",
        opacity: [0, 1],
        translateY: [18, 0],
        delay: anime.stagger(10),
        duration: 520
      })
      .add({
        targets: ".splash-ring",
        scale: [0.6, 1.05],
        opacity: [0.2, 1],
        duration: 950
      }, "-=460")
      .add({
        targets: ".splash-progress-fill",
        width: ["0%", "100%"],
        duration: 1500,
        easing: "linear"
      }, "-=700");

    anime({
      targets: ".splash-title",
      textShadow: [
        "0 0 0 rgba(0,0,0,0)",
        "0 0 18px rgba(0, 162, 255, 0.35)",
        "0 0 0 rgba(0,0,0,0)"
      ],
      duration: 1800,
      easing: "easeInOutSine",
      loop: true
    });

    return () => clearTimeout(timer);
  }, [onDone]);

  function skip() {
    if (!doneRef.current) {
      doneRef.current = true;
      anime({
        targets: ".splash-root",
        opacity: [1, 0],
        duration: 420,
        easing: "easeInOutQuad",
        complete: onDone
      });
    }
  }

  return (
    <div className="splash-root">
      <div className="splash-ring" />
      <div className="splash-card">
        <div style={{ display: "grid", gap: 6 }}>
          <h1 style={{ margin: 0 }} className="splash-title">{renderChars(title, "splash-char")}</h1>
          <p style={{ margin: 0 }} className="splash-sub">{renderChars(subtitle, "splash-sub-char")}</p>
        </div>
        <div className="splash-progress">
          <div className="splash-progress-fill" />
        </div>
      </div>
    </div>
  );
}
