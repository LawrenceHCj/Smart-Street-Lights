const fs = require("fs");
const path = require("path");
const { FRONTEND_DIR } = require("../config/paths");
const { sendJson } = require("../utils/http");

const CONTENT_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
};

function serveFrontend(res, pathname) {
  const requestPath = pathname === "/" ? "/index.html" : pathname;
  const filePath = path.normalize(path.join(FRONTEND_DIR, decodeURIComponent(requestPath)));
  if (!filePath.startsWith(FRONTEND_DIR)) return sendJson(res, 403, { error: "forbidden" });

  fs.readFile(filePath, (error, content) => {
    if (error) return sendJson(res, 404, { error: "not found" });
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { "Content-Type": CONTENT_TYPES[ext] || "application/octet-stream" });
    res.end(content);
  });
}

module.exports = {
  serveFrontend,
};
