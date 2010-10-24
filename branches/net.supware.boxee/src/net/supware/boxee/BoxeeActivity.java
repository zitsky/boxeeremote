package net.supware.boxee;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.KeyCharacterMap.KeyData;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class BoxeeActivity extends Activity implements
		OnSharedPreferenceChangeListener, DiscovererThread.Receiver,
		Remote.ErrorHandler, OnClickListener {

	public final static String TAG = BoxeeActivity.class.toString();

	// Messages
	public static final int MESSAGE_UPDATE_ELAPSED = 100;

	// Menu items
	private static final int MENU_MODE_GESTURE = Menu.FIRST;
	private static final int MENU_MODE_DPAD = Menu.FIRST + 1;
	private static final int MENU_SETTINGS = Menu.FIRST + 2;
	private MenuItem mGestureMenuItem;
	private MenuItem mDpadMenuItem;

	// ViewFlipper
	private static final int PAGE_DPAD = 0;
	private static final int PAGE_GESTURE = 1;
	private static final int PAGE_NOWPLAYING = 2;
	private ViewFlipper mFlipper;

	// Other Views
	ImageView mImageThumbnail;
	Button mButtonPlayPause;
	TextView mTextTitle;
	TextView mTextArrowTitle;
	TextView mTextElapsed;
	TextView mTextArrowElapsed;
	TextView mDuration;
	ProgressBar mElapsedBar;

	private Settings mSettings;
	private Remote mRemote;
	private NowPlaying mNowPlaying = new NowPlaying();

	private Point mTouchPoint = new Point();
	private boolean mDragged = false;
	private boolean mIsNowPlaying = false;
	private boolean mIsScreenOverride = false;
	private ProgressDialog mPleaseWaitDialog;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case CurrentlyPlayingThread.MESSAGE_NOW_PLAYING_UPDATED:
				refreshNowPlaying();
				getNowPlayingAfter(5000);
				break;
			case CurrentlyPlayingThread.MESSAGE_THUMBNAIL_UPDATED:
				refreshThumbnail();
				break;
			case MESSAGE_UPDATE_ELAPSED:
				refreshElapsed();
				break;
			}
		}
	};

	Thread mElapsedThread = new Thread(new Runnable() {
		public void run() {
			while (true) {
				mHandler.sendMessage(mHandler
						.obtainMessage(MESSAGE_UPDATE_ELAPSED));
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// ignore
					e.printStackTrace();
				}
			}
		}
	});

	Runnable mRunnableGetNowPlaying = new Runnable() {
		public void run() {
			new CurrentlyPlayingThread(mHandler, mRemote, mNowPlaying).start();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mRemote = new Remote(this, this);

		setContentView(R.layout.main);

		// Setup flipper
		mFlipper = (ViewFlipper) findViewById(R.id.flipper);
		mFlipper.setInAnimation(this, android.R.anim.fade_in);
		mFlipper.setOutAnimation(this, android.R.anim.fade_out);

		// Find other views
		mImageThumbnail = (ImageView) findViewById(R.id.thumbnail);
		mButtonPlayPause = (Button) findViewById(R.id.buttonPlayPause);
		mTextTitle = (TextView) findViewById(R.id.textNowPlayingTitle);
		mTextArrowTitle = (TextView) findViewById(R.id.textArrowNowPlayingTitle);
		mTextElapsed = (TextView) findViewById(R.id.textElapsed);
		mTextArrowElapsed = (TextView) findViewById(R.id.textArrowNowPlayingElapsed);
		mDuration = (TextView) findViewById(R.id.textDuration);
		mElapsedBar = (ProgressBar) findViewById(R.id.progressTimeBar);

		mSettings = new Settings(this);

		loadPreferences();

		setButtonAction(R.id.left, KeyEvent.KEYCODE_DPAD_LEFT);
		setButtonAction(R.id.right, KeyEvent.KEYCODE_DPAD_RIGHT);
		setButtonAction(R.id.up, KeyEvent.KEYCODE_DPAD_UP);
		setButtonAction(R.id.down, KeyEvent.KEYCODE_DPAD_DOWN);
		setButtonAction(R.id.select, KeyEvent.KEYCODE_DPAD_CENTER);
		setButtonAction(R.id.back, KeyEvent.KEYCODE_BACK);
		setButtonAction(R.id.nowplaying, 0);
		setButtonAction(R.id.buttonPlayPause, 0);
		setButtonAction(R.id.buttonStop, 0);
		setButtonAction(R.id.buttonSmallSkipBack, 0);
		setButtonAction(R.id.buttonSmallSkipFwd, 0);
	}

	@Override
	protected void onPause() {
		mSettings.unlisten(this);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSettings.listen(this);
		getNowPlayingAfter(100);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		mGestureMenuItem = menu.add(Menu.NONE, MENU_MODE_GESTURE, 0,
				R.string.menu_mode).setIcon(R.drawable.ic_menu_boxee_logo);

		mDpadMenuItem = menu.add(Menu.NONE, MENU_MODE_DPAD, 0,
				R.string.menu_mode).setIcon(R.drawable.ic_menu_dpad);

		menu.add(Menu.NONE, MENU_SETTINGS, 0, R.string.settings).setIcon(
				android.R.drawable.ic_menu_preferences).setIntent(
				new Intent(this, SettingsActivity.class));

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean gesture = mFlipper.getDisplayedChild() == PAGE_GESTURE;
		mGestureMenuItem.setEnabled(!gesture);
		mGestureMenuItem.setVisible(!gesture);
		mDpadMenuItem.setEnabled(gesture);
		mDpadMenuItem.setVisible(gesture);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case MENU_MODE_GESTURE:
			mSettings.putPage(PAGE_GESTURE);
			return true;

		case MENU_MODE_DPAD:
			mSettings.putPage(PAGE_DPAD);
			return true;

		case MENU_SETTINGS:
			// This is already handled by menu.setIntent in create
			break;
		}
		return false;
	}

	@Override
	public void onClick(View v) {
		int duration;

		switch (v.getId()) {
		case R.id.nowplaying:
			flipTo(PAGE_NOWPLAYING);
			mRemote.goToNowPlaying();
			getNowPlayingAfter(100);
			break;

		case R.id.buttonPlayPause:
			mRemote.pause();
			getNowPlayingAfter(100);
			break;

		case R.id.buttonStop:
			mRemote.stop();
			getNowPlayingAfter(100);
			break;

		case R.id.buttonSmallSkipBack:
			// Seek backwards 10 seconds
			duration = mNowPlaying.getDurationSeconds();
			if (duration == 0)
				break;
			mRemote.seek(-10 * 100 / duration);
			getNowPlayingAfter(100);
			break;

		case R.id.buttonSmallSkipFwd:
			// Seek forwards 30 seconds
			duration = mNowPlaying.getDurationSeconds();
			if (duration == 0)
				break;
			mRemote.seek(30.0 * 100 / duration);
			getNowPlayingAfter(100);
			break;

		case R.id.back:
			mRemote.back();
			break;

		case R.id.left:
		case R.id.up:
		case R.id.down:
		case R.id.right:
		case R.id.select:
			simulateKeystroke(((Integer) v.getTag()).intValue());
			break;
		}

	}

	private void flipTo(int page) {
		if (mFlipper.getDisplayedChild() != page)
			mFlipper.setDisplayedChild(page);

		// TODO: animate these
		Button backButton = (Button) findViewById(R.id.back);
		backButton.setVisibility(page == PAGE_NOWPLAYING ? View.GONE
				: View.VISIBLE);

		int arrowVisibility = page == PAGE_NOWPLAYING
		|| !mIsNowPlaying ? View.GONE : View.VISIBLE;
		Button nowPlayingButton = (Button) findViewById(R.id.nowplaying);
		nowPlayingButton.setVisibility(arrowVisibility);
		mTextArrowTitle.setVisibility(arrowVisibility);
		mTextArrowElapsed.setVisibility(arrowVisibility);
	}

	private void refreshNowPlaying() {
		mIsNowPlaying = mNowPlaying.isNowPlaying();

		if (!mIsScreenOverride) 
			flipTo(mNowPlaying.isOnNowPlayingScreen() ? PAGE_NOWPLAYING : mSettings.getPage());

		if (!mIsNowPlaying) {
			mIsScreenOverride = false;
			if (mElapsedThread.isAlive())
				mElapsedThread.stop();
			return;
		}

		if (!mElapsedThread.isAlive())
			mElapsedThread.start();

		mButtonPlayPause.setBackgroundDrawable(getResources().getDrawable(
				mNowPlaying.isPaused() ? R.drawable.icon_osd_play
						: R.drawable.icon_osd_pause));

		String title = mNowPlaying.getTitle();
		mTextTitle.setText(title);
		mTextArrowTitle.setText(title);

		mDuration.setText(mNowPlaying.getDuration());
	}

	private void refreshElapsed() {
		String elapsed = mNowPlaying.getElapsed();
		mTextElapsed.setText(elapsed);
		mTextArrowElapsed.setText(elapsed);

		mElapsedBar.setProgress(mNowPlaying.getPercentage());
	}

	private void refreshThumbnail() {
		mImageThumbnail.setImageBitmap(mNowPlaying.getThumbnail());
	}

	/**
	 * Schedule an attempt to get the currently-playing item.
	 * 
	 * @param delay_ms
	 *            Delay before attempt in milliseconds
	 */
	private void getNowPlayingAfter(int delay_ms) {
		mHandler.removeCallbacks(mRunnableGetNowPlaying);
		mHandler.postDelayed(mRunnableGetNowPlaying, delay_ms);
	}

	/**
	 * Handler an android keypress and send it to boxee if appropriate.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		int code = event.getKeyCode();

		KeyData keyData = new KeyData();
		event.getKeyData(keyData);
		Log.d(TAG, "Unicode is " + event.getUnicodeChar());

		String punctuation = "!@#$%^&*()[]{}/?|'\",.<>";
		if (Character.isLetterOrDigit(keyData.displayLabel)
				|| punctuation.indexOf(keyData.displayLabel) != -1) {
			mRemote.keypress(event.getUnicodeChar());
			return true;
		}

		switch (code) {

		case KeyEvent.KEYCODE_DEL:
			mRemote.back();
			getNowPlayingAfter(100);
			return true;

		case KeyEvent.KEYCODE_BACK:
			if (mFlipper.getDisplayedChild() == PAGE_NOWPLAYING) {
				mIsScreenOverride = true;
				flipTo(mSettings.getPage());
				getNowPlayingAfter(100);
			}
			else
				return super.onKeyDown(keyCode, event);
			return true;

		case KeyEvent.KEYCODE_DPAD_CENTER:
			mRemote.select();
			return true;

		case KeyEvent.KEYCODE_DPAD_DOWN:
			mRemote.down();
			return true;

		case KeyEvent.KEYCODE_DPAD_UP:
			mRemote.up();
			return true;

		case KeyEvent.KEYCODE_DPAD_LEFT:
			mRemote.left();
			return true;

		case KeyEvent.KEYCODE_DPAD_RIGHT:
			mRemote.right();
			return true;

		case KeyEvent.KEYCODE_VOLUME_UP:
			mRemote.changeVolume(20);
			return true;

		case KeyEvent.KEYCODE_VOLUME_DOWN:
			mRemote.changeVolume(-20);
			return true;

		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_ENTER:
			// Some special keycodes we can translate from ASCII
			mRemote.keypress(event.getUnicodeChar());
			return true;

		default:
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mFlipper.getDisplayedChild() != PAGE_GESTURE)
			return false;

		int x = (int) event.getX();
		int y = (int) event.getY();
		int sensitivity = 30;
		switch (event.getAction()) {

		case MotionEvent.ACTION_UP:
			if (!mDragged) {
				mRemote.select();
				return true;
			}
			break;

		case MotionEvent.ACTION_DOWN:
			mTouchPoint.x = x;
			mTouchPoint.y = y;
			mDragged = false;
			return true;

		case MotionEvent.ACTION_MOVE:
			if (x - mTouchPoint.x > sensitivity) {
				mRemote.right();
				mTouchPoint.x += sensitivity;
				mTouchPoint.y = y;
				mDragged = true;
				return true;
			} else if (mTouchPoint.x - x > sensitivity) {
				mRemote.left();
				mTouchPoint.x -= sensitivity;
				mTouchPoint.y = y;
				mDragged = true;
				return true;
			} else if (y - mTouchPoint.y > sensitivity) {
				mRemote.down();
				mTouchPoint.y += sensitivity;
				mTouchPoint.x = x;
				mDragged = true;
				return true;
			} else if (mTouchPoint.y - y > sensitivity) {
				mRemote.up();
				mTouchPoint.y -= sensitivity;
				mTouchPoint.x = x;
				mDragged = true;
				return true;
			}
			break;
		}

		return false;
	}

	/**
	 * Set up a navigation button in the UI. Sets the focus to false so that we
	 * can capture KEYCODE_DPAD_CENTER.
	 * 
	 * @param id
	 *            id of the button in the resource file
	 * 
	 * @param keycode
	 *            keyCode we should send to Boxee when this button is pressed
	 */
	private void setButtonAction(int id, final int keyCode) {
		Button button = (Button) findViewById(id);
		button.setFocusable(false);
		button.setTag(new Integer(keyCode));
		button.setOnClickListener(this);
	}

	/**
	 * Wrapper-function taking a KeyCode. A complete KeyStroke is DOWN and UP
	 * Action on a key!
	 */
	private void simulateKeystroke(int keyCode) {
		onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
		onKeyUp(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
	}

	/**
	 * Display an error from R.strings, may be called from any thread
	 * 
	 * @param id
	 *            an id from R.strings
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
	private void loadPreferences() {

		// Setup the proper pageflipper page:
		int page = mSettings.getPage();
		if (mFlipper.getDisplayedChild() != page)
			flipTo(page);

		// Parse the credentials, if needed.
		String user = mSettings.getUser();
		String password = mSettings.getPassword();
		if (!TextUtils.isEmpty(password)) {
			HttpRequestBlocking.setUserPassword(user, password);
		}

		// Only set the host if manual. Otherwise we'll auto-detect it with
		// Discoverer -> addAnnouncedServers
		if (mSettings.isManual()) {
			mRemote.setServer(mSettings.constructServer());
			getNowPlayingAfter(100);
		}
		else {
			mPleaseWaitDialog = ProgressDialog.show(this, "", "Connecting to "
					+ mSettings.getServerName() + "...", true);
			DiscovererThread discoverer = new DiscovererThread(this, this);
			discoverer.start();
		}
		
		// Setup the HTTP timeout.
		int timeout_ms = mSettings.getTimeout();
		HttpRequestBlocking.setTimeout(timeout_ms);

		// Read the "require wifi" setting.
		boolean requireWifi = mSettings.requiresWifi();
		mRemote.setRequireWifi(requireWifi);
	}

	/**
	 * Callback when user alters preferences.
	 */
	public void onSharedPreferenceChanged(SharedPreferences prefs, String pref) {
		loadPreferences();
	}

	/**
	 * Called when the discovery request we sent in onCreate finishes. If we
	 * find a server matching mAutoName, we use that.
	 * 
	 * @param servers
	 *            list of discovered servers
	 */
	public void addAnnouncedServers(ArrayList<BoxeeServer> servers) {

		// This condition shouldn't ever be true.
		if (mSettings.isManual()) {
			Log.d(TAG, "Skipping announced servers. Set manually");
			return;
		}

		mPleaseWaitDialog.dismiss();

		String preferred = mSettings.getServerName();

		for (int k = 0; k < servers.size(); ++k) {
			BoxeeServer server = servers.get(k);
			if (server.name().equals(preferred)) {
				if (!server.valid()) {
					ShowError(String.format("Found '%s' but looks broken",
							server.name()));
					continue;
				} else {
					// Yay, found it and it works
					mRemote.setServer(server);
					mRemote.displayMessage("Connected to Boxee Remote");
					getNowPlayingAfter(100);
					if (server.authRequired())
						passwordCheck();
					return;
				}
			}
		}

		ShowError(String.format("Could not find preferred server '%s'",
				preferred));
	}

	private void passwordCheck() {
		// TODO: open a dialog box here instead
		String password = HttpRequestBlocking.password();
		if (password == null || password.length() == 0)
			ShowError("Server requires password. Set one in preferences.");
	}

}