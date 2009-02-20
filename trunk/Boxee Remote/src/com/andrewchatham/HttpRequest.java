package com.andrewchatham;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.util.Log;

class BlockingHttpRequest {
	private URL mUrl;
	private boolean mSuccess;
	private String mResult;
	
	public BlockingHttpRequest(String url) {
		try {
			mUrl = new URL(url);
		} catch (MalformedURLException e) {
		}
	}
	
	boolean success() {
		return mSuccess;
	}
	
	String response() {
		return mResult;
	}

	void run() {
		if (mUrl == null) return;
		
		try {
		HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
		connection.setConnectTimeout(1000);
		connection.setReadTimeout(1500);
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
		Log.d(HttpRequest.TAG, String.format("finished request(size=%d, remote=%s)",
				os.size(), mUrl.toString()));
		mResult = os.toString();
		mSuccess = connection.getResponseCode() == 200;
		} catch (IOException e) {
			mSuccess = false;
		}
	}
}

class HttpRequest extends Thread {
	static final String TAG = "HttpRequest";
	final private Handler mHandler;
	final private BlockingHttpRequest mReq;

	interface Handler {
		public void HandleResponse(boolean success, String content);
	}

	public HttpRequest(String url, Handler handler)
	throws MalformedURLException {
		mReq = new BlockingHttpRequest(url);
		mHandler = handler;
		Log.d(TAG, String.format("started request(remote=%s)", url));
		start();
	}

	public void run() {
		Log.d(TAG, "Get thread started");
		mReq.run();
		mHandler.HandleResponse(mReq.success(), mReq.response());
	}
}
