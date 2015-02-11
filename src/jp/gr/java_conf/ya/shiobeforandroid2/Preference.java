package jp.gr.java_conf.ya.shiobeforandroid2; // Copyright (c) 2013-2015 YA <ya.androidapp@gmail.com> All rights reserved.

import android.os.Bundle;
import android.preference.PreferenceActivity;

public final class Preference extends PreferenceActivity {
	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.pref);
	}
}
