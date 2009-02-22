package com.andrewchatham;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NowPlaying represents information we know about the currently playing item in
 * Boxee. It parses the information from Boxee's getcurrentlyplaying() function.
 */
final class NowPlaying {
  final static Pattern LIST_ITEM = Pattern.compile(
      "^<li>([A-Za-z]+):([^\n<]+)", Pattern.MULTILINE);

  private HashMap<String, String> mEntries;

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

}
