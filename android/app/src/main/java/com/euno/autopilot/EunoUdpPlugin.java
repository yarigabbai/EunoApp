package com.euno.autopilot;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "EunoUdp")
public class EunoUdpPlugin extends Plugin {

  private DatagramSocket sock = null;
  private Thread rxThread = null;
  private Timer keepAliveTimer = null;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private String remoteIp = "";
  private int remotePort = 10110;

  // ====== DISCOVERY ======

  @PluginMethod
  public void discover(PluginCall call) {
    final int port = call.getInt("port", 10110);
    final int timeout = call.getInt("timeoutMs", 1500);

    new Thread(() -> {
      try {
        // 1) Tentativo diretto su AP 192.168.4.1
        Pair p = helloOnce("192.168.4.1", port, timeout);
        if (p != null) {
          JSObject ret = new JSObject();
          ret.put("ip", p.ip);
          ret.put("mode", p.mode);
          call.resolve(ret);
          return;
        }

        // 2) Broadcast
        p = helloBroadcast(port, timeout);
        if (p != null) {
          JSObject ret = new JSObject();
          ret.put("ip", p.ip);
          ret.put("mode", p.mode);
          call.resolve(ret);
          return;
        }

        // 3) (Opz.) NSD/mDNS non implementato qui
        call.reject("not_found");
      } catch (Exception e) {
        call.reject("error: " + e.getMessage());
      }
    }).start();
  }

  private static class Pair {
    String ip;
    String mode;
    Pair(String ip, String mode) { this.ip = ip; this.mode = mode; }
  }

  private Pair parseHelloReply(String s) {
    if (s == null) return null;
    s = s.trim();
    if (!s.startsWith("$EUNO,HELLO,")) return null;
    // atteso: $EUNO,HELLO,MODE=STA,IP=192.168.x.y
    String[] parts = s.split(",", 4);
    if (parts.length < 4) return null;
    String mode = parts[2].replace("MODE=", "");
    String ip   = parts[3].replace("IP=", "");
    if (ip.isEmpty()) return null;
    if (mode.isEmpty()) mode = "?";
    return new Pair(ip, mode);
  }

  private Pair helloOnce(String targetIp, int port, int timeoutMs) {
    DatagramSocket s = null;
    try {
      s = new DatagramSocket();
      s.setSoTimeout(timeoutMs);
      byte[] data = "$PEUNO,HELLO,APP".getBytes();
      DatagramPacket pkt = new DatagramPacket(data, data.length, InetAddress.getByName(targetIp), port);
      s.send(pkt);

      byte[] buf = new byte[512];
      DatagramPacket resp = new DatagramPacket(buf, buf.length);
      s.receive(resp);
      String line = new String(resp.getData(), 0, resp.getLength()).trim();
      return parseHelloReply(line);
    } catch (SocketTimeoutException ignored) {
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      if (s != null) try { s.close(); } catch (Exception ignored) {}
    }
  }

  private Pair helloBroadcast(int port, int timeoutMs) {
    DatagramSocket s = null;
    try {
      s = new DatagramSocket();
      s.setBroadcast(true);
      s.setSoTimeout(timeoutMs);
      byte[] data = "$PEUNO,HELLO,APP".getBytes();
      DatagramPacket pkt = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), port);
      s.send(pkt);

      byte[] buf = new byte[512];
      DatagramPacket resp = new DatagramPacket(buf, buf.length);
      s.receive(resp);
      String line = new String(resp.getData(), 0, resp.getLength()).trim();
      return parseHelloReply(line);
    } catch (SocketTimeoutException ignored) {
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      if (s != null) try { s.close(); } catch (Exception ignored) {}
    }
  }

  // ====== CONNECT / RX / TX ======

  @PluginMethod
  public void connect(PluginCall call) {
    String ip = call.getString("ip");
    int port = call.getInt("port", 10110);
    if (ip == null || ip.trim().isEmpty()) {
      call.reject("missing ip");
      return;
    }
    closeInternal(); // chiudi eventuale precedente

    try {
      sock = new DatagramSocket();
      sock.setSoTimeout(1000);
      remoteIp = ip.trim();
      remotePort = port;
      running.set(true);

      // RX loop
      rxThread = new Thread(() -> {
        byte[] buf = new byte[1500];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        while (running.get()) {
          try {
            sock.receive(pkt);
            String line = new String(pkt.getData(), 0, pkt.getLength()).trim();
            JSObject ev = new JSObject();
            ev.put("line", line);
            notifyListeners("line", ev);
          } catch (SocketTimeoutException ignored) {
            // idle tick
          } catch (Exception e) {
            if (running.get()) Log.e("EunoUdp", "RX error", e);
          }
        }
      });
      rxThread.start();

      // Keep-alive ogni 7s
      keepAliveTimer = new Timer();
      keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
        @Override public void run() {
          try { sendRaw("$PEUNO,PING"); } catch (Exception ignored) {}
        }
      }, 7000, 7000);

      JSObject ret = new JSObject();
      ret.put("connected", true);
      ret.put("ip", remoteIp);
      ret.put("port", remotePort);
      call.resolve(ret);

    } catch (Exception e) {
      call.reject("connect_failed: " + e.getMessage());
    }
  }

  @PluginMethod
  public void send(PluginCall call) {
    String line = call.getString("line");
    if (line == null || line.trim().isEmpty()) {
      call.reject("empty");
      return;
    }
    try {
      sendRaw(line);
      call.resolve();
    } catch (Exception e) {
      call.reject("send_failed: " + e.getMessage());
    }
  }

  private void sendRaw(String s) throws Exception {
    if (sock == null) throw new Exception("not_connected");
    byte[] data = s.getBytes();
    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(remoteIp), remotePort);
    sock.send(packet);
  }

  @PluginMethod
  public void close(PluginCall call) {
    closeInternal();
    call.resolve();
  }

  private void closeInternal() {
    running.set(false);
    if (keepAliveTimer != null) {
      try { keepAliveTimer.cancel(); } catch (Exception ignored) {}
      keepAliveTimer = null;
    }
    if (rxThread != null) {
      try { rxThread.interrupt(); } catch (Exception ignored) {}
      rxThread = null;
    }
    if (sock != null) {
      try { sock.close(); } catch (Exception ignored) {}
      sock = null;
    }
  }
}
