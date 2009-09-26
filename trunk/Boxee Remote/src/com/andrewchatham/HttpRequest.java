package com.andrewchatham;

import java.net.MalformedURLException;

import android.util.Log;

/**
 * A thread object which performs an HTTP get request, which can be used to
 * perform non-blocking fetches.
 */
class HttpRequest extends Thread {
  static final String TAG = "HttpRequest";
  final private Handler mHandler;
  final private BlockingHttpRequest mReq;

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
