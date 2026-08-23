const path = require("path");

const ROOT_DIR = path.resolve(__dirname, "..", "..");
const FRONTEND_DIR = path.join(ROOT_DIR, "frontend");
const DATA_DIR = path.join(ROOT_DIR, "data");
const STATE_FILE = path.join(DATA_DIR, "app-state.json");
const PORT = Number(process.env.PORT || 8080);

module.exports = {
  ROOT_DIR,
  FRONTEND_DIR,
  DATA_DIR,
  STATE_FILE,
  PORT,
};
