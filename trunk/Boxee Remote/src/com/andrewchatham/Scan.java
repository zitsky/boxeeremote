package com.andrewchatham;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class Scan extends ListActivity implements Discoverer.Receiver {
  public static final String TAG = "Scan";
  private ArrayList<BoxeeServer> mServers;

  class ServerListAdapter extends ArrayAdapter<BoxeeServer> {

    public ServerListAdapter(Context context, ArrayList<BoxeeServer> servers) {
      super(context, android.R.layout.simple_list_item_1, servers);
    }

  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.scan);

    new Discoverer((WifiManager) getSystemService(Context.WIFI_SERVICE), this)
        .start();
  }

  public void addAnnouncedServers(final ArrayList<BoxeeServer> servers) {
    runOnUiThread(new Runnable() {
      public void run() {
        fillServerList(servers);
      }
    });
  }

  private void fillServerList(ArrayList<BoxeeServer> servers) {
    if (servers == null) {
      Toast.makeText(this, getText(R.string.none_found), Toast.LENGTH_SHORT)
          .show();
      setResult(RESULT_CANCELED);
      finish();
      return;
    }
    mServers = servers;
    Log.d(TAG, "Adding servers " + servers.size());
    setListAdapter(new ServerListAdapter(this, servers));
  }

  protected void onListItemClick(ListView l, View v, int position, long id) {
    BoxeeServer server = mServers.get(position);
    Intent response = new Intent();
    // TODO: Make server serializable or parcelable and pass it all along.
    response.putExtra("host", server.address().getHostAddress());
    response.putExtra("port", server.port());
    response.putExtra("name", server.name());
    response.putExtra("auth", server.authRequired());
    setResult(RESULT_OK, response); 
    finish();
  }
}
