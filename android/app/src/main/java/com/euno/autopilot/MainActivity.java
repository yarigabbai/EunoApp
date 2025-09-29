package com.euno.autopilot;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Registra il plugin che gestisce la rete cellulare in parallelo
    registerPlugin(CellularPlugin.class);

    // Sblocca contenuti misti (es. ws://, http://192.168.4.1 con app https)
    WebView wv = getBridge().getWebView();
    WebSettings ws = wv.getSettings();
    ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

    // Debug della WebView (utile in sviluppo)
    WebView.setWebContentsDebuggingEnabled(true);
  }
}
