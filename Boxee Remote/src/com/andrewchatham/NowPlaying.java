package com.andrewchatham;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;

/**
 * NowPlaying represents information we know about the currently playing item in
 * Boxee. It parses the information from Boxee's getcurrentlyplaying() function.
 */
final class NowPlaying {
  final static Pattern LIST_ITEM = Pattern.compile(
      "^<li>([A-Za-z]+):([^\n<]+)", Pattern.MULTILINE);

  private HashMap<String, String> mEntries;
  private Bitmap mThumbnail;

  NowPlaying(String s) {
    Matcher m = LIST_ITEM.matcher(s);
    HashMap<String, String> entries = new HashMap<String, String>();
    mEntries = entries;
    while (m.find()) {
      entries.put(m.group(1), m.group(2));
    }
  }

  String getFilename() {
    return mEntries.get("Filename");
  }

  String getThumbnailUrl() {
    return mEntries.get("Thumb");
  }

  String getTitle() {
    return mEntries.get("Title");
  }

  public Bitmap getThumbnail() {
    return mThumbnail;
  }

  public void setThumbnail(Bitmap thumbnail) {
    mThumbnail = thumbnail;
  }

  /**
   * Return the elapsed and total time of the track, if available
   * 
   * @return Elapsed and total time as a human-readable string.
   */
  public String getTimeInfo() {
    String time = mEntries.get("Time");
    String duration = mEntries.get("Duration");

    if (time == null) {
      return null;
    } else if (duration == null) {
      return "Elapsed: " + time;
    } else {
      return String.format("Elapsed: %s/%s", time, duration);
    }
  }
}
