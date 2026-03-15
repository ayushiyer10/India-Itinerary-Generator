import { useState } from "react";

const DEFAULT_PAYLOAD = {
  title: "",
  startDate: "",
  endDate: "",
  cities: "",
  budgetInr: "",
  pace: "balanced",
  preferredTravelMode: "cab"
};

export function PlannerForm({ loading, onGenerate, userId }) {
  const [form, setForm] = useState(DEFAULT_PAYLOAD);

  function parsePromptList(value, options = {}) {
    const { allowDotSeparator = false } = options;
    let normalized = value.replace(/\r/g, "\n");
    if (allowDotSeparator) {
      normalized = normalized.replace(/\s+\.\s+/g, ",");
    }
    return normalized
      .split(/[,\n;|]+/)
      .map((item) => item.trim().replace(/\.$/, ""))
      .filter(Boolean);
  }

  function submit(event) {
    event.preventDefault();
    onGenerate({
      ...form,
      userId,
      cities: parsePromptList(form.cities, { allowDotSeparator: true }),
      budgetInr: Number(form.budgetInr)
    });
  }

  function setField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <form onSubmit={submit} className="trip-form">
      <label className="full-row">
        Trip Name
        <input
          value={form.title}
          onChange={(e) => setField("title", e.target.value)}
          placeholder="e.g. South India Discovery"
          required
        />
      </label>
      <label>
        Start Date
        <input type="date" value={form.startDate} onChange={(e) => setField("startDate", e.target.value)} required />
      </label>
      <label>
        End Date
        <input type="date" value={form.endDate} onChange={(e) => setField("endDate", e.target.value)} required />
      </label>
      <label className="full-row">
        City Prompt (comma separated)
        <input
          value={form.cities}
          onChange={(e) => setField("cities", e.target.value)}
          placeholder="e.g. Kochi, Munnar, Madurai"
          required
        />
      </label>
      <label>
        Budget (INR)
        <input
          type="number"
          min="1000"
          value={form.budgetInr}
          onChange={(e) => setField("budgetInr", e.target.value)}
          placeholder="e.g. 45000"
          required
        />
      </label>
      <label className="dropdown-row">
        Pace
        <select value={form.pace} onChange={(e) => setField("pace", e.target.value)}>
          <option value="relaxed">Relaxed</option>
          <option value="balanced">Balanced</option>
          <option value="fast">Fast</option>
        </select>
      </label>
      <label className="dropdown-row full-row">
        Preferred Travel Mode
        <select value={form.preferredTravelMode} onChange={(e) => setField("preferredTravelMode", e.target.value)}>
          <option value="cab">Cab</option>
          <option value="metro">Metro</option>
          <option value="train">Train</option>
          <option value="flight">Flight</option>
        </select>
      </label>
      <button className="submit-row" disabled={loading} type="submit">
        {loading ? "Generating..." : "Generate Itinerary"}
      </button>
    </form>
  );
}
