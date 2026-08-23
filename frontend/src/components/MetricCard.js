export function MetricCard(id, label, unit = "") {
  return `
    <article class="metric-card">
      <span>${label}</span>
      <strong id="${id}">--</strong>
      <small>${unit}</small>
    </article>
  `;
}
