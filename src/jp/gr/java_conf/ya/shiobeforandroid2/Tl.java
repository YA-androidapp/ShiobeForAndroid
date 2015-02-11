package jp.gr.java_conf.ya.shiobeforandroid2; // Copyright (c) 2013-2015 YA <ya.androidapp@gmail.com> All rights reserved.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import jp.gr.java_conf.ya.shiobeforandroid2.util.CheckNetworkUtil;
import jp.gr.java_conf.ya.shiobeforandroid2.util.ConnectionReceiver;
import jp.gr.java_conf.ya.shiobeforandroid2.util.MyConnectionLifeCycleListener;
import jp.gr.java_conf.ya.shiobeforandroid2.util.StringUtil;
import jp.gr.java_conf.ya.shiobeforandroid2.util.TlViewLayoutUtil;
import jp.gr.java_conf.ya.shiobeforandroid2.util.WriteLog;
import twitter4j.FilterQuery;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.UserStreamAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class Tl extends Activity implements ConnectionReceiver.Observer, TextToSpeech.OnInitListener {
	private boolean isMobile;
	private boolean mLongPressed;
	private boolean tl_repeat = true;

	private final CheckNetworkUtil checkNetworkUtil = new CheckNetworkUtil(this);

	private ConnectionReceiver mConnectionReceiver = new ConnectionReceiver(this);

	private final Handler handler = new Handler();

	private int currentIndex = 0;
	private static final int WC = LinearLayout.LayoutParams.WRAP_CONTENT, MP = LinearLayout.LayoutParams.MATCH_PARENT;

	private ListAdapter adapter;

	private ListView listView;

	private List<Status> statuses = new ArrayList<Status>(200);

	private long TlMaxId = -1;
	private long TlSinceId = -1;

	private Runnable runnable;

	private SharedPreferences pref_app;

	private String crpKey = "";
	private String uriString = "";

	private boolean enabled_tts = false;
	private boolean pref_enable_tts = false;
	private float pref_tts_set_pitch = 1.0f;
	private float pref_tts_set_rate = 1.0f;
	private long preStatusId = 0;
	private MyConnectionLifeCycleListener mMyConnectionLifeCycleListener = new MyConnectionLifeCycleListener();
	private MyUserStreamAdapter mMyUserStreamAdapter = new MyUserStreamAdapter();
	private TextToSpeech tts;
	private TwitterStream twitterStream;

	private final class MyUserStreamAdapter extends UserStreamAdapter {
		@Override
		public final void onException(final java.lang.Exception e) {
			if (twitterStream != null) {
				try {
					twitterStream.cleanUp();
				} catch (final Exception e1) {
				}
			}
		}

		@Override
		public final void onStatus(final Status status) {
			super.onStatus(status);

			if (status.getId() == preStatusId) {
				return;
			}
			preStatusId = status.getId();

			String statusUserScreenname = "";
			String statusText = "";
			String speechPan = "";
			String speechVolume = "";
			if (status.getRetweetedStatus() != null) {
				statusUserScreenname = status.getRetweetedStatus().getUser().getScreenName();
				statusText = status.getRetweetedStatus().getText().replaceAll("RT @" + statusUserScreenname + ": ", "");
				speechPan = "0.5";
				speechVolume = "0.5";
			} else {
				statusUserScreenname = status.getUser().getScreenName();
				statusText = status.getText();
				speechPan = "-0.5";
				speechVolume = "1.0";
			}

			statuses.add(0, status);

			if (enabled_tts) {
				speechText("@" + statusUserScreenname + " " + statusText, speechVolume, speechPan);
			}

			TlMaxId = ( ( TlMaxId == -1 ) || ( status.getId() > TlMaxId ) ) ? status.getId() : TlMaxId;
			TlSinceId = ( ( TlSinceId == -1 ) || ( status.getId() < TlSinceId ) ) ? status.getId() : TlSinceId;

			handler.post(new Runnable() {
				@Override
				public final void run() {
					adapter.listViewSetSelection(adapter.getSelectPos(1), true);
				}
			});
		}
	}

	private final String getCrpKey() {
		String crpKey = getString(R.string.app_name);
		final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		crpKey += telephonyManager.getDeviceId();
		crpKey += telephonyManager.getSimSerialNumber();
		try {
			final PackageInfo packageInfo = getPackageManager().getPackageInfo("jp.gr.java_conf.ya.shiobeforandroid2", PackageManager.GET_META_DATA);
			crpKey += Long.toString(packageInfo.firstInstallTime);
		} catch (NameNotFoundException e) {
			WriteLog.write(Tl.this, e);
		}
		return crpKey;
	}

	private final List<Status> getListStatus(final String uriString, final long sinceId, final long maxId) {
		if (checkNetworkUtil.isConnected() == false) {
			adapter.toast(getString(R.string.cannot_access_internet));
			return null;
		}

		final Paging paging = new Paging();
		if (sinceId > 0) {
			paging.setSinceId(sinceId);
		} else if (maxId > 0) {
			paging.setMaxId(maxId);
		}
		try {
			final String tag = StringUtil.uriStringToTag(uriString, true);
			WriteLog.write(this, "getListStatus() uriString: " + uriString + " tag: " + tag);

			if (( uriString.replace("(s)", "").equals("https://twitter.com") ) || ( uriString.replace("(s)", "").equals(ListAdapter.TWITTER_BASE_URI) )) {
				final int pref_home_count = isMobile ? Integer.parseInt(pref_app.getString("pref_home_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_home_count", "200"));
				paging.setCount(pref_home_count);
				return adapter.getTwitter(adapter.checkIndexFromPrefTwtr(), false).getHomeTimeline(paging);
			} else if (tag.replace("(s)", "").equals("home")) {
				final int pref_home_count = isMobile ? Integer.parseInt(pref_app.getString("pref_home_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_home_count", "200"));
				paging.setCount(pref_home_count);
				final String screenName = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "").replace("#home", "").replace("#h", "");
				return adapter.getTwitter(adapter.checkIndexFromScreenname(screenName), false).getHomeTimeline(paging);
			} else if (tag.replace("(s)", "").equals("mention")) {
				final int pref_mention_count = isMobile ? Integer.parseInt(pref_app.getString("pref_mention_count_mobile", "20")) : Integer.parseInt(pref_app.getString("pref_mention_count", "20"));
				paging.setCount(pref_mention_count);
				final String screenName = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "").replace("#mention", "").replace("#m", "");
				return adapter.getTwitter(adapter.checkIndexFromScreenname(screenName), false).getMentionsTimeline(paging);
			} else if (tag.replace("(s)", "").equals("search")) {
				final Query q = new Query();
				final String pref_search_lang_searchtl = pref_app.getString("pref_search_lang_searchtl", "");
				if (pref_search_lang_searchtl.equals("") == false) {
					q.setLang(pref_search_lang_searchtl);
				}
				final String query = adapter.getQuery(uriString.replace("(s)", ""));
				q.setQuery(query);
				final int pref_search_count = isMobile ? Integer.parseInt(pref_app.getString("pref_search_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_search_count", "200"));
				q.setCount(pref_search_count);
				if (sinceId > 0) {
					q.setSinceId(sinceId);
				} else if (maxId > -1) {
					q.setMaxId(maxId);
				}
				return adapter.getTwitter(adapter.checkIndexFromPrefTwtr(), false).search(q).getTweets();
			} else if (tag.replace("(s)", "").equals("user")) {
				final int pref_user_count = isMobile ? Integer.parseInt(pref_app.getString("pref_user_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_user_count", "200"));
				paging.setCount(pref_user_count);
				final String screenName = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "");
				return adapter.getTwitter(adapter.checkIndexFromScreenname(screenName), false).getUserTimeline(screenName, paging);
			} else if (tag.replace("(s)", "").equals("userfav")) {
				final int pref_userfav_count = isMobile ? Integer.parseInt(pref_app.getString("pref_userfav_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_userfav_count", "200"));
				paging.setCount(pref_userfav_count);
				final String screenName = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "").replace("#favorite", "").replace("#f", "");
				return adapter.getTwitter(adapter.checkIndexFromScreenname(screenName), false).getFavorites(screenName, paging);
			} else if (tag.replace("(s)", "").equals("userlist")) {
				final int pref_userlist_count =
						isMobile ? Integer.parseInt(pref_app.getString("pref_userlist_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_userlist_count", "200"));
				paging.setCount(pref_userlist_count);
				final String uriStringReplaced = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "");
				final String[] screenNameAndListName = uriStringReplaced.split("/lists/");
				WriteLog.write(Tl.this, "uriString.replace(\"(s)\", \"\").replace(ListAdapter.TWITTER_BASE_URI, \"\"): " + uriStringReplaced);
				if (screenNameAndListName.length == 2) {
					if (screenNameAndListName[0].equals("")) {
						final int index = adapter.checkIndexFromPrefTwtr();
						WriteLog.write(Tl.this, "adapter.checkScreennameFromIndex(index): " + adapter.checkScreennameFromIndex(index) + " screenNameAndListName[1]: " + screenNameAndListName[1]);
						return adapter.getTwitter(index, false).getUserListStatuses(adapter.checkScreennameFromIndex(index), screenNameAndListName[1], paging);
					} else {
						WriteLog.write(Tl.this, "screenNameAndListName[0]: " + screenNameAndListName[0] + " screenNameAndListName[1]: " + screenNameAndListName[1]);
						return adapter.getTwitter(adapter.checkIndexFromScreenname(screenNameAndListName[0]), false).getUserListStatuses(screenNameAndListName[0], screenNameAndListName[1], paging);
					}
				} else {
					final String[] screenNameAndListName2 = uriStringReplaced.split("/");
					if (screenNameAndListName2.length == 2) {
						WriteLog.write(Tl.this, "screenNameAndListName2[0]: " + screenNameAndListName2[0] + " screenNameAndListName2[1]: " + screenNameAndListName2[1]);
						if (screenNameAndListName[0].equals("")) {
							final int index = adapter.checkIndexFromPrefTwtr();
							return adapter.getTwitter(index, false).getUserListStatuses(adapter.checkScreennameFromIndex(index), screenNameAndListName2[1], paging);
						} else {
							return adapter.getTwitter(adapter.checkIndexFromScreenname(screenNameAndListName2[0]), false).getUserListStatuses(screenNameAndListName2[0], screenNameAndListName2[1], paging);
						}
					}
				}
			} else if (( uriString.startsWith("https://twitter.com") ) && ( uriString.contains("/status/") )) {
				final String[] screenNameAndId = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "").split("/status/");
				final List<Status> statuses = new ArrayList<Status>(200);
				final int index = adapter.checkIndexFromScreenname(screenNameAndId[0]);
				Status status = adapter.getTwitter(index, false).showStatus(Long.parseLong(screenNameAndId[1]));
				while (status != null) {
					statuses.add(status);
					if (( status.getInReplyToStatusId() ) > 0) {
						try {
							status = adapter.getTwitter(index, false).showStatus(status.getInReplyToStatusId());
						} catch (final TwitterException e) {
							status = null;
							WriteLog.write(Tl.this, e);
						} catch (final Exception e) {
							status = null;
							WriteLog.write(Tl.this, e);
						}
					} else {
						break;
					}
				}
				return statuses;
			}
			final int pref_home_count = isMobile ? Integer.parseInt(pref_app.getString("pref_home_count_mobile", "50")) : Integer.parseInt(pref_app.getString("pref_home_count", "200"));
			paging.setCount(pref_home_count);
			return adapter.getTwitter(adapter.checkIndexFromPrefTwtr(), false).getHomeTimeline(paging);
		} catch (final NumberFormatException e) {
			WriteLog.write(Tl.this, e);
		} catch (final TwitterException e) {
			WriteLog.write(Tl.this, e);
		}
		return null;
	}

	private final float getPrefFloat(final String key, final String defaultValueString) {
		try {
			return Float.parseFloat(pref_app.getString(key, defaultValueString));
		} catch (final Exception e) {
			return Float.parseFloat(defaultValueString);
		}
	}

	private final int getPrefInt(final String key, final String defaultValueString) {
		try {
			return Integer.parseInt(pref_app.getString(key, defaultValueString));
		} catch (final Exception e) {
			return Integer.parseInt(defaultValueString);
		}
	}

	private final long getTlMaxId() {
		return TlMaxId;
	}

	private final long getTlSinceId() {
		return TlSinceId;
	}

	private final String intent2UriString(final Intent intent) {
		try {
			return ( intent.getData() ).toString();
		} catch (final Exception e) {
			return "";
		}
	}

	@Override
	public final void onConnect() {
		updateStatuses(uriString, getTlMaxId() + 1, -1);
	}

	@Override
	public final void onCreate(final Bundle bundle) {
		super.onCreate(bundle);
		final Intent intent1 = getIntent();

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
		}

		crpKey = getCrpKey();

		uriString = intent2UriString(intent1).replace("http://", "https://");
		final String TAG = StringUtil.uriStringToTag(uriString, false);

		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		//		pref_enable_log = pref_app.getBoolean("pref_enable_log", false);

		playRingtone(TAG);

		pref_enable_tts = pref_app.getBoolean("pref_enable_tts", false);
		pref_tts_set_pitch = Float.parseFloat(pref_app.getString("pref_tts_set_pitch", "1.0f"));
		pref_tts_set_rate = Float.parseFloat(pref_app.getString("pref_tts_set_rate", "1.0f"));

		if (pref_enable_tts) {
			if (tts == null) {
				tts = new TextToSpeech(this, this);
				if (tts.setPitch(pref_tts_set_pitch) == TextToSpeech.ERROR) {
					adapter.toast(getString(R.string.tts_error_set_pitch));
				}
				if (tts.setSpeechRate(pref_tts_set_rate) == TextToSpeech.ERROR) {
					adapter.toast(getString(R.string.tts_error_set_rate));
				}
			}
		}

		setRequestedOrientation();

		// リストビューの生成
		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		final float dpi = metrics.density; // DPIの取得
		final boolean pref_enable_singleline = pref_app.getBoolean("pref_enable_singleline", false);
		final int pref_header_height = getPrefInt("pref_header_height", "-1");
		final float pref_tl_fontsize = getPrefFloat("pref_tl_fontsize", "14");
		final int pref_tl_iconsize1 = (int) ( pref_tl_fontsize * dpi * ( ( pref_enable_singleline ) ? 2.0f : ( 4.0f * Float.parseFloat(pref_app.getString("pref_tl_iconsize", "1")) ) ) ); // pref_enable_singleline pref_tl_fontsize

		final String pref_header_bgcolor = pref_app.getString("pref_header_bgcolor", "#000000");
		final String pref_tl_bgcolor_buttons = pref_app.getString("pref_tl_bgcolor_buttons", "#000000");
		final String pref_tl_fontcolor_buttons = pref_app.getString("pref_tl_fontcolor_buttons", "#ffffff");
		final TlViewLayoutUtil tlViewLayoutUtil =
				new TlViewLayoutUtil(pref_enable_singleline, pref_header_height, pref_tl_fontsize, pref_tl_iconsize1, pref_header_bgcolor, pref_tl_bgcolor_buttons, pref_tl_fontcolor_buttons);
		final LinearLayout linearLayout1 = tlViewLayoutUtil.getTlViewLayout1(this);
		final LinearLayout linearLayout2 = tlViewLayoutUtil.getTlViewLayout2(this);
		final boolean pref_enable_fastscroll = pref_app.getBoolean("pref_enable_fastscroll", false);
		listView = new ListView(this);
		listView.setLayoutParams(new LinearLayout.LayoutParams(MP, WC));
		listView.addHeaderView(linearLayout1, null, false);
		listView.addFooterView(linearLayout2, null, false);
		listView.setFastScrollEnabled(pref_enable_fastscroll);
		final String pref_listview_bgcolor = pref_app.getString("pref_listview_bgcolor", "#000000");
		if (pref_listview_bgcolor.equals("") == false) {
			try {
				listView.setBackgroundColor(Color.parseColor(pref_listview_bgcolor));
			} catch (IllegalArgumentException e) {
			}
		}
		//		final TextView textView1 = (TextView) linearLayout1.findViewWithTag(TlViewLayoutUtil.ID_TEXTVIEW);
		//		final Button button1 = (Button) linearLayout1.findViewWithTag(TlViewLayoutUtil.ID_BUTTON1);
		//		final Button button2 = (Button) linearLayout2.findViewWithTag(TlViewLayoutUtil.ID_BUTTON2);
		//		final Button button3 = (Button) linearLayout1.findViewWithTag(TlViewLayoutUtil.ID_BUTTON3);
		//		final Button button4 = (Button) linearLayout2.findViewWithTag(TlViewLayoutUtil.ID_BUTTON4);
		//		final Button button5 = (Button) linearLayout1.findViewWithTag(TlViewLayoutUtil.ID_BUTTON5);
		//		final Button button6 = (Button) linearLayout2.findViewWithTag(TlViewLayoutUtil.ID_BUTTON6);
		final TextView textView1 = (TextView) linearLayout1.findViewById(TlViewLayoutUtil.ID_TEXTVIEW);
		final Button button1 = (Button) linearLayout1.findViewById(TlViewLayoutUtil.ID_BUTTON1);
		final Button button2 = (Button) linearLayout2.findViewById(TlViewLayoutUtil.ID_BUTTON2);
		final Button button3 = (Button) linearLayout1.findViewById(TlViewLayoutUtil.ID_BUTTON3);
		final Button button4 = (Button) linearLayout2.findViewById(TlViewLayoutUtil.ID_BUTTON4);
		final Button button5 = (Button) linearLayout1.findViewById(TlViewLayoutUtil.ID_BUTTON5);
		final Button button6 = (Button) linearLayout2.findViewById(TlViewLayoutUtil.ID_BUTTON6);

		textView1.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.setInfo(uriString);
			}
		});
		button1.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.setMaxId(ListAdapter.getSha1(uriString), 0);
				updateStatuses(uriString, getTlMaxId() + 1, -1);
			}
		});
		button2.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.setMaxIdKey(ListAdapter.getSha1(uriString));
				updateStatuses(uriString, -1, getTlSinceId() - 1);
			}
		});
		button3.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.listViewSetSelection(listView.getCount() - 2, true);
			}
		});
		button4.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.listViewSetSelection(1, true);
			}
		});
		button5.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.setPosUnread(listView);
			}
		});
		button6.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				adapter.setPosUnread(listView);
			}
		});

		setContentView(listView);
		// リストビューの生成

		adapter = new ListAdapter(this, crpKey, listView, null);
		listView.setAdapter(adapter);

		final boolean pref_showIcon = pref_app.getBoolean("pref_showIcon", false);
		final boolean pref_showIconWear = pref_app.getBoolean("pref_showIconWear", false);
		if (pref_showIcon == true) {
			adapter.notificationShowIcon(pref_showIconWear);
		}

		isMobile = ( ( checkNetworkUtil.autoConnect(false) ) == ( ConnectivityManager.TYPE_MOBILE ) ) ? true : false;
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		final SharedPreferences.Editor editor = pref_app.edit();
		editor.putBoolean("pref_enable_tl_speedy", isMobile);
		editor.commit();
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);

		// トークンの読み込み
		final int user_index_size = Integer.parseInt(pref_app.getString("pref_user_index_size", Integer.toString(ListAdapter.default_user_index_size)));

		try {
			currentIndex = Integer.parseInt(intent1.getStringExtra("index"));
		} catch (final Exception e) {
			currentIndex = adapter.checkIndexFromPrefTwtr();
		}
		if (currentIndex >= user_index_size) {
			currentIndex = 0;
		} else if (0 > currentIndex) {
			return;
		}
		WriteLog.write(this, "index: " + Integer.toString(currentIndex));

		twitterStream = adapter.getTwitterStream(currentIndex, true);
		twitterStream.addListener(mMyUserStreamAdapter);
		twitterStream.addConnectionLifeCycleListener(mMyConnectionLifeCycleListener);

		// ステータスの更新
		adapter.setInfo(uriString);
		adapter.setMaxIdKey(ListAdapter.getSha1(uriString));
		adapter.uriStringHistoryAdd(uriString);
		updateStatuses(uriString, -1, -1);

		if (uriString.endsWith("(s)")) {
			updateStatusesStr(uriString);
		}

		final int pref_tl_interval = Integer.parseInt(pref_app.getString("pref_tl_interval", Integer.toString(ListAdapter.default_pref_tl_interval)));

		final boolean pref_enable_tl_interval = pref_app.getBoolean("pref_enable_tl_interval", true);
		if (pref_enable_tl_interval) {

			runnable = new Runnable() {
				@Override
				public final void run() {
					adapter.setMaxIdKey(ListAdapter.getSha1(uriString));
					updateStatuses(uriString, getTlMaxId() + 1, -1);
					if (tl_repeat) {
						handler.postDelayed(runnable, 1000 * pref_tl_interval);
					} else {
						handler.removeCallbacks(runnable);
					}
				}
			};
			handler.postDelayed(runnable, 1000 * pref_tl_interval);
		}

		final boolean pref_tl_reload_wifistate_changed = pref_app.getBoolean("pref_tl_reload_wifistate_changed", false);
		if (pref_tl_reload_wifistate_changed) {
			if (mConnectionReceiver == null) {
				mConnectionReceiver = new ConnectionReceiver(this);
			}
			final IntentFilter filter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
			try {
				registerReceiver(mConnectionReceiver, filter);
			} catch (final Exception e) {
			}
		}

		adapter.notifyDataSetChanged();
	}

	@Override
	protected final Dialog onCreateDialog(final int id) {
		final Dialog dialog = adapter.createDialog(id);

		if (dialog != null) {
			return dialog;
		} else {
			return super.onCreateDialog(id);
		}
	}

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		adapter.setTlOptionsMenu(menu, true);
		return true;
	}

	@Override
	protected final void onDestroy() {
		final boolean pref_tl_reload_wifistate_changed = pref_app.getBoolean("pref_tl_reload_wifistate_changed", false);
		if (pref_tl_reload_wifistate_changed) {
			if (mConnectionReceiver == null) {
				mConnectionReceiver = new ConnectionReceiver(this);
			}
			try {
				unregisterReceiver(mConnectionReceiver);
			} catch (final Exception e) {
			}
		}

		if (twitterStream != null) {
			try {
				twitterStream.cleanUp();
				twitterStream = null;
			} catch (final Exception e) {
			}
		}
		if (tts != null) {
			enabled_tts = false;
			tts.stop();
			tts.shutdown();
			tts = null;
		}

		super.onDestroy();
	}

	@Override
	public final void onDisconnect() {
		adapter.toast(getString(R.string.disconnected_network));
	}

	@Override
	public final void onInit(final int status) {
		if (status == TextToSpeech.SUCCESS) {
			final Locale locale1 = ListAdapter.LOCALE;
			final Locale locale2 = Locale.ENGLISH;
			if (tts != null) {
				if (tts.isLanguageAvailable(locale1) >= TextToSpeech.LANG_AVAILABLE) {
					if (tts.setLanguage(locale1) >= TextToSpeech.LANG_AVAILABLE) {
						enabled_tts = true;
						adapter.toast("TTS init: success: JAPAN");
					}
				} else if (tts.isLanguageAvailable(locale2) >= TextToSpeech.LANG_AVAILABLE) {
					pref_app = PreferenceManager.getDefaultSharedPreferences(this);
					final boolean pref_enable_tts_english = pref_app.getBoolean("pref_enable_tts_english", false);
					if (( tts.setLanguage(locale2) >= TextToSpeech.LANG_AVAILABLE ) && ( pref_enable_tts_english )) {
						enabled_tts = true;
						adapter.toast("TTS init: success: ENGLISH");
					}
				}
			}
		}
	}

	@Override
	public final boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			final String pref_hardkey_tl_volumeup = pref_app.getString("pref_hardkey_tl_volumeup", "0");
			if (pref_hardkey_tl_volumeup.equals("0")) {
				adapter.setMaxId(StringUtil.uriStringToTag(uriString, false), 0);
				updateStatuses(uriString, getTlMaxId() + 1, -1);
			} else if (pref_hardkey_tl_volumeup.equals("1")) {
				adapter.listViewSetSelection(1, true);
			} else if (pref_hardkey_tl_volumeup.equals("2")) {
				adapter.setPosUnread(listView);
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			final String pref_hardkey_tl_volumedown = pref_app.getString("pref_hardkey_tl_volumedown", "0");
			if (pref_hardkey_tl_volumedown.equals("0")) {
				adapter.setMaxIdKey(StringUtil.uriStringToTag(uriString, false));
				updateStatuses(uriString, -1, getTlSinceId() - 1);
			} else if (pref_hardkey_tl_volumedown.equals("1")) {
				adapter.listViewSetSelection(listView.getCount() - 2, true);
			} else if (pref_hardkey_tl_volumedown.equals("2")) {
				adapter.setPosUnread(listView);
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
			adapter.setPosUnread(listView);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_HOME) {
			adapter.setMaxId(StringUtil.uriStringToTag(uriString, false), 0);
			return super.onKeyDown(keyCode, event);
		} else if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (event.getRepeatCount() > 0) {
				mLongPressed = true;
			}
			return super.onKeyDown(keyCode, event);
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public final boolean onKeyUp(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!mLongPressed) {
				mLongPressed = false;
				adapter.toast(getString(R.string.backbutton_singleclick));
				return false;
			} else {
				adapter.setMaxId(StringUtil.uriStringToTag(uriString, false), 0);
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		pref_enable_tts = pref_app.getBoolean("pref_enable_tts", false);
		if (item.getItemId() == R.string.enter_tl_uri) {
			showEnterTlUriDialog();

		} else if (item.getItemId() == R.string.streaming_restart) {
			if (twitterStream != null) {
				try {
					twitterStream.cleanUp();
				} catch (final Exception e) {
				}
			} else {
				try {
					twitterStream = adapter.getTwitterStream(adapter.checkIndexFromPrefTwtr(), true);
					twitterStream.addListener(mMyUserStreamAdapter);
					twitterStream.addConnectionLifeCycleListener(mMyConnectionLifeCycleListener);
				} catch (final Exception e) {
				}
			}

			// ステータスの更新
			if (uriString.endsWith("(s)")) {
				updateStatusesStr(uriString);
			}

		} else if (item.getItemId() == R.string.streaming_cleanup) {
			if (twitterStream != null) {
				try {
					twitterStream.cleanUp();
				} catch (final Exception e) {
				}
			}

		} else if (item.getItemId() == R.string.load_tl_up) {
			adapter.setMaxId(StringUtil.uriStringToTag(uriString, false), 0);
			updateStatuses(uriString, getTlMaxId() + 1, -1);

		} else if (item.getItemId() == R.string.load_tl_down) {
			adapter.setMaxIdKey(StringUtil.uriStringToTag(uriString, false));
			updateStatuses(uriString, -1, getTlSinceId() - 1);

		} else if (item.getItemId() == R.string.updatetweet_lite) {
			adapter.showUpdateTweetLiteDialog(-1);

		} else if (item.getItemId() == R.string.move_tl_up) {
			adapter.listViewSetSelection(1, true);

		} else if (item.getItemId() == R.string.move_tl_unread) {
			adapter.setPosUnread(listView);

		} else if (item.getItemId() == R.string.move_tl_down) {
			adapter.listViewSetSelection(listView.getCount() - 2, true);

		} else if (item.getItemId() == R.string.search) {
			adapter.searchTweet(listView);

		} else if (item.getItemId() == R.string.tl_repeat_on) {
			tl_repeat = true;

		} else if (item.getItemId() == R.string.tl_repeat_off) {
			tl_repeat = false;

		} else if (item.getItemId() == R.string.tl_speedy_on) {
			final SharedPreferences.Editor editor1 = pref_app.edit();
			editor1.putBoolean("pref_enable_tl_speedy", true);
			editor1.commit();

		} else if (item.getItemId() == R.string.tl_speedy_off) {
			final SharedPreferences.Editor editor2 = pref_app.edit();
			editor2.putBoolean("pref_enable_tl_speedy", false);
			editor2.commit();

		} else if (item.getItemId() == R.string.make_shortcut) {
			final String[] ITEM = adapter.getOurScreenNames("@", "");
			new AlertDialog.Builder(Tl.this).setTitle(R.string.select_screenname).setItems(ITEM, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int index) {
					adapter.makeShortcutTl(ITEM[index], uriString, false);
					adapter.toast(getString(R.string.done_make_shortcut1) + ITEM[index] + ":" + uriString.replace(ListAdapter.TWITTER_BASE_URI, "") + getString(R.string.done_make_shortcut2));
				}
			}).create().show();

		} else if (item.getItemId() == R.string.check_ratelimit) {
			adapter.showRateLimits();

		} else if (item.getItemId() == R.string.check_apistatus) {
			adapter.showApiStatuses();

		} else if (item.getItemId() == R.string.deljustbefore) {
			adapter.deljustbefore(-1);

		} else if (item.getItemId() == R.string.settings) {
			final Intent intent2 = new Intent();
			intent2.setClassName("jp.gr.java_conf.ya.shiobeforandroid2", "jp.gr.java_conf.ya.shiobeforandroid2.Preference");
			startActivity(intent2);

		} else if (item.getItemId() == R.string.copyright) {
			try {
				final PackageInfo packageInfo = getPackageManager().getPackageInfo("jp.gr.java_conf.ya.shiobeforandroid2", PackageManager.GET_META_DATA);
				adapter.toast(getString(R.string.app_name_short) + ": " + getString(R.string.version) + packageInfo.versionName + " (" + packageInfo.versionCode + ")");
			} catch (NameNotFoundException e) {
			}
			try {
				final Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(ListAdapter.app_uri_about));
				startActivity(intent);
			} catch (final Exception e) {
			}

		}
		return true;
	}

	@Override
	protected final void onPause() {
		tl_repeat = false;
		try {
			dismissDialog(R.string.loading);
		} catch (IllegalArgumentException e) {
		}
		super.onPause();
	}

	@Override
	protected final void onResume() {
		super.onResume();
		tl_repeat = true;
		isMobile = ( ( checkNetworkUtil.autoConnect(false) ) == ( ConnectivityManager.TYPE_MOBILE ) ) ? true : false;
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		final SharedPreferences.Editor editor = pref_app.edit();
		editor.putBoolean("pref_enable_tl_speedy", isMobile);
		editor.commit();
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
	}

	private final void playRingtone(final String TAG) {
		final boolean pref_enable_ringtone_onstart = pref_app.getBoolean("pref_enable_ringtone_onstart", true);
		final String pref_ringtone_onstart_tl = pref_app.getString("pref_ringtone_onstart_" + TAG + "tl", "");
		if (pref_enable_ringtone_onstart && ( pref_ringtone_onstart_tl != null ) && ( pref_ringtone_onstart_tl.equals("") == false )) {
			final MediaPlayer mediaPlayer = MediaPlayer.create(this, Uri.parse(pref_ringtone_onstart_tl));
			mediaPlayer.setLooping(false);
			mediaPlayer.seekTo(0);
			mediaPlayer.start();
		}
	}

	private final void setRequestedOrientation() {
		final int pref_screen_orientation_timeline = Integer.parseInt(pref_app.getString("pref_screen_orientation_timeline", "0"));
		switch (pref_screen_orientation_timeline) {
		default:
			break;
		case 0:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		case 1:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		case 2:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			break;
		case 3:
			switch (getResources().getConfiguration().orientation) {
			case android.content.res.Configuration.ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case android.content.res.Configuration.ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			default:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				break;
			}
		}
	}

	private final void showEnterTlUriDialog() {
		new Thread(new Runnable() {
			@Override
			public final void run() {
				final String[] tlAutoCompleteStringArray = adapter.getTlAutoCompleteStringArray();
				runOnUiThread(new Runnable() {
					@Override
					public final void run() {
						final AutoCompleteTextView autoCompleteTextView = new AutoCompleteTextView(Tl.this);
						autoCompleteTextView.setText(uriString);
						final ArrayAdapter<String> autoCompleteTextViewAdapter = new ArrayAdapter<String>(Tl.this, R.layout.list_item, tlAutoCompleteStringArray);
						autoCompleteTextView.setAdapter(autoCompleteTextViewAdapter);
						new AlertDialog.Builder(Tl.this).setView(autoCompleteTextView).setTitle(R.string.enter_tl_uri).setNegativeButton(R.string.history_previous, new DialogInterface.OnClickListener() {
							@Override
							public final void onClick(final DialogInterface dialog, final int which) {
								updateStatusesPrepare(adapter.uriStringHistoryUndo(), false);
							}
						}).setNeutralButton(R.string.history_next, new DialogInterface.OnClickListener() {
							@Override
							public final void onClick(final DialogInterface dialog, final int which) {
								updateStatusesPrepare(adapter.uriStringHistoryRedo(), false);
							}
						}).setPositiveButton(R.string.load, new DialogInterface.OnClickListener() {
							@Override
							public final void onClick(final DialogInterface dialog, final int which) {
								updateStatusesPrepare(autoCompleteTextView.getText().toString(), true);
							}
						}).create().show();
					}
				});
			}
		}).start();
		return;

	}

	private final void speechText(final String speechString, final String speechVolume, final String speechPan) {
		if (speechString.equals("") == false) {
			if (tts == null) {
				tts = new TextToSpeech(this, this);
			}
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
				final HashMap<String, String> params = new HashMap<String, String>();
				params.put("volume", speechVolume);
				params.put("pan", speechPan);

				if (tts != null) {
					if (tts.isSpeaking()) {
						tts.speak(speechString, TextToSpeech.QUEUE_ADD, params);
					} else {
						tts.speak(speechString, TextToSpeech.QUEUE_FLUSH, params);
					}
				}
			} else {
				if (tts != null) {
					if (tts.isSpeaking()) {
						tts.speak(speechString, TextToSpeech.QUEUE_ADD, null);
					} else {
						tts.speak(speechString, TextToSpeech.QUEUE_FLUSH, null);
					}
				}
			}
		}
	}

	// ステータスの更新
	private final void updateStatuses(final String uriString, final long sinceId, final long maxId) {
		if (!isFinishing()) {
			try {
				dismissDialog(R.string.loading);
			} catch (final Exception e) {
			}
			showDialog(R.string.loading);
		}

		pref_app = PreferenceManager.getDefaultSharedPreferences(this);

		final int index = adapter.checkIndex(currentIndex, true);

		WriteLog.write(this, "updateStatuses() uriString: " + uriString + " sinceId: " + Long.toString(sinceId) + " maxId: " + Long.toString(maxId) + " index: " + Integer.toString(index));

		// ステータスの更新
		new Thread(new Runnable() {
			@Override
			public final void run() {
				try {
					// ステータスの読み込み
					statuses = getListStatus(uriString, sinceId, maxId);

					if (statuses.isEmpty() == false) {
						final int statuses_size = statuses.size();
						TlMaxId = ( ( TlMaxId == -1 ) || ( statuses.get(0).getId() > TlMaxId ) ) ? statuses.get(0).getId() : TlMaxId;
						TlSinceId = ( ( TlSinceId == -1 ) || ( statuses.get(statuses_size - 1).getId() < TlSinceId ) ) ? statuses.get(statuses_size - 1).getId() : TlSinceId;

						if (adapter.getCount() > 0) {
							if (maxId > -1) {
								adapter.setTweets(statuses, 1);
							} else if (sinceId > -1) {
								adapter.setTweets(statuses, 2);
							} else {
								adapter.setTweets(statuses, 0);
							}
						} else {
							adapter.setTweets(statuses, 0);
						}

						adapter.preloadMaps(statuses);
						runOnUiThread(new Runnable() {
							@Override
							public final void run() {
								try {
									adapter.notifyDataSetChanged();
									adapter.setPosition(listView, statuses_size, maxId, sinceId);
								} catch (final Exception e) {
									WriteLog.write(Tl.this, e);
								}
							}
						});
					}
					runOnUiThread(new Runnable() {
						@Override
						public final void run() {
							try {
								adapter.notifyDataSetChanged();
							} catch (final Exception e) {
								WriteLog.write(Tl.this, e);
							}
						}
					});
				} catch (final Exception e) {
					WriteLog.write(Tl.this, e);
				}
				handler.post(new Runnable() {
					@Override
					public final void run() {
						try {
							dismissDialog(R.string.loading);
						} catch (IllegalArgumentException e) {
						}
					}
				});
			}
		}).start();
	}

	private final void updateStatusesPrepare(final String inputedString, final boolean isAdd) {
		if (inputedString.equals("")) {
			updateStatuses(uriString, getTlMaxId() + 1, -1);
		} else if (inputedString.equals(uriString)) {
			updateStatuses(uriString, getTlMaxId() + 1, -1);
		} else {
			uriString = inputedString.replace("http://", "https://");
			adapter.setInfo(uriString);
			if (isAdd) {
				adapter.uriStringHistoryAdd(uriString);
			}
			updateStatuses(uriString, -1, -1);

			if (uriString.endsWith("(s)")) {
				updateStatusesStr(uriString);
			}
		}
	}

	private final void updateStatusesStr(final String uriString) {
		WriteLog.write(this, "updateStatusesStr() uriString: " + uriString);

		try {
			final String tag = StringUtil.uriStringToTag(uriString, true);
			WriteLog.write(this, "updateStatusesStr() uriString: " + uriString + " tag: " + tag);

			if (( uriString.equals("https://twitter.com(s)") ) || ( uriString.equals(ListAdapter.TWITTER_BASE_URI + "(s)") ) || ( tag.equals("home(s)") )) {
				try {
					twitterStream.user();
				} catch (final Exception e) {
					WriteLog.write(this, e);
				}
			} else if (tag.equals("mention(s)")) {
				final FilterQuery query = new FilterQuery();
				query.track(adapter.getOurScreenNames("@", ""));
				twitterStream.filter(query);
			} else if (tag.equals("search(s)")) {
				final FilterQuery query = new FilterQuery();
				final String pref_search_lang_searchtl = pref_app.getString("pref_search_lang_searchtl", "");
				if (pref_search_lang_searchtl.equals("") == false) {
					query.language(pref_search_lang_searchtl.split(","));
				}
				WriteLog.write(this, "updateStatusesStr() uriString: " + uriString + " tag: " + tag + " getQuery:" + adapter.getQuery(uriString.replace("(s)", "")));
				final String[] track = { adapter.getQuery(uriString.replace("(s)", "")) };
				query.track(track);
				twitterStream.filter(query);
			} else if (tag.equals("user(s)")) {
				final FilterQuery query = new FilterQuery();
				final String[] track = { uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "") };
				query.track(track);
				twitterStream.filter(query);
			} else if (tag.equals("userlist(s)")) {
				final Boolean pref_streaminguserlisttl_track_follow = pref_app.getBoolean("pref_streaminguserlisttl_track_follow", true);

				final String uriStringReplaced = uriString.replace("(s)", "").replace(ListAdapter.TWITTER_BASE_URI, "");
				String[] screenNameAndListName = uriStringReplaced.split("/lists/");
				if (screenNameAndListName.length == 2) {
					if (screenNameAndListName[0].equals("")) {
						screenNameAndListName[0] = adapter.checkScreennameFromIndex(adapter.checkIndexFromPrefTwtr());
					}
				} else {
					screenNameAndListName = uriStringReplaced.split("/");
					if (screenNameAndListName.length == 2) {
						if (screenNameAndListName[0].equals("")) {
							screenNameAndListName[0] = adapter.checkScreennameFromIndex(adapter.checkIndexFromPrefTwtr());
						}
					}
				}

				final FilterQuery query = new FilterQuery();
				if (pref_streaminguserlisttl_track_follow) {
					query.track(adapter.getListMemberString(screenNameAndListName[0], screenNameAndListName[1]));
				} else {
					query.follow(adapter.getListMemberLong(screenNameAndListName[0], screenNameAndListName[1]));
				}
				twitterStream.filter(query);
				twitterStream.user();
			}
		} catch (final NumberFormatException e) {
			WriteLog.write(Tl.this, e);
		} catch (final Exception e) {
			WriteLog.write(Tl.this, e);
		}
	}
}
