const fs = require("fs");
const { DATA_DIR, STATE_FILE } = require("../config/paths");
const { createInitialState } = require("./initialState");
const { buildSummary } = require("../services/summaryService");

function createStateStore() {
  let state = loadState();
  let saveTimer = null;

  function ensureDataDir() {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }

  function scheduleSave() {
    if (saveTimer) return;

    saveTimer = setTimeout(() => {
      saveTimer = null;
      ensureDataDir();
      fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2), "utf8");
    }, 250);
  }

  return {
    getState() {
      return state;
    },

    getSummary() {
      return buildSummary(state);
    },

    scheduleSave,
    ensureDataDir,
  };
}

function loadState() {
  try {
    if (!fs.existsSync(STATE_FILE)) return createInitialState();
    const parsed = JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
    return { ...createInitialState(), ...parsed };
  } catch (error) {
    console.warn("Failed to load state, using defaults:", error.message);
    return createInitialState();
  }
}

module.exports = {
  createStateStore,
};
