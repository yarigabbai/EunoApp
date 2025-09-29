package com.euno.autopilot;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.euno.autopilot.net.CellularBinder;

@CapacitorPlugin(name = "Cellular")
public class CellularPlugin extends Plugin {
  private CellularBinder binder;

  @Override
  public void load() {
    binder = new CellularBinder(getContext());
    binder.acquire(); // tiene viva la rete cellulare in parallelo
  }

  @Override
  protected void handleOnStop() {
    super.handleOnStop();
    if (binder != null) binder.release();
  }

  /** GET su rete cellulare: Plugins.Cellular.internetGet({ url }) */
  @com.getcapacitor.PluginMethod
  public void internetGet(PluginCall call) {
    String url = call.getString("url");
    if (url == null || url.isEmpty()) { call.reject("url mancante"); return; }

    getBridge().execute(() -> {
      try {
        String body = binder.simpleGetOverCellular(url);
        JSObject ret = new JSObject().put("body", body);
        call.resolve(ret);
      } catch (Throwable t) {
        Log.e("CellularPlugin", "internetGet error", t);
       call.reject(t.getMessage(), t.toString());

      }
    });
  }

  /** GET su Wi-Fi locale (default route): Plugins.Cellular.localGet({ path: \"ping\" }) */
  @com.getcapacitor.PluginMethod
  public void localGet(PluginCall call) {
    String path = call.getString("path", "ping"); // default /ping
    String url = "http://192.168.4.1/" + path;

    getBridge().execute(() -> {
      try {
        String body = CellularBinder.simpleGetDefault(url);
        JSObject ret = new JSObject().put("body", body);
        call.resolve(ret);
      } catch (Throwable t) {
        Log.e("CellularPlugin", "localGet error", t);
call.reject(t.getMessage(), t.toString());
      }
    });
  }
}
