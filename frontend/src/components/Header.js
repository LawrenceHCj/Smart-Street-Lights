export function Header() {
  return `
    <header class="app-header">
      <div>
        <div class="eyebrow">Smart Street Lights</div>
        <h1>智慧路灯系统</h1>
      </div>
      <div class="header-status">
        <span id="connection-state" class="badge">连接中</span>
        <span id="last-updated" class="muted">--</span>
      </div>
    </header>
  `;
}
