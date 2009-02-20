package com.andrewchatham;

import java.net.MalformedURLException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyCharacterMap.KeyData;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

// http://www.iconspedia.com/pack/crystal-clear-actions-1303/
public class BoxeeRemote extends Activity implements
		OnSharedPreferenceChangeListener {
	private static final int BAD_PORT = -1;
	public final static String TAG = BoxeeRemote.class.toString();
	public static final int PREFERENCES = Menu.FIRST;
	public static final int REQUEST_PREF = 1;

	private String mHost;
	private int mPort = BAD_PORT;
	private boolean mRequireWifi;

	private NetworkInfo mWifiInfo;

	private final static int CODE_LEFT = 272;
	private final static int CODE_RIGHT = 273;
	private final static int CODE_UP = 270;
	private final static int CODE_DOWN = 271;
	private final static int CODE_SELECT = 256;
	private final static int CODE_BACK = 257;

	final int KEY_ASCII = 0xF100;
	final int KEY_INVALID = 0xFFFF;

	// TODO: getmedialocation(video)

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiInfo = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		setContentView(R.layout.remote);

		setPreferences(prefs);
		prefs.registerOnSharedPreferenceChangeListener(this);

		setButtonAction(R.id.left, CODE_LEFT);
		setButtonAction(R.id.right, CODE_RIGHT);
		setButtonAction(R.id.up, CODE_UP);
		setButtonAction(R.id.down, CODE_DOWN);
		setButtonAction(R.id.select, CODE_SELECT);
		setButtonAction(R.id.back, CODE_BACK);

		getThumbnail();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem settings = menu.add(getString(R.string.settings));
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(this, Preferences.class));

		return true;
	}

	final int THUMBNAIL_DELAY_MS = 1000 * 10;

	/**
	 * Set the thumbnail image and schedule another fetch of the thumbnail after a few seconds.
	 * 
	 *   @param bmp bitmap to set as the thumbnail, may be null
	 */
	public void setThumbnail(Bitmap bmp) {
		ImageView view = (ImageView) findViewById(R.id.thumbnail);
		view.setImageBitmap(bmp);

		// Schedule another attempt to get the thumbnail.
		Handler h = new Handler();
		Runnable r = new Runnable() {
			public void run() {
				getThumbnail();
			}
		};
		h.postDelayed(r, THUMBNAIL_DELAY_MS);
	}

	private void getThumbnail() {
		new CurrentlyPlayingThread(BoxeeRemote.this).start();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		int code = eventToBoxeeCode(keyCode, event);
		if (code != KEY_INVALID) {
			sendKeyPress(code, false);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

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

		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_ENTER:
			// Some special keycodes we can translate from ASCII
			return event.getUnicodeChar() + KEY_ASCII;
		}

		if (Character.isLetterOrDigit(keyData.displayLabel)) {
			return event.getUnicodeChar() + KEY_ASCII;
		}
		return KEY_INVALID;
	}

	public String getRequestPrefix() {
		return String.format("http://%s:%d/xbmcCmds/xbmcHttp?command=", mHost,
				mPort);
	}

	private void sendKeyPress(int keycode, final boolean fromAction) {
		if (mHost == null || mHost.length() == 0) {
			ShowError("Go to settings and set the host");
			if (fromAction)
				setResult(RESULT_CANCELED);
			return;
		}

		if (mPort == BAD_PORT) {
			ShowError("Go to settings and set the port");
			if (fromAction)
				setResult(RESULT_CANCELED);
			return;
		}

		if (mRequireWifi && !mWifiInfo.isAvailable()) {
			ShowError(R.string.no_wifi);
			if (fromAction)
				setResult(RESULT_CANCELED);
			return;
		}

		final String request = getRequestPrefix()
				+ String.format("SendKey(%d)", keycode);
		Log.d(TAG, "Fetching " + request);

		try {
			new HttpRequest(request, new HttpRequest.Handler() {
				public void HandleResponse(boolean success, String resp) {
					if (!success) {
						ShowErrorAsync("Problem fetching URL " + request);
						if (fromAction)
							setResult(RESULT_CANCELED);
					}
					if (fromAction)
						setResult(RESULT_OK);
				}
			});
		} catch (MalformedURLException e) {
			ShowErrorAsync("Malformed URL: " + request);
			if (fromAction)
				setResult(RESULT_CANCELED);
		}
	}

	/**
	 * Set up a navigation button in the UI. Sets the focus to false so that we
	 * can capture KEYCODE_DPAD_CENTER.
	 * 
	 * @param id id of the button in the resource file
	 * 
	 * @param keycode keycode we should send to boxee when this button is
	 * pressed
	 */
	private void setButtonAction(int id, final int keycode) {
		ImageButton button = (ImageButton) findViewById(id);
		button.setFocusable(false);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				sendKeyPress(keycode, true);
			}
		});
	}

	/**
	 * Display an error from R.strings
	 * 
	 * @param id an id from R.strings
	 */
	private void ShowError(int id) {
		ShowError(getString(id));
	}

	/**
	 * Display a short error via a popup message.
	 */
	private void ShowError(String s) {
		Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Show an error, but can be called from any thread.
	 */
	private void ShowErrorAsync(final String s) {
		runOnUiThread(new Runnable() {
			public void run() {
				ShowError(s);
			}
		});
	}

	/**
	 * 
	 * @param prefs
	 */
	private void setPreferences(SharedPreferences prefs) {
		try {
			mPort = Integer.parseInt(prefs.getString(
					getString(R.string.port_key), null));
		} catch (NumberFormatException e) {
			mPort = BAD_PORT;
			ShowError("Port must be a number");
		}
		mHost = prefs.getString(getString(R.string.host_key), null);
		mRequireWifi = prefs.getBoolean(getString(R.string.require_wifi_key),
				true);

		boolean hidden = prefs.getBoolean(getString(R.string.hide_arrows),
				false);
		findViewById(R.id.table).setVisibility(
				hidden ? View.GONE : View.VISIBLE);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String pref) {
		setPreferences(prefs);
	}
}