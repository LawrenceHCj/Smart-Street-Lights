const http = require("http");
const { PORT } = require("./config/paths");
const { createStateStore } = require("./store/stateStore");
const { createSseHub } = require("./realtime/sseHub");
const { createApiRouter } = require("./routes/apiRoutes");
const { serveFrontend } = require("./routes/staticRoutes");
const { startDeviceSimulator } = require("./simulator/deviceSimulator");

const store = createStateStore();
const sseHub = createSseHub();
const apiRouter = createApiRouter({ store, sseHub });

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

    if (url.pathname === "/events") {
      return sseHub.handleConnection(req, res, store.getSummary());
    }

    if (url.pathname.startsWith("/api/")) {
      return apiRouter.handle(req, res, url);
    }

    return serveFrontend(res, url.pathname);
  } catch (error) {
    return apiRouter.sendError(res, 500, error.message);
  }
});

startDeviceSimulator({ store, sseHub });

server.listen(PORT, () => {
  store.ensureDataDir();
  console.log(`Smart Street Lights framework is running at http://localhost:${PORT}`);
});
