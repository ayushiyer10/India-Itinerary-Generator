export function AnimatedBackdrop() {
  return (
    <div aria-hidden className="bg-stage">
      <span className="bg-orb orb-a" />
      <span className="bg-orb orb-b" />
      <span className="bg-orb orb-c" />
      <span className="bg-grid" />
    </div>
  );
}
