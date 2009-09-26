package com.andrewchatham;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.NetworkInfo;
import android.util.Log;

/**
 * Controls interaction with the boxee server. No UI management.
 * 
 * @author chatham
 *
 */
public class Remote {

  interface ErrorHandler {
    public void ShowError(int id);
    public void ShowError(String s);
  }
  static public final int BAD_PORT = -1;
  public final static String TAG = BoxeeRemote.class.toString();
  private ErrorHandler mError;
  private String mHost;
  private int mPort;
  private boolean mRequireWifi;

  private NetworkInfo mWifiInfo;

  public Remote(ErrorHandler error, NetworkInfo wifiInfo) {
    mError = error;
    mWifiInfo = wifiInfo;
  }

  /**
   * Tell boxee to change the volume. This involves doing a getVolume request,
   * then a setVolume request.
   * 
   * @param percent
   *          percent increase/decrease in volume
   */
  public void changeVolume(final int percent) {
	if (mHost == null)
		return;
	
    final String getvolume = getRequestPrefix() + String.format("getVolume()");
    // TODO: Move around
    final Pattern VOLUME_RE = Pattern.compile("^<li>([0-9]+)",
        Pattern.MULTILINE);

    // First ask for the current volume.
    try {
      new HttpRequest(getvolume, new Handler() {
        public void HandleResponse(boolean success, String resp) {
          if (!success) {
            mError.ShowError("Problem fetching URL " + getvolume);
          }

          // Parse out the current volume and send a setvolume request.
          Matcher m = VOLUME_RE.matcher(resp);
          if (m != null && m.find()) {
            int current_volume = Integer.parseInt(m.group(1));
            int new_volume = Math.max(0, Math
                .min(100, current_volume + percent));
            Log.d(TAG, "Setting volume to " + new_volume);
            final String setvolume = getRequestPrefix()
                + String.format("setVolume(%d)", new_volume);
            sendHttpCommand(setvolume);
          } else {
            Log.d(TAG, "Could not parse RE " + resp);
          }
        }
      });
    } catch (MalformedURLException e) {
      mError.ShowError("Malformed URL: " + getvolume);
    }
  }

  /**
   * Return the HTTP request prefix for sending boxee a command.
   * 
   * @return URL to send to boxee, up to but not including the boxee command
   */
  public String getRequestPrefix() {
    return String.format("http://%s:%d/xbmcCmds/xbmcHttp?command=", mHost,
        mPort);
  }

  public String host() {
    return mHost;
  }

  private void sendHttpCommand(final String request) {
    Log.d(TAG, "Fetching " + request);
    try {
      new HttpRequest(request, new Handler() {
        public void HandleResponse(boolean success, String resp) {
          if (!success) {
            mError.ShowError("Problem fetching URL " + request);
          }
        }
      });
    } catch (MalformedURLException e) {
      mError.ShowError("Malformed URL: " + request);
    }
  }

  public void sendKeyPress(int keycode) {
    if (mHost == null || mHost.length() == 0) {
      mError.ShowError("Run scan or go to settings and set the host");
      return;
    }

    if (mPort == BAD_PORT) {
      mError.ShowError("Run scan or go to settings and set the port");
      return;
    }

    if (mRequireWifi && !mWifiInfo.isAvailable()) {
      mError.ShowError(R.string.no_wifi);
      return;
    }

    final String request = getRequestPrefix()
        + String.format("SendKey(%d)", keycode);
    sendHttpCommand(request);
  }

  public void setHostPort(String host, int port) {
    mHost = host;
    mPort = port;
  }

  public void setRequireWifi(boolean requireWifi) {
    mRequireWifi = requireWifi;
  }

}
