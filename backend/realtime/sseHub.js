function createSseHub() {
  const clients = new Set();

  function send(res, summary) {
    res.write(`event: summary\ndata: ${JSON.stringify(summary)}\n\n`);
  }

  return {
    handleConnection(req, res, summary) {
      res.writeHead(200, {
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        Connection: "keep-alive",
      });

      clients.add(res);
      send(res, summary);
      req.on("close", () => clients.delete(res));
    },

    broadcast(summary) {
      for (const client of clients) {
        send(client, summary);
      }
    },
  };
}

module.exports = {
  createSseHub,
};
