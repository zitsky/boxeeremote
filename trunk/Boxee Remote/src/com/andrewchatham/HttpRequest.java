package com.andrewchatham;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;

import android.util.Log;

/**
 * Performs a blocking HTTP get request, without much flexibility.
 */
class BlockingHttpRequest {
  private URL mUrl;
  private boolean mSuccess;
  private String mResult;

  // We set these for all requests.
  private static int mTimeout = 2000;
  private static String mPassword;
  private static String mUser;

  static public void setTimeout(int timeout_ms) {
    mTimeout = timeout_ms;
  }

  /**
   * Constructor.
   */
  public BlockingHttpRequest(String url) {
    try {
      mUrl = new URL(url);
    } catch (MalformedURLException e) {
    }
  }

  /**
   * Returns whether the fetch resulted in a 200.
   */
  boolean success() {
    return mSuccess;
  }

  /**
   * Returns the fetched content, or null if the fetch failed.
   */
  String response() {
    return mResult;
  }

  /**
   * Perform the blocking fetch.
   */
  void fetch() {
    if (mUrl == null)
      return;

    try {
      HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
      connection.setConnectTimeout(mTimeout);
      connection.setReadTimeout(mTimeout);
      maybeAddAuthentication(connection);
      connection.connect();
      InputStream is = connection.getInputStream();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      int bytesRead;
      byte[] buffer = new byte[1024];
      while ((bytesRead = is.read(buffer)) != -1) {
        os.write(buffer, 0, bytesRead);
      }
      os.flush();
      os.close();
      is.close();
      Log.d(HttpRequest.TAG, String.format(
          "finished request(size=%d, remote=%s)", os.size(), mUrl.toString()));
      mResult = os.toString();
      mSuccess = connection.getResponseCode() == 200;
    } catch (IOException e) {
      mSuccess = false;
    }
  }

  private void maybeAddAuthentication(HttpURLConnection conn) {
    if (mPassword != null && mPassword.length() != 0) {
      byte[] enc = Base64.encodeBase64((mUser + ":" + mPassword).getBytes());
      conn.setRequestProperty("Authorization", "Basic " + new String(enc));
    }

  }

  public static void setUserPassword(String user, String password) {
    mUser = user;
    mPassword = password;
  }

  public static String password() {
    return mPassword;
  }
}

/**
 * A thread object which performs an HTTP get request, which can be used to
 * perform non-blocking fetches.
 */
class HttpRequest extends Thread {
  static final String TAG = "HttpRequest";
  final private Handler mHandler;
  final private BlockingHttpRequest mReq;

  /**
   * Callback interface for receiving the result of an asynchronous fetch. This
   * will be called from the HttpRequest thread when the fetch completes.
   */
  interface Handler {
    /*
     * Handle the response.
     * 
     * @param success true if the fetch completed with a 200 status code
     * 
     * @param content content of the fetched page, or null if the fetch was
     * unsuccessful
     */
    public void HandleResponse(boolean success, String content);
  }

  /**
   * Constructor
   * 
   * @param url
   *          url to fetch
   * 
   * @param handler
   *          handler which will receive notification when the fetch completes
   *          or has an error
   */
  public HttpRequest(String url, Handler handler) throws MalformedURLException {
    mReq = new BlockingHttpRequest(url);
    mHandler = handler;
    Log.d(TAG, String.format("started request(remote=%s)", url));
    start();
  }

  public void run() {
    Log.d(TAG, "Get thread started");
    mReq.fetch();
    mHandler.HandleResponse(mReq.success(), mReq.response());
  }
}
