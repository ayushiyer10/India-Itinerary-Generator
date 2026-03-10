import { ReactBitsHoverText } from "./ReactBitsHoverText";

export function BudgetMeter({ trip }) {
  if (!trip) {
    return <p className="placeholder"><ReactBitsHoverText text="Budget insights will appear after itinerary generation." /></p>;
  }

  const estimated = trip.days.reduce((sum, day) => sum + day.estimatedCostInr, 0);
  const percent = Math.min(100, Math.round((estimated / trip.budgetInr) * 100));

  return (
    <div className="budget-meter">
      <h2><ReactBitsHoverText text="Budget Meter" /></h2>
      <p>
        <ReactBitsHoverText text={`Estimated: INR ${estimated.toLocaleString("en-IN")} / Budget: INR ${trip.budgetInr.toLocaleString("en-IN")}`} />
      </p>
      <div className="meter-track">
        <div className="meter-fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
