// WSManager: connessione diretta a ESP (192.168.4.1:81)

const WSManager = (() => {
  let ws = null;
  let reconnectTimer = null;
  let backoff = 1000; // inizio 1s

  const HOST = "192.168.4.1";
  const PORT = 81;
  const URL = `ws://${HOST}:${PORT}/`;

  function connect() {
    log(`[WS] Connecting to ${URL}`);
    ws = new WebSocket(URL);

    ws.onopen = () => {
      log("[WS] Connected");
      setWsLed(true);
      backoff = 1000; // reset
    };

    ws.onclose = () => {
      log("[WS] Disconnected");
      setWsLed(false);
      scheduleReconnect();
    };

    ws.onerror = (err) => {
      log("[WS] Error: " + err.message);
      ws.close();
    };

    ws.onmessage = (ev) => {
      const line = ev.data.trim();
      if (line.startsWith("$AUTOPILOT")) {
        parseTelem(line);
      } else {
        log("[WS] RX: " + line);
      }
    };
  }

  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      backoff = Math.min(backoff * 2, 10000); // max 10s
      connect();
    }, backoff);
  }

  function send(msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(msg);
      log("[WS] TX: " + msg);
    } else {
      log("[WS] Not connected, drop: " + msg);
    }
  }

  return { connect, send };
})();

// avvia subito
WSManager.connect();
