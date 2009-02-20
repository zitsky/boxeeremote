package com.andrewchatham;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TableLayout;

public class CapturingView extends TableLayout {
	public CapturingView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public final static String TAG = "View";
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.d(TAG, "onTouchEvent");
		return false;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		Log.d(TAG, "onTrackballEvent");
		return false;
	}


}
