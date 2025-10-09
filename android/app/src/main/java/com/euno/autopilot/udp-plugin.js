<script>
  const EunoUDP = (() => {
    const cap = (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.EunoUdp) || null;
    if (!cap) {
      console.warn("EunoUdp plugin non disponibile (sei nel browser?).");
      return {
        discover: async () => { throw new Error("Plugin non disponibile"); },
        connect:  async () => { throw new Error("Plugin non disponibile"); },
        send:     async () => { throw new Error("Plugin non disponibile"); },
        onLine:   () => {}
      };
    }
    let onLineCb = null;
    cap.addListener("line", (ev) => {
      if (onLineCb && ev && ev.line) onLineCb(ev.line);
      if (typeof log === 'function') log("[UDP] RX: " + ev.line);
    });
    return {
      discover: async (port=10110, timeoutMs=1500) => {
        try { return await cap.discover({ port, timeoutMs }); } catch(e){ return null; }
      },
      connect: async (ip, port=10110) => cap.connect({ ip, port }),
      send:    async (line) => cap.send({ line }),
      onLine:  (cb) => { onLineCb = cb; }
    };
  })();
  window.EunoUDP = EunoUDP;
</script>
