package com.andrewchatham;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyCharacterMap.KeyData;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class BoxeeRemote extends Activity implements
    OnSharedPreferenceChangeListener, Discoverer.Receiver, Remote.ErrorHandler {
  public final static String TAG = BoxeeRemote.class.toString();
  public static final int PREFERENCES = Menu.FIRST;
  public static final int REQUEST_PREF = 1;
  private final static int ACTIVITY_SCAN = 1;
  
  private String mUser;
  private String mPassword;

  /**
   * If the user has run a scan before, this is set to the name of their
   * preferred server.
   */
  private String mAutoName;

  private final static int CODE_LEFT = 272;
  private final static int CODE_RIGHT = 273;
  private final static int CODE_UP = 270;
  private final static int CODE_DOWN = 271;
  private final static int CODE_SELECT = 256;
  private final static int CODE_BACK = 257;

  // I made these up as unlikely codes to be used in XBMC's sendKey.
  private final static int CODE_VOLUME_UP = 0xAABBCC2;
  private final static int CODE_VOLUME_DOWN = 0xAABBCC1;

  final int KEY_ASCII = 0xF100;
  final int KEY_INVALID = 0xFFFF;

  private Remote mRemote;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    mRemote = new Remote(this, connectivity
        .getNetworkInfo(ConnectivityManager.TYPE_WIFI));

    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

    SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    setContentView(R.layout.remote);

    setPreferences(prefs);
    prefs.registerOnSharedPreferenceChangeListener(this);

    if (mAutoName != null && mAutoName.length() > 0) {
      // Only send a discovery request if the user has done a scan before and
      // picked a server.
      new Discoverer((WifiManager) getSystemService(Context.WIFI_SERVICE), this)
          .start();
    }

    setButtonAction(R.id.left, CODE_LEFT);
    setButtonAction(R.id.right, CODE_RIGHT);
    setButtonAction(R.id.up, CODE_UP);
    setButtonAction(R.id.down, CODE_DOWN);
    setButtonAction(R.id.select, CODE_SELECT);
    setButtonAction(R.id.back, CODE_BACK);

    // Don't try to get the thumbnail until after the Discoverer had a chance to
    // do its job.
    getNowPlayingAfter(1000);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem scan = menu.add(R.string.scan);
    scan.setIcon(android.R.drawable.ic_menu_manage);
    scan.setIntent(new Intent(this, Scan.class));
    scan.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        startActivityForResult(item.getIntent(), ACTIVITY_SCAN);
        return true;
      }
    });

    MenuItem settings = menu.add(R.string.settings);
    settings.setIcon(android.R.drawable.ic_menu_preferences);
    settings.setIntent(new Intent(this, Preferences.class));

    // MenuItem help = menu.add(R.string.help);
    // help.setIcon(android.R.drawable.ic_menu_help);
    // help.setOnMenuItemClickListener(new OnMenuItemClickListener() {
    // public boolean onMenuItemClick(MenuItem item) {
    // return true;
    // }
    // });

    return true;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == ACTIVITY_SCAN && resultCode != RESULT_CANCELED) {
      mRemote.setHostPort(data.getStringExtra("host"), data.getIntExtra("port",
          Remote.BAD_PORT));
      String name = data.getStringExtra("name");
      
      if (data.getBooleanExtra("auth", false))
        passwordCheck();

      // Change the preferences to indicate that we should autodiscover the
      // server with this name next time.
      SharedPreferences prefs = PreferenceManager
          .getDefaultSharedPreferences(this);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString(getString(R.string.discovered_name_key), name);
      editor.putString(getString(R.string.host_key), "");
      editor.commit();
    }
  }

  /**
   * Set the NowPlaying information, such as the thumbnail image. Also schedule
   * another fetch of the NowPlaying info after a few seconds.
   * 
   * @param playing
   */
  public void setNowPlaying(final NowPlaying playing) {
    runOnUiThread(new Runnable() {
      public void run() {
        if (playing != null) {
          ImageView view = (ImageView) findViewById(R.id.thumbnail);
          view.setImageBitmap(playing.getThumbnail());

          TextView title = (TextView) findViewById(R.id.title);
          title.setText(playing.getTitle());

          TextView time = (TextView) findViewById(R.id.timeinfo);
          time.setText(playing.getTimeInfo());
        }

        // Schedule another attempt to get the currently playing.
        getNowPlayingAfter(10000);
      }
    });
  }

  /**
   * Schedule an attempt to get the currently-playing item.
   * 
   * @param delay_ms
   *          Delay before attempt in milliseconds
   */
  private void getNowPlayingAfter(int delay_ms) {
    Handler h = new Handler();
    Runnable r = new Runnable() {
      public void run() {
        new CurrentlyPlayingThread(BoxeeRemote.this, mRemote).start();
      }
    };
    h.postDelayed(r, delay_ms);
  }

  /**
   * Handler an android keypress and send it to boxee if appropriate.
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    int code = eventToBoxeeCode(keyCode, event);

    switch (code) {
    case CODE_VOLUME_UP:
      mRemote.changeVolume(20);
      return true;
    case CODE_VOLUME_DOWN:
      mRemote.changeVolume(-20);
      return true;
    default:
      mRemote.sendKeyPress(code);
      return true;
    case KEY_INVALID:
      return super.onKeyDown(keyCode, event);
    }
  }

  /**
   * Translate an android key event into a boxee keycode.
   * 
   * @param keyCode
   *          keycode from onKeyDown
   * @param event
   *          key event from onKeyDown
   * @return argument to boxee's sendKey function
   */
  private int eventToBoxeeCode(int keyCode, KeyEvent event) {
    KeyData keyData = new KeyData();
    event.getKeyData(keyData);
    Log.d(TAG, "Unicode is " + event.getUnicodeChar());

    // Certain events we hard-code to certain event codes.
    switch (keyCode) {
    case KeyEvent.KEYCODE_DEL:
      return CODE_BACK;
    case KeyEvent.KEYCODE_BACK:
      return CODE_BACK;
    case KeyEvent.KEYCODE_DPAD_CENTER:
      return CODE_SELECT;
    case KeyEvent.KEYCODE_DPAD_DOWN:
      return CODE_DOWN;
    case KeyEvent.KEYCODE_DPAD_UP:
      return CODE_UP;
    case KeyEvent.KEYCODE_DPAD_LEFT:
      return CODE_LEFT;
    case KeyEvent.KEYCODE_DPAD_RIGHT:
      return CODE_RIGHT;
    case KeyEvent.KEYCODE_VOLUME_UP:
      return CODE_VOLUME_UP;
    case KeyEvent.KEYCODE_VOLUME_DOWN:
      return CODE_VOLUME_DOWN;

    case KeyEvent.KEYCODE_SPACE:
    case KeyEvent.KEYCODE_ENTER:
      // Some special keycodes we can translate from ASCII
      return event.getUnicodeChar() + KEY_ASCII;
    }

    String punctuation = "!@#$%^&*()[]{}/?|'\",.<>";
    if (Character.isLetterOrDigit(keyData.displayLabel) ||
        punctuation.indexOf(keyData.displayLabel) != -1) {
      return event.getUnicodeChar() + KEY_ASCII;
    }
    
    return KEY_INVALID;
  }

  /**
   * Set up a navigation button in the UI. Sets the focus to false so that we
   * can capture KEYCODE_DPAD_CENTER.
   * 
   * @param id
   *          id of the button in the resource file
   * 
   * @param keycode
   *          keycode we should send to boxee when this button is pressed
   */
  private void setButtonAction(int id, final int keycode) {
    ImageButton button = (ImageButton) findViewById(id);
    button.setFocusable(false);
    button.setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        mRemote.sendKeyPress(keycode);
      }
    });
  }

  /**
   * Display an error from R.strings, may be called from any thread
   * 
   * @param id
   *          an id from R.strings
   */
  public void ShowError(int id) {
    ShowError(getString(id));
  }

  /**
   * Display a short error via a popup message.
   */
  private void ShowErrorInternal(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  /**
   * Show an error, may be called from any thread
   */
  public void ShowError(final String s) {
    runOnUiThread(new Runnable() {
      public void run() {
        ShowErrorInternal(s);
      }
    });
  }

  /**
   * Set the state of the application based on prefs. This should be called
   * after every preference change or when starting up.
   * 
   * @param prefs
   */
  private void setPreferences(SharedPreferences prefs) {

    String host = prefs.getString(getString(R.string.host_key), null);
    mAutoName = prefs.getString(getString(R.string.discovered_name_key), null);
    
    mPassword = prefs.getString(getString(R.string.password_key), null);
    mUser = prefs.getString(getString(R.string.user_key), null);
    
    if (mPassword != null && mPassword.length() > 0) {
      BlockingHttpRequest.setUserPassword(mUser, mPassword);
    }

    // Only set the host if it was non-empty. We don't want to overwrite the
    // auto-discovered values.
    if (host != null && host.length() > 0) {
      int port;
      try {
        port = Integer.parseInt(prefs.getString(getString(R.string.port_key),
            "8800"));
      } catch (NumberFormatException e) {
        port = Remote.BAD_PORT;
        ShowError("Port must be a number");
      }
      mRemote.setHostPort(host, port);
    }

    try {
      int timeout_ms = Integer.parseInt(prefs.getString(
          getString(R.string.timeout_key), "1000"));
      BlockingHttpRequest.setTimeout(timeout_ms);
    } catch (NumberFormatException e) {
      ShowError("Timeout must be a number");
    }
    mRemote.setRequireWifi(prefs.getBoolean(
        getString(R.string.require_wifi_key), true));

    boolean hidden = prefs.getBoolean(getString(R.string.hide_arrows), false);
    findViewById(R.id.table).setVisibility(hidden ? View.GONE : View.VISIBLE);
  }

  /**
   * Callback when user alters preferences.
   */
  public void onSharedPreferenceChanged(SharedPreferences prefs, String pref) {
    setPreferences(prefs);
  }

  /**
   * Called when the discovery request we sent in onCreate finishes. If we find
   * a server matching mAutoName, we use that.
   * 
   * @param servers
   *          list of discovered servers
   */
  public void addAnnouncedServers(ArrayList<BoxeeServer> servers) {
    String host = mRemote.host();
    if (host != null && host.length() > 0) {
      Log.d(TAG, "Skipping announced servers. Set manually");
      return;
    }

    for (int k = 0; k < servers.size(); ++k) {
      BoxeeServer server = servers.get(k);
      if (server.name().equals(mAutoName)) {
        if (!server.valid()) {
          ShowError(String.format("Found '%s' but looks broken", server
              .name()));
          continue;
        } else {
          // Yay, found it and it works
          mRemote.setHostPort(server.address().getHostAddress(), server.port());
          if (server.authRequired())
            passwordCheck();
          return;
        }
      }
    }

    ShowError(String.format("Could not find preferred server '%s'",
        mAutoName));
  }
  
  private void passwordCheck() {
    String password = BlockingHttpRequest.password(); 
    if (password == null || password.length() == 0)
      ShowError("Server requires password. Set one in preferences.");
 }
 }