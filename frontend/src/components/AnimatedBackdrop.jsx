import { useEffect, useMemo } from "react";
import anime from "animejs/lib/anime.es.js";

export function AnimatedBackdrop() {
  const particles = useMemo(
    () => Array.from({ length: 24 }, (_, idx) => ({
      id: idx,
      left: `${Math.round(Math.random() * 100)}%`,
      top: `${Math.round(Math.random() * 100)}%`,
      delay: `${Math.round(Math.random() * 1900)}ms`,
      duration: `${4200 + Math.round(Math.random() * 3500)}ms`
    })),
    []
  );

  useEffect(() => {
    anime({
      targets: ".bg-beam",
      opacity: [0.24, 0.62, 0.28],
      scale: [0.9, 1.05, 0.94],
      translateY: [0, -22, 0],
      easing: "easeInOutSine",
      delay: anime.stagger(230),
      duration: 6400,
      loop: true
    });

    anime({
      targets: ".bg-orb",
      translateX: [0, 24, -14, 0],
      translateY: [0, -20, 14, 0],
      scale: [1, 1.08, 0.96, 1],
      easing: "easeInOutSine",
      duration: 12000,
      delay: anime.stagger(300),
      loop: true
    });

    anime({
      targets: ".bg-ribbon",
      rotate: [0, 360],
      opacity: [0.16, 0.4, 0.16],
      easing: "linear",
      duration: 22000,
      delay: anime.stagger(400),
      loop: true
    });
  }, []);

  return (
    <div aria-hidden className="bg-stage">
      <span className="bg-noise" />
      <span className="bg-vignette" />
      <span className="bg-ribbon ribbon-a" />
      <span className="bg-ribbon ribbon-b" />
      <span className="bg-beam beam-a" />
      <span className="bg-beam beam-b" />
      <span className="bg-beam beam-c" />
      <span className="bg-orb orb-a" />
      <span className="bg-orb orb-b" />
      <span className="bg-orb orb-c" />
      <span className="bg-grid" />
      <div className="bg-particles">
        {particles.map((particle) => (
          <span
            key={particle.id}
            className="bg-particle"
            style={{
              left: particle.left,
              top: particle.top,
              animationDelay: particle.delay,
              animationDuration: particle.duration
            }}
          />
        ))}
      </div>
    </div>
  );
}
