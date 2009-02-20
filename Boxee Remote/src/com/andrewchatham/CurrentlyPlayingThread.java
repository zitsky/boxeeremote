package com.andrewchatham;

import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

interface ThumbnailSetter {
	public void setThumbnail(Bitmap bmp);
}

class CurrentlyPlayingThread extends Thread {
	private NowPlaying mPlaying;
	private Bitmap mThumbnail;
	private BoxeeRemote mRemote;
	
	CurrentlyPlayingThread(BoxeeRemote remote) {
		mRemote = remote;
	}
	
	public void run() {
		downloadThumbnail();

		mRemote.runOnUiThread(new Runnable() {
			public void run() {
				mRemote.setThumbnail(mThumbnail);
			}
		});
	}
	
	private void downloadThumbnail() {
		if (!getCurrentlyPlaying())
			return;
		if (mPlaying.getThumbnailUrl() == null)
			return;
		if (!getThumbnail())
			return;
	}
	
	private boolean getCurrentlyPlaying() {
		final String request = mRemote.getRequestPrefix() + "getcurrentlyplaying()";
		BlockingHttpRequest r;
		r = new BlockingHttpRequest(request);
		r.run();
		if (!r.success()) {
			return false;
		}
		mPlaying = new NowPlaying(r.response());
		return true;
	}
	
	private boolean getThumbnail() {
		final String request = mRemote.getRequestPrefix() + String.format("getthumbnail(%s)", URLEncoder.encode(mPlaying.getThumbnailUrl()));
		BlockingHttpRequest r;
		r = new BlockingHttpRequest(request);
		r.run();
		if (!r.success()) {
			return false;
		}
		String shorter = r.response().replaceAll("<html>", "").replaceAll("</html>", "");
		byte[] thumb = Base64.decodeBase64(shorter.getBytes());
		mThumbnail = BitmapFactory.decodeByteArray(thumb, 0, thumb.length);

		return true;
	}
}
