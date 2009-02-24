package com.andrewchatham;

import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;

import android.graphics.BitmapFactory;

class CurrentlyPlayingThread extends Thread {
  private NowPlaying mPlaying;
  private BoxeeRemote mApp;
  private Remote mRemote;

  CurrentlyPlayingThread(BoxeeRemote app, Remote remote) {
    mRemote = remote;
    mApp = app;
  }

  public void run() {
    downloadThumbnail();
    mApp.setNowPlaying(mPlaying);
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
    r.fetch();
    if (!r.success()) {
      return false;
    }
    mPlaying = new NowPlaying(r.response());
    return true;
  }

  private boolean getThumbnail() {
    final String request = mRemote.getRequestPrefix()
        + String.format("getthumbnail(%s)", URLEncoder.encode(mPlaying
            .getThumbnailUrl()));
    BlockingHttpRequest r;
    r = new BlockingHttpRequest(request);
    r.fetch();
    if (!r.success()) {
      return false;
    }
    String shorter = r.response().replaceAll("<html>", "").replaceAll(
        "</html>", "");
    byte[] thumb = Base64.decodeBase64(shorter.getBytes());
    mPlaying.setThumbnail(BitmapFactory.decodeByteArray(thumb, 0, thumb.length));

    return true;
  }
}
