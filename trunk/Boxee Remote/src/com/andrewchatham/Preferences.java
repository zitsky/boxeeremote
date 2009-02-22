package com.andrewchatham;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Handles preference storage for BoxeeRemote. Incredibly simple.
 */
public class Preferences extends PreferenceActivity {
  public static String KEY = "com.andrewchatham.BoxeeRemote";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }

}
