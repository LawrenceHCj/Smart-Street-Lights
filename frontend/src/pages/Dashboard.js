import { api } from "../api/client.js";
import { Header } from "../components/Header.js";
import { MetricCard } from "../components/MetricCard.js";

let currentSummary = null;

export function createDashboard(root) {
  root.innerHTML = `
    ${Header()}
    <main class="page">
      <section class="metric-grid">
        ${MetricCard("latest-lux", "当前光照", "lux")}
        ${MetricCard("online-devices", "在线设备", "台")}
        ${MetricCard("lamps-on", "亮灯数量", "盏")}
        ${MetricCard("active-alerts", "活动告警", "条")}
      </section>

      <section class="workspace">
        <section class="panel">
          <div class="panel-head">
            <div>
              <h2>设备列表</h2>
              <p>查看在线状态、灯状态和绑定信息</p>
            </div>
            <div class="scenario-actions">
              <button data-scenario="normal">正常</button>
              <button data-scenario="low-light">低光</button>
              <button data-scenario="daylight">高光</button>
              <button data-scenario="outage">离线</button>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>设备</th>
                  <th>位置</th>
                  <th>光照</th>
                  <th>在线</th>
                  <th>路灯</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody id="device-table"></tbody>
            </table>
          </div>
        </section>

        <aside class="side-stack">
          <section class="panel">
            <h2>控制参数</h2>
            <form id="config-form" class="form-stack">
              <label>
                <span>自动控制</span>
                <input id="auto-control" type="checkbox" />
              </label>
              <label>
                <span>开灯阈值</span>
                <input id="lux-threshold" type="number" min="10" max="500" />
              </label>
              <label>
                <span>关灯滞回</span>
                <input id="hysteresis" type="number" min="0" max="200" />
              </label>
              <button type="submit">保存</button>
            </form>
          </section>

          <section class="panel">
            <h2>告警日志</h2>
            <div id="alert-list" class="list"></div>
          </section>

          <section class="panel">
            <h2>维护问答</h2>
            <form id="chat-form" class="form-stack">
              <textarea id="question" rows="3" placeholder="输入维护问题"></textarea>
              <button type="submit">提问</button>
            </form>
            <div id="assistant-answer" class="answer"></div>
          </section>
        </aside>
      </section>
    </main>
  `;

  bindEvents();
  initData();
  initRealtime();
}

async function initData() {
  render(await api.getSummary());
}

function initRealtime() {
  const events = new EventSource("/events");
  events.addEventListener("open", () => setConnection(true));
  events.addEventListener("error", () => setConnection(false));
  events.addEventListener("summary", (event) => {
    render(JSON.parse(event.data));
  });
}

function bindEvents() {
  document.querySelector("#config-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await api.updateConfig({
      autoControl: document.querySelector("#auto-control").checked,
      luxThreshold: Number(document.querySelector("#lux-threshold").value),
      hysteresis: Number(document.querySelector("#hysteresis").value),
      heartbeatTimeoutMs: currentSummary?.config.heartbeatTimeoutMs || 15000,
    });
  });

  document.querySelector("#chat-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const question = document.querySelector("#question").value.trim();
    if (!question) return;
    const answer = document.querySelector("#assistant-answer");
    answer.textContent = "检索中...";
    const result = await api.askAssistant(question);
    answer.innerHTML = `<strong>回答</strong><p>${result.answer}</p><small>来源：${result.sources.join("、")}</small>`;
  });

  document.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;

    if (target.dataset.scenario) {
      await api.setScenario(target.dataset.scenario);
    }

    if (target.dataset.controlDevice) {
      await api.controlDevice(target.dataset.controlDevice, target.dataset.action);
    }

    if (target.dataset.toggleBind) {
      await api.updateDevice(target.dataset.toggleBind, {
        bound: target.dataset.bound === "true",
      });
    }
  });
}

function render(summary) {
  currentSummary = summary;
  renderMetrics(summary);
  renderConfig(summary);
  renderDevices(summary);
  renderAlerts(summary);
}

function renderMetrics(summary) {
  document.querySelector("#latest-lux").textContent = summary.metrics.latestLux ?? "--";
  document.querySelector("#online-devices").textContent = `${summary.metrics.onlineDevices}/${summary.metrics.totalDevices}`;
  document.querySelector("#lamps-on").textContent = summary.metrics.lampsOn;
  document.querySelector("#active-alerts").textContent = summary.metrics.activeAlerts;
  document.querySelector("#last-updated").textContent = new Date(summary.timestamp).toLocaleTimeString();
}

function renderConfig(summary) {
  document.querySelector("#auto-control").checked = summary.config.autoControl;
  document.querySelector("#lux-threshold").value = summary.config.luxThreshold;
  document.querySelector("#hysteresis").value = summary.config.hysteresis;
}

function renderDevices(summary) {
  const tbody = document.querySelector("#device-table");
  tbody.innerHTML = summary.devices
    .map(
      (device) => `
        <tr>
          <td><strong>${device.name}</strong><br><span class="muted">${device.id}</span></td>
          <td>${device.location}</td>
          <td>${device.lastLux ?? "--"} lux</td>
          <td><span class="status ${device.online ? "ok" : "bad"}">${device.online ? "在线" : "离线"}</span></td>
          <td><span class="status ${device.lampStatus === "ON" ? "ok" : ""}">${device.lampStatus === "ON" ? "开灯" : "关灯"}</span></td>
          <td class="row-actions">
            <button data-control-device="${device.id}" data-action="ON">开灯</button>
            <button data-control-device="${device.id}" data-action="OFF" class="secondary">关灯</button>
            <button data-toggle-bind="${device.id}" data-bound="${device.bound ? "false" : "true"}" class="secondary">
              ${device.bound ? "解绑" : "绑定"}
            </button>
          </td>
        </tr>
      `,
    )
    .join("");
}

function renderAlerts(summary) {
  const list = document.querySelector("#alert-list");
  if (!summary.alerts.length) {
    list.innerHTML = `<p class="muted">暂无告警</p>`;
    return;
  }

  list.innerHTML = summary.alerts
    .map(
      (alert) => `
        <article class="list-item ${alert.status === "ACTIVE" ? "danger" : ""}">
          <strong>${alert.type} · ${alert.deviceId}</strong>
          <p>${alert.message}</p>
          <span class="muted">${alert.status}</span>
        </article>
      `,
    )
    .join("");
}

function setConnection(connected) {
  const el = document.querySelector("#connection-state");
  el.textContent = connected ? "已连接" : "已断开";
  el.classList.toggle("ok", connected);
}
