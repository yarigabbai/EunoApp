package com.euno.autopilot.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Mantiene una rete CELLULARE parallela mentre il telefono è connesso al Wi-Fi dell'ESP. */
public class CellularBinder {
  private final ConnectivityManager cm;
  private ConnectivityManager.NetworkCallback cb;
  private volatile Network cellular;

  public CellularBinder(Context ctx) {
    cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  public synchronized void acquire() {
    if (cellular != null) return;
    NetworkRequest req = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build();

    cb = new ConnectivityManager.NetworkCallback() {
      @Override public void onAvailable(Network network) { cellular = network; }
      @Override public void onLost(Network network) { if (cellular == network) cellular = null; }
    };
    cm.requestNetwork(req, cb);
  }

  public synchronized void release() {
    if (cb != null) cm.unregisterNetworkCallback(cb);
    cb = null;
    cellular = null;
  }

  public HttpURLConnection openUrlOverCellular(String urlStr, int timeoutMs) throws Exception {
    if (cellular == null) throw new IllegalStateException("Cellular non pronta: chiama acquire()");
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) cellular.openConnection(url);
    conn.setConnectTimeout(timeoutMs);
    conn.setReadTimeout(timeoutMs);
    return conn;
  }

  public String simpleGetOverCellular(String urlStr) throws Exception {
    HttpURLConnection c = openUrlOverCellular(urlStr, 8000);
    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) sb.append(line).append('\n');
    br.close();
    return sb.toString();
  }

  public static String simpleGetDefault(String urlStr) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
    c.setConnectTimeout(4000);
    c.setReadTimeout(4000);
    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) sb.append(line).append('\n');
    br.close();
    return sb.toString();
  }
}
