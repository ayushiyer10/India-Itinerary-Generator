import { useEffect, useMemo, useRef, useState } from "react";
import anime from "animejs/lib/anime.es.js";

export function SplashScreen({ onDone }) {
  const doneRef = useRef(false);
  const rafRef = useRef(0);
  const timeoutRef = useRef(0);
  const [progress, setProgress] = useState(0);
  const title = "India Itinerary Planner";
  const subtitle = "Cinematic route intelligence for your next India adventure.";
  const stars = useMemo(
    () => Array.from({ length: 28 }, (_, idx) => ({
      id: idx,
      left: `${Math.round(Math.random() * 100)}%`,
      top: `${Math.round(Math.random() * 100)}%`,
      delay: `${Math.round(Math.random() * 2300)}ms`,
      duration: `${3200 + Math.round(Math.random() * 3800)}ms`
    })),
    []
  );

  function renderChars(text, cls) {
    return [...text].map((ch, idx) => (
      <span key={`${cls}-${idx}`} className={cls}>
        {ch === " " ? "\u00A0" : ch}
      </span>
    ));
  }

  useEffect(() => {
    const loadDuration = 4200;
    const startedAt = performance.now();

    const progressTick = () => {
      const elapsed = performance.now() - startedAt;
      const next = Math.min(100, Math.round((elapsed / loadDuration) * 100));
      setProgress(next);
      if (next < 100) {
        rafRef.current = requestAnimationFrame(progressTick);
      }
    };

    rafRef.current = requestAnimationFrame(progressTick);

    function finishSplash() {
      if (doneRef.current) return;
      doneRef.current = true;
      anime({
        targets: ".splash-root",
        opacity: [1, 0],
        scale: [1, 1.02],
        duration: 720,
        easing: "easeInOutQuad",
        complete: onDone
      });
    }

    timeoutRef.current = setTimeout(finishSplash, 4700);

    const revealTimeline = anime.timeline({ easing: "easeOutExpo" })
      .add({
        targets: ".splash-card",
        opacity: [0, 1],
        translateY: [28, 0],
        scale: [0.96, 1],
        duration: 700
      })
      .add({
        targets: ".splash-kicker, .splash-title-wrap",
        opacity: [0, 1],
        translateY: [14, 0],
        duration: 420,
        delay: anime.stagger(120)
      }, "-=500")
      .add({
        targets: ".splash-char",
        opacity: [0, 1],
        translateY: [30, 0],
        rotateX: [35, 0],
        delay: anime.stagger(20),
        duration: 560
      })
      .add({
        targets: ".splash-sub-char",
        opacity: [0, 1],
        translateY: [14, 0],
        delay: anime.stagger(8),
        duration: 460
      })
      .add({
        targets: ".splash-stat",
        opacity: [0, 1],
        translateY: [16, 0],
        delay: anime.stagger(110),
        duration: 460
      }, "-=260")
      .add({
        targets: ".splash-progress-fill",
        width: ["0%", "100%"],
        duration: loadDuration,
        easing: "linear"
      }, "-=280");

    const titlePulse = anime({
      targets: ".splash-title",
      textShadow: [
        "0 0 0 rgba(0,0,0,0)",
        "0 0 24px rgba(0, 181, 255, 0.5)",
        "0 0 0 rgba(0,0,0,0)"
      ],
      duration: 2100,
      easing: "easeInOutSine",
      loop: true
    });

    const ringSpin = anime({
      targets: ".splash-ring",
      rotate: [0, 360],
      duration: 15000,
      easing: "linear",
      loop: true
    });

    const auroraFlow = anime({
      targets: ".splash-aurora",
      translateX: [0, 42, -24, 0],
      translateY: [0, -20, 12, 0],
      scale: [1, 1.08, 0.97, 1],
      easing: "easeInOutSine",
      duration: 11000,
      delay: anime.stagger(260),
      loop: true
    });

    return () => {
      clearTimeout(timeoutRef.current);
      cancelAnimationFrame(rafRef.current);
      revealTimeline.pause();
      titlePulse.pause();
      ringSpin.pause();
      auroraFlow.pause();
    };
  }, [onDone]);

  return (
    <div className="splash-root">
      <div className="splash-noise" />
      <div className="splash-aurora aurora-a" />
      <div className="splash-aurora aurora-b" />
      <div className="splash-aurora aurora-c" />
      <div className="splash-stars" aria-hidden>
        {stars.map((star) => (
          <span
            key={star.id}
            className="splash-star"
            style={{
              left: star.left,
              top: star.top,
              animationDelay: star.delay,
              animationDuration: star.duration
            }}
          />
        ))}
      </div>
      <div className="splash-ring ring-outer" />
      <div className="splash-ring ring-inner" />
      <div className="splash-card">
        <p className="splash-kicker">India Travel Intelligence</p>
        <div className="splash-title-wrap">
          <h1 style={{ margin: 0 }} className="splash-title">{renderChars(title, "splash-char")}</h1>
          <p style={{ margin: 0 }} className="splash-sub">{renderChars(subtitle, "splash-sub-char")}</p>
        </div>

        <div className="splash-progress">
          <div className="splash-progress-fill" />
        </div>
        <div className="splash-load-row">
          <span>Booting itinerary engine</span>
          <strong>{progress}%</strong>
        </div>
      </div>
    </div>
  );
}
