export async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || "Request failed");
  }
  return data;
}

export const api = {
  getSummary() {
    return request("/api/summary");
  },

  updateConfig(config) {
    return request("/api/config", {
      method: "PUT",
      body: JSON.stringify(config),
    });
  },

  updateDevice(deviceId, patch) {
    return request(`/api/devices/${encodeURIComponent(deviceId)}`, {
      method: "PATCH",
      body: JSON.stringify(patch),
    });
  },

  controlDevice(deviceId, action) {
    return request(`/api/devices/${encodeURIComponent(deviceId)}/control`, {
      method: "POST",
      body: JSON.stringify({ action }),
    });
  },

  setScenario(scenario) {
    return request("/api/simulator/scenario", {
      method: "POST",
      body: JSON.stringify({ scenario }),
    });
  },

  askAssistant(question) {
    return request("/api/assistant/chat", {
      method: "POST",
      body: JSON.stringify({ question }),
    });
  },
};
