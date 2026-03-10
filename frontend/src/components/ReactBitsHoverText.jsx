import { useMemo, useRef } from "react";
import anime from "animejs/lib/anime.es.js";

export function ReactBitsHoverText({ text, as = "span", className = "" }) {
  const rootRef = useRef(null);
  const chars = useMemo(() => [...String(text || "")], [text]);
  const Tag = as;

  function animateIn() {
    if (!rootRef.current) return;
    const targets = rootRef.current.querySelectorAll(".rb-char");
    anime.remove(targets);
    anime({
      targets,
      translateY: [0, -6],
      color: ["#0b2139", "#00a7ff"],
      textShadow: ["0 0 0 rgba(0,0,0,0)", "0 0 10px rgba(0, 174, 255, 0.55)"],
      delay: anime.stagger(18),
      duration: 280,
      easing: "easeOutQuad"
    });
  }

  function animateOut() {
    if (!rootRef.current) return;
    const targets = rootRef.current.querySelectorAll(".rb-char");
    anime.remove(targets);
    anime({
      targets,
      translateY: 0,
      color: "#0b2139",
      textShadow: "0 0 0 rgba(0,0,0,0)",
      delay: anime.stagger(10),
      duration: 220,
      easing: "easeOutQuad"
    });
  }

  return (
    <Tag
      ref={rootRef}
      className={`rb-hover-text ${className}`.trim()}
      aria-label={text}
      onMouseEnter={animateIn}
      onMouseLeave={animateOut}
    >
      {chars.map((ch, idx) => (
        <span className="rb-char" key={`${ch}-${idx}`}>
          {ch === " " ? "\u00A0" : ch}
        </span>
      ))}
    </Tag>
  );
}
