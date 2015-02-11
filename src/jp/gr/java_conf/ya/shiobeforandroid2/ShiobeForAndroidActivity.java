package jp.gr.java_conf.ya.shiobeforandroid2; // Copyright (c) 2013-2015 YA <ya.androidapp@gmail.com> All rights reserved.

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import jp.gr.java_conf.ya.shiobeforandroid2.util.CheckNetworkUtil;
import jp.gr.java_conf.ya.shiobeforandroid2.util.HttpsClient;
import jp.gr.java_conf.ya.shiobeforandroid2.util.MyCrypt;
import jp.gr.java_conf.ya.shiobeforandroid2.util.StringUtil;
import jp.gr.java_conf.ya.shiobeforandroid2.util.WriteLog;
import twitter4j.PagableResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

public class ShiobeForAndroidActivity extends Activity {
	private final class FileCheck_font implements FilenameFilter {
		@Override
		public final boolean accept(final File dir, final String strfilename) {
			if (( strfilename.endsWith(".ttf") ) || ( strfilename.endsWith(".zip") )) {
				return true;
			} else {
				return false;
			}
		}
	}

	private final class FileCheckPrefXml implements FilenameFilter {
		@Override
		public final boolean accept(final File dir, final String strfilename) {
			if (strfilename.contains(getPackageName()) && ( strfilename.endsWith(".xml") || strfilename.endsWith(".xml.bin") )) {
				return true;
			} else {
				return false;
			}
		}
	}

	private ListAdapter adapter;

	private boolean timeout = true;

	private final CheckNetworkUtil checkNetworkUtil = new CheckNetworkUtil(this);

	private int pref_timeout_connection = 0;
	private int pref_timeout_so = 0;
	private int pref_timeout_t4j_connection;
	private int pref_timeout_t4j_read;

	private OAuthAuthorization oAuthAuthorization;

	private RequestToken requestToken;

	private SharedPreferences pref_app;
	private SharedPreferences pref_twtr;

	private static final String CALLBACK_URL = "myapp://oauth";
	private static String consumerKey = "";
	private static String consumerSecret = "";
	private String crpKey = "";
	private String Status;

	private TextView textView1;

	private Twitter twitter;

	private WebView webView1;

	private final void connectTwitter() throws TwitterException {
		if (checkNetworkUtil.isConnected() == false) {
			adapter.toast(getString(R.string.cannot_access_internet));
			return;
		}

		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		final String pref_consumerKey = pref_app.getString("pref_consumerkey", "");
		final String pref_consumerSecret = pref_app.getString("pref_consumersecret", "");
		final Boolean pref_enable_consumerKey = pref_app.getBoolean("pref_enable_consumerkey", false);
		if (( pref_enable_consumerKey == true ) && ( pref_consumerKey.equals("") == false ) && ( pref_consumerSecret.equals("") == false )) {
			consumerKey = pref_consumerKey;
			consumerSecret = pref_consumerSecret;
		} else {
			consumerKey = getString(R.string.default_consumerKey);
			consumerSecret = getString(R.string.default_consumerSecret);
		}
		if (pref_consumerKey.equals("") || pref_consumerSecret.equals("")) {
			adapter.toast(getString(R.string.consumerkey_orand_secret_is_empty));

			pref_app = PreferenceManager.getDefaultSharedPreferences(this);
			final EditText editText = new EditText(this);
			new AlertDialog.Builder(this).setTitle(R.string.consumerkey_orand_secret_is_empty).setMessage(R.string.enter_consumerkey_and_secret_ssv).setView(editText).setCancelable(true).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					return;
				}
			}).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					final SharedPreferences.Editor editor = pref_app.edit();
					editor.putBoolean("pref_enable_consumerkey", true);
					editor.putString("pref_consumerkey", ( editText.getText().toString() ).split(" ")[0]);
					editor.putString("pref_consumersecret", ( editText.getText().toString() ).split(" ")[1]);
					editor.commit();

					try {
						connectTwitter();
					} catch (TwitterException e) {
						WriteLog.write(ShiobeForAndroidActivity.this, e);
					}
				}
			}).create().show();
			return;
		}
		try {
			pref_timeout_t4j_connection = Integer.parseInt(pref_app.getString("pref_timeout_t4j_connection", "20000"));
		} catch (final Exception e) {
			pref_timeout_t4j_connection = 20000;
		}
		try {
			pref_timeout_t4j_read = Integer.parseInt(pref_app.getString("pref_timeout_t4j_read", "120000"));
		} catch (final Exception e) {
			pref_timeout_t4j_read = 120000;
		}

		final String pref_twitterlogin_mode = pref_app.getString("pref_twitterlogin_mode", "0");
		WriteLog.write(this, "pref_twitterlogin_mode: " + pref_twitterlogin_mode);

		if (pref_twitterlogin_mode.equals("1")) {
			final ConfigurationBuilder confbuilder = new ConfigurationBuilder();
			confbuilder.setOAuthConsumerKey(consumerKey);
			confbuilder.setOAuthConsumerSecret(consumerSecret);
			final Configuration conf = confbuilder.build();
			oAuthAuthorization = new OAuthAuthorization(conf);
			oAuthAuthorization.setOAuthAccessToken(null);
			String authUrl = null;
			try {
				authUrl = oAuthAuthorization.getOAuthRequestToken(CALLBACK_URL).getAuthorizationURL();
			} catch (final Exception e) {
				return;
			}
			final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
			startActivity(intent);
		} else if (pref_twitterlogin_mode.equals("2")) {
			final ConfigurationBuilder confbuilder = new ConfigurationBuilder();
			confbuilder.setOAuthConsumerKey(consumerKey).setOAuthConsumerSecret(consumerSecret).setHttpConnectionTimeout(pref_timeout_t4j_connection).setHttpReadTimeout(pref_timeout_t4j_read);// .setUseSSL(true);
			twitter = new TwitterFactory(confbuilder.build()).getInstance();
			try {
				requestToken = twitter.getOAuthRequestToken(CALLBACK_URL);
			} catch (final TwitterException e) {
				WriteLog.write(this, e);
			} catch (final Exception e) {
				WriteLog.write(this, e);
			}
			String authorizationUrl = "";
			try {
				authorizationUrl = requestToken.getAuthorizationURL();
			} catch (final Exception e) {
			}
			if (authorizationUrl.equals("") == false) {
				final Intent intent = new Intent(this, TwitterLoginPin.class);
				intent.putExtra("auth_url", authorizationUrl);
				intent.putExtra("consumer_key", consumerKey);
				intent.putExtra("consumer_secret", consumerSecret);
				startActivityForResult(intent, 0);
			}
		} else {
			ConfigurationBuilder confbuilder = new ConfigurationBuilder();
			confbuilder.setOAuthConsumerKey(consumerKey).setOAuthConsumerSecret(consumerSecret).setHttpConnectionTimeout(pref_timeout_t4j_connection).setHttpReadTimeout(pref_timeout_t4j_read).setHttpRetryCount(3).setHttpRetryIntervalSeconds(10);// .setUseSSL(true);
			twitter = new TwitterFactory(confbuilder.build()).getInstance();
			try {
				requestToken = twitter.getOAuthRequestToken(CALLBACK_URL);
			} catch (final TwitterException e) {
				WriteLog.write(this, e);
			} catch (final Exception e) {
				WriteLog.write(this, e);
			}
			String authorizationUrl = "";
			try {
				authorizationUrl = requestToken.getAuthorizationURL();
			} catch (final Exception e) {
				WriteLog.write(this, e);
			}
			if (authorizationUrl.equals("") == false) {
				final Intent intent = new Intent(this, TwitterLogin.class);
				intent.putExtra("auth_url", authorizationUrl);
				this.startActivityForResult(intent, 0);
			}
		}
	}

	private final boolean copyFile(final String srcFilePath, final String dstFilePath) {
		final File srcFile = new File(srcFilePath);
		WriteLog.write(this, "copyFile() srcFilePath: " + srcFilePath);
		final File dstFile = new File(dstFilePath);
		WriteLog.write(this, "copyFile() dstFilePath: " + dstFilePath);

		try {
			new File(( dstFile.getParentFile() ).getPath()).mkdirs();
		} catch (final Exception e) {
			WriteLog.write(this, "copyFile() mkdirs() Exception: " + e.toString());
		}

		InputStream input;
		OutputStream output = null;
		try {
			input = new FileInputStream(srcFile);
		} catch (FileNotFoundException e) {
			input = null;
			WriteLog.write(this, "copyFile() input FileNotFoundException: " + e.toString());
		}
		try {
			output = new FileOutputStream(dstFile);
		} catch (final FileNotFoundException e) {
			try {
				input.close();
			} catch (final IOException e1) {
			}
			input = null;
			WriteLog.write(this, "copyFile() output FileNotFoundException: " + e.toString());
			return false;
		}

		final int DEFAULT_BUFFER_SIZE = 1024 * 4;
		final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int n = 0;
		try {
			while (-1 != ( n = input.read(buffer) )) {
				output.write(buffer, 0, n);
			}
			input.close();
			output.flush();
			output.close();
		} catch (final IOException e) {
			WriteLog.write(this, "copyFile() output.write() IOException: " + e.toString());
			return false;
		} catch (final Exception e) {
			WriteLog.write(this, "copyFile() output.write() Exception: " + e.toString());
			return false;
		}
		return true;
	}

	private final void disconnectTwitter() {
		pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
		final int index = Integer.parseInt(pref_twtr.getString("index", "-1"));

		if (index > -1) {
			final SharedPreferences.Editor editor = pref_twtr.edit();
			editor.remove("consumer_key_" + Integer.toString(index));
			editor.remove("consumer_secret_" + Integer.toString(index));
			editor.remove("oauth_token_" + Integer.toString(index));
			editor.remove("oauth_token_secret_" + Integer.toString(index));
			editor.remove("profile_image_url_" + Integer.toString(index));
			editor.remove("screen_name_" + Integer.toString(index));
			editor.remove("status_" + Integer.toString(index));
			editor.commit();
			// finish();
		}

		WriteLog.write(this, "disconnected.");
	}

	private final void download(final String apkurl) {
		if (checkNetworkUtil.isConnected() == false) {
			adapter.toast(getString(R.string.cannot_access_internet));
			return;
		}

		HttpURLConnection c;
		try {
			final URL url = new URL(apkurl);
			c = (HttpURLConnection) url.openConnection();
			c.setRequestMethod("GET");
			c.connect();
		} catch (final MalformedURLException e) {
			c = null;
		} catch (final IOException e) {
			c = null;
		}
		try {
			final String PATH = Environment.getExternalStorageDirectory() + "/";
			final File file = new File(PATH);
			file.mkdirs();
			final File outputFile = new File(file, "ShiobeForAndroid.apk");
			final FileOutputStream fos = new FileOutputStream(outputFile);
			final InputStream is = c.getInputStream();
			final byte[] buffer = new byte[1024];
			int len = 0;
			while (( len = is.read(buffer) ) != -1) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			is.close();

			final Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(new File(PATH + "ShiobeForAndroid.apk")), "application/vnd.android.package-archive");
			startActivity(intent);
		} catch (final MalformedURLException e) {
		} catch (final IOException e) {
		}
	}

	private final boolean isConnected(final String shiobeStatus) {
		if (( shiobeStatus != null ) && shiobeStatus.equals("available")) {
			return true;
		} else {
			return false;
		}
	}

	private final void makeShortcuts() {
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		final String pref_shiobeforandroidactivity_make_shortcut_urls = pref_app.getString("pref_shiobeforandroidactivity_make_shortcut_urls", "");

		final String[] urls = pref_shiobeforandroidactivity_make_shortcut_urls.split(",");
		for (final String url : urls) {
			if (url.startsWith(ListAdapter.TWITTER_BASE_URI)) {
				adapter.makeShortcutTl("", url, url.toLowerCase(ListAdapter.LOCALE).endsWith("(s)"));
			} else {
				adapter.makeShortcutUri(url);
			}
		}
	}

	@Override
	protected final void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (resultCode == RESULT_OK) {
			if (requestCode == 0) {
				if (checkNetworkUtil.isConnected() == false) {
					adapter.toast(getString(R.string.cannot_access_internet));
					return;
				}

				try {
					pref_app = PreferenceManager.getDefaultSharedPreferences(this);
					final String pref_consumerKey = pref_app.getString("pref_consumerkey", "");
					final String pref_consumerSecret = pref_app.getString("pref_consumersecret", "");
					final Boolean pref_enable_consumerKey = pref_app.getBoolean("pref_enable_consumerkey", false);
					if (( pref_enable_consumerKey == true ) && ( pref_consumerKey.equals("") == false ) && ( pref_consumerSecret.equals("") == false )) {
						consumerKey = pref_consumerKey;
						consumerSecret = pref_consumerSecret;
					} else {
						consumerKey = getString(R.string.default_consumerKey);
						consumerSecret = getString(R.string.default_consumerSecret);
					}

					final AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, intent.getExtras().getString("oauth_verifier"));
					final ConfigurationBuilder confbuilder = new ConfigurationBuilder();
					confbuilder.setOAuthAccessToken(accessToken.getToken()).setOAuthAccessTokenSecret(accessToken.getTokenSecret()).setOAuthConsumerKey(consumerKey).setOAuthConsumerSecret(consumerSecret).setHttpConnectionTimeout(pref_timeout_t4j_connection).setHttpReadTimeout(pref_timeout_t4j_read).setHttpRetryCount(3).setHttpRetryIntervalSeconds(10);// .setUseSSL(true);
					twitter = new TwitterFactory(confbuilder.build()).getInstance();
					final User user = twitter.showUser(twitter.getScreenName());
					final String profile_image_url = user.getProfileImageURL().toString();

					pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
					final int index = Integer.parseInt(pref_twtr.getString("index", "-1"));

					if (index > -1) {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("consumer_key_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, consumerKey));
						editor.putString("consumer_secret_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, consumerSecret));
						editor.putString("oauth_token_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, accessToken.getToken()));
						editor.putString("oauth_token_secret_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, accessToken.getTokenSecret()));
						editor.putString("status_" + Integer.toString(index), "available");
						editor.putString("screen_name_" + Integer.toString(index), twitter.getScreenName());
						editor.putString("profile_image_url_" + Integer.toString(index), profile_image_url);
						editor.commit();
						Intent intent2 = new Intent(this, UpdateTweet.class);
						this.startActivityForResult(intent2, 0);
					}
				} catch (final TwitterException e) {
				}
			}
		}
	}

	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
		}

		crpKey = getString(R.string.app_name);
		final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		crpKey += telephonyManager.getDeviceId();
		crpKey += telephonyManager.getSimSerialNumber();
		try {
			final PackageInfo packageInfo = getPackageManager().getPackageInfo("jp.gr.java_conf.ya.shiobeforandroid2", PackageManager.GET_META_DATA);
			crpKey += Long.toString(packageInfo.firstInstallTime);
		} catch (final NameNotFoundException e) {
		}

		adapter = new ListAdapter(this, crpKey, null, null);

		pref_app = PreferenceManager.getDefaultSharedPreferences(this);

		final boolean pref_enable_ringtone_onstart = pref_app.getBoolean("pref_enable_ringtone_onstart", true);
		final String pref_ringtone_onstart_shiobeforandroidactivity = pref_app.getString("pref_ringtone_onstart_shiobeforandroidactivity", "");
		if (pref_enable_ringtone_onstart && ( pref_ringtone_onstart_shiobeforandroidactivity != null ) && ( pref_ringtone_onstart_shiobeforandroidactivity.equals("") == false )) {
			final MediaPlayer mediaPlayer = MediaPlayer.create(this, Uri.parse(pref_ringtone_onstart_shiobeforandroidactivity));
			mediaPlayer.setLooping(false);
			mediaPlayer.seekTo(0);
			mediaPlayer.start();
		}

		try {
			pref_timeout_connection = Integer.parseInt(pref_app.getString("pref_timeout_connection", Integer.toString(ListAdapter.default_timeout_connection)));
		} catch (final Exception e) {
			pref_timeout_connection = ListAdapter.default_timeout_connection;
		}
		try {
			pref_timeout_so = Integer.parseInt(pref_app.getString("pref_timeout_so", Integer.toString(ListAdapter.default_timeout_so)));
		} catch (final Exception e) {
			pref_timeout_so = ListAdapter.default_timeout_connection;
		}
		try {
			pref_timeout_t4j_connection = Integer.parseInt(pref_app.getString("pref_timeout_t4j_connection", "20000"));
		} catch (final Exception e) {
			pref_timeout_t4j_connection = 20000;
		}
		try {
			pref_timeout_t4j_read = Integer.parseInt(pref_app.getString("pref_timeout_t4j_read", "120000"));
		} catch (final Exception e) {
			pref_timeout_t4j_read = 120000;
		}

		if (checkNetworkUtil.isConnected() == false) {
			adapter.toast(getString(R.string.cannot_access_internet));
		} else {
			final Boolean pref_enable_update_check = pref_app.getBoolean("pref_enable_update_check", false);
			if (pref_enable_update_check) {
				adapter.toast(getString(R.string.update_check_developer_ver_only));

				String pref_update_check_url = pref_app.getString("pref_update_check_url", ListAdapter.default_update_check_url);
				pref_update_check_url += ( pref_update_check_url.endsWith("/") ) ? "" : "/";
				final String updateVerStr = HttpsClient.https2data(this, pref_update_check_url + "index.php?mode=updatecheck", pref_timeout_connection, pref_timeout_so, "UTF-8");
				if (updateVerStr.equals("") == false) {
					long updateVer = 0;
					try {
						updateVer = Long.parseLong(updateVerStr);
					} catch (final NumberFormatException e) {
					}
					try {
						final PackageInfo packageInfo = getPackageManager().getPackageInfo("jp.gr.java_conf.ya.shiobeforandroid2", PackageManager.GET_META_DATA);
						if (updateVer > ( packageInfo.lastUpdateTime / 1000L )) {
							final File deleteFile = new File(Environment.getExternalStorageDirectory() + "/ShiobeForAndroid.apk");
							if (deleteFile.exists()) {
								deleteFile.delete();
							}

							download(HttpsClient.https2data(this, pref_update_check_url + "index.php?mode=updateuri", pref_timeout_connection, pref_timeout_so, ListAdapter.default_charset));
						}
					} catch (NameNotFoundException e) {
					}
				}
			}
		}

		setContentView(R.layout.main);

		pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);

		int index = -1;
		try {
			index = Integer.parseInt(pref_twtr.getString("index", "0"));
		} catch (final Exception e) {
			index = 0;
		}
		Status = pref_twtr.getString("status_" + Integer.toString(index), "");

		final long pref_timeout_connection2 = pref_timeout_connection / 5;

		final String pref_useragent = pref_app.getString("pref_useragent", getString(R.string.useragent_ff));

		textView1 = (TextView) this.findViewById(R.id.textView1);
		webView1 = (WebView) this.findViewById(R.id.webView1);

		new Thread(new Runnable() {
			@Override
			public final void run() {

				runOnUiThread(new Runnable() {
					@Override
					public final void run() {
						if (isConnected(Status)) {
							textView1.setText(getString(R.string.welcome));
						} else {
							textView1.setText(getString(R.string.hello));
						}

						if (checkNetworkUtil.isConnected() == false) {
							adapter.toast(getString(R.string.cannot_access_internet));

							new Thread(new Runnable() {
								@Override
								public final void run() {
									runOnUiThread(new Runnable() {
										public final void run() {
											webView1.loadUrl(ListAdapter.app_uri_local);
											webView1.requestFocus(View.FOCUS_DOWN);
										}
									});
								}
							});
						} else {
							webView1.getSettings().setBuiltInZoomControls(true);
							webView1.getSettings().setJavaScriptEnabled(true);
							if (pref_useragent.equals("")) {
								webView1.getSettings().setUserAgentString(getString(R.string.useragent_ff));
							} else {
								webView1.getSettings().setUserAgentString(pref_useragent);
							}
							webView1.setWebViewClient(new WebViewClient() {
								@Override
								public void onPageFinished(WebView view, String url) {
									timeout = false;
								}

								@Override
								public final void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
									new Thread(new Runnable() {
										@Override
										public final void run() {
											try {
												Thread.sleep(pref_timeout_connection2);
											} catch (final InterruptedException e) {
											}
											if (timeout) {
												WriteLog.write(ShiobeForAndroidActivity.this, getString(R.string.timeout));
												if (url.startsWith(ListAdapter.app_uri_about)) {
													runOnUiThread(new Runnable() {
														public final void run() {
															webView1.stopLoading();
															webView1.loadUrl(ListAdapter.app_uri_local);
															webView1.requestFocus(View.FOCUS_DOWN);
														}
													});
													//											} else if (url.equals(ListAdapter.app_uri_local) == false) {
													//												adapter.toast(getString(R.string.timeout));
												}
											}
										}
									}).start();
								}

								@Override
								public final void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
									WriteLog.write(ShiobeForAndroidActivity.this, getString(R.string.description) + ": " + description);

									//								if (failingUrl.startsWith(ListAdapter.app_uri_about)) {
									//									webView1.stopLoading();
									//									webView1.loadUrl(ListAdapter.app_uri_local);
									//									webView1.requestFocus(View.FOCUS_DOWN);
									//								} else if (failingUrl.equals(ListAdapter.app_uri_local) == false) {
									//									WriteLog.write(ShiobeForAndroidActivity.this, "errorCode: " + errorCode + " description: " + description + " failingUrl: " + failingUrl);
									//									adapter.toast("errorCode: " + errorCode + " description: " + description + " failingUrl: " + failingUrl);
									//								}
								}

								@Override
								public final void onReceivedSslError(final WebView view, final SslErrorHandler handler, final SslError error) {
									handler.proceed();
								}
							});
							webView1.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
							webView1.setVerticalScrollbarOverlay(true);
							//						connectivityManager1 = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
							//						final NetworkInfo nInfo = connectivityManager1.getActiveNetworkInfo();
							//						if (nInfo != null) {
							//							webView1.loadUrl(ListAdapter.app_uri_about + "?id=" + StringUtil.join("_", adapter.getPhoneIds()) + "&note=" + StringUtil.join("__", adapter.getOurScreenNames()));
							//						} else {
							webView1.loadUrl(ListAdapter.app_uri_local);
							//						}
							webView1.requestFocus(View.FOCUS_DOWN);
						}
					}
				});
			}
		}).start();
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

		menu.add(0, R.string.tweet, 0, R.string.tweet).setIcon(android.R.drawable.ic_menu_edit);

		final SubMenu sub1 = menu.addSubMenu(getString(R.string.timeline) + "...").setIcon(android.R.drawable.ic_menu_view);
		sub1.add(0, R.string.app_name_tabsactivity, 0, R.string.app_name_tabsactivity);
		sub1.add(0, R.string.home, 0, R.string.home);
		sub1.add(0, R.string.mention, 0, R.string.mention);
		sub1.add(0, R.string.user, 0, R.string.user);
		sub1.add(0, R.string.userfav, 0, R.string.userfav);

		menu.add(0, R.string.addaccount, 0, R.string.addaccount).setIcon(android.R.drawable.ic_input_add);

		menu.add(0, R.string.check_ratelimit, 0, R.string.check_ratelimit).setIcon(android.R.drawable.stat_sys_download);

		menu.add(0, R.string.check_apistatus, 0, R.string.check_apistatus).setIcon(android.R.drawable.stat_sys_download);

		menu.add(0, R.string.make_shortcut, 0, R.string.make_shortcut).setIcon(android.R.drawable.ic_menu_add);

		final SubMenu sub2 = menu.addSubMenu(getString(R.string.settings_all) + "...").setIcon(android.R.drawable.ic_menu_preferences);
		sub2.add(0, R.string.settings, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
		sub2.add(0, R.string.settings_import_export, 0, getString(R.string.settings_import_export) + "...").setIcon(android.R.drawable.stat_notify_sdcard);
		sub2.add(0, R.string.select_tl_fontfilename, 0, getString(R.string.select_tl_fontfilename) + "...").setIcon(android.R.drawable.stat_notify_sdcard);
		sub2.add(0, R.string.get_blockusers, 0, R.string.get_blockusers).setIcon(android.R.drawable.stat_sys_download);
		sub2.add(0, R.string.get_short_url_length, 0, R.string.get_short_url_length).setIcon(android.R.drawable.stat_sys_download);

		final SubMenu sub3 = sub2.addSubMenu(getString(R.string.set_colors) + "...").setIcon(android.R.drawable.ic_menu_gallery);
		sub3.add(0, R.string.get_colors, 0, R.string.get_colors).setIcon(android.R.drawable.stat_sys_download);
		sub3.add(0, R.string.set_colors_white_on_black, 0, R.string.set_colors_white_on_black).setIcon(android.R.drawable.ic_menu_gallery);
		sub3.add(0, R.string.set_colors_black_on_white, 0, R.string.set_colors_black_on_white).setIcon(android.R.drawable.ic_menu_gallery);
		sub3.add(0, R.string.set_colors_white_on_darkgrey, 0, R.string.set_colors_white_on_darkgrey).setIcon(android.R.drawable.ic_menu_gallery);
		sub3.add(0, R.string.set_colors_darkgrey_on_black, 0, R.string.set_colors_darkgrey_on_black).setIcon(android.R.drawable.ic_menu_gallery);

		menu.add(0, R.string.copyright, 0, R.string.copyright).setIcon(android.R.drawable.ic_menu_info_details);

		menu.add(0, R.string.quit, 0, R.string.quit).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	@Override
	public final boolean onKeyDown(final int keyCode, final KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (webView1.canGoBack()) {
				webView1.goBack();
				return true;
			} else {
				// this.moveTaskToBack(true);
				finish();
				return false;
			}
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	public final void onNewIntent(final Intent intent) {
		if (checkNetworkUtil.isConnected() == false) {
			adapter.toast(getString(R.string.cannot_access_internet));
			return;
		}

		final Uri uri = intent.getData();
		if (uri == null) {
			return;
		}
		final String verifier = uri.getQueryParameter("oauth_verifier");
		if (verifier.equals("") == false) {
			try {
				pref_app = PreferenceManager.getDefaultSharedPreferences(this);
				final String pref_consumerKey = pref_app.getString("pref_consumerkey", "");
				final String pref_consumerSecret = pref_app.getString("pref_consumersecret", "");
				final Boolean pref_enable_consumerKey = pref_app.getBoolean("pref_enable_consumerkey", false);
				if (( pref_enable_consumerKey == true ) && ( pref_consumerKey.equals("") == false ) && ( pref_consumerSecret.equals("") == false )) {
					consumerKey = pref_consumerKey;
					consumerSecret = pref_consumerSecret;
				} else {
					consumerKey = getString(R.string.default_consumerKey);
					consumerSecret = getString(R.string.default_consumerSecret);
				}
				try {
					pref_timeout_t4j_connection = Integer.parseInt(pref_app.getString("pref_timeout_t4j_connection", "20000"));
				} catch (final Exception e) {
					pref_timeout_t4j_connection = 20000;
				}
				try {
					pref_timeout_t4j_read = Integer.parseInt(pref_app.getString("pref_timeout_t4j_read", "120000"));
				} catch (final Exception e) {
					pref_timeout_t4j_read = 120000;
				}

				AccessToken accessToken = null;
				try {
					accessToken = oAuthAuthorization.getOAuthAccessToken(verifier);
					final ConfigurationBuilder cbuilder = new ConfigurationBuilder();
					cbuilder.setOAuthConsumerKey(consumerKey);
					cbuilder.setOAuthConsumerSecret(consumerSecret);
					cbuilder.setOAuthAccessToken(accessToken.getToken());
					cbuilder.setOAuthAccessTokenSecret(accessToken.getTokenSecret());
					final Configuration configuration = cbuilder.build();
					final TwitterFactory twitterFactory = new TwitterFactory(configuration);
					twitter = twitterFactory.getInstance();
				} catch (final Exception e) {
				}

				String profile_image_url = "";
				try {
					final User user = twitter.showUser(twitter.getScreenName());
					profile_image_url = user.getProfileImageURL().toString();
				} catch (final IllegalStateException e) {
					WriteLog.write(this, e);
				}

				pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
				final int index = Integer.parseInt(pref_twtr.getString("index", "-1"));

				if (index > -1) {
					if (accessToken != null) {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("consumer_key_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, consumerKey));
						editor.putString("consumer_secret_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, consumerSecret));
						editor.putString("oauth_token_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, accessToken.getToken()));
						editor.putString("oauth_token_secret_" + Integer.toString(index), MyCrypt.encrypt(this, crpKey, accessToken.getTokenSecret()));
						editor.putString("status_" + Integer.toString(index), "available");
						editor.putString("screen_name_" + Integer.toString(index), twitter.getScreenName());
						editor.putString("profile_image_url_" + Integer.toString(index), profile_image_url);
						editor.commit();

						final Intent intent2 = new Intent(this, UpdateTweet.class);
						this.startActivityForResult(intent2, 0);
					}
				}
			} catch (final TwitterException e) {
			}
		}
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		boolean ret = true;

		pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
		final int user_index_size = Integer.parseInt(pref_app.getString("pref_user_index_size", Integer.toString(ListAdapter.default_user_index_size)));
		final String[] ITEM1 = new String[user_index_size + 1];
		final String[] ITEM2 = new String[user_index_size];
		int account_num = 0;
		for (int i = 0; i < user_index_size; i++) {
			final String itemname = pref_twtr.getString("screen_name_" + i, "");
			account_num += itemname.equals("") ? 0 : 1;
			ITEM1[i + 1] = itemname.equals("") ? " - " : "@" + itemname;
			ITEM2[i] = itemname.equals("") ? " - " : "@" + itemname;
		}
		ITEM1[0] = ( account_num > 0 ) ? "Current" : "---";

		pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
		int idx = -1;
		try {
			idx = Integer.parseInt(pref_twtr.getString("index", "0"));
		} catch (final Exception e) {
			idx = 0;
		}
		final int index = idx;

		if (item.getItemId() == R.string.tweet) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							startUpdateTweet();
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							startUpdateTweet();
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.app_name_tabsactivity) {
			final Intent intent1 = new Intent();
			intent1.setClassName("jp.gr.java_conf.ya.shiobeforandroid2", "jp.gr.java_conf.ya.shiobeforandroid2.TabsActivity");
			startActivity(intent1);

		} else if (item.getItemId() == R.string.home) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							adapter.startTlHome(index);
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							adapter.startTlHome(which - 1);
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.mention) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							adapter.startTlMention(index);
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							adapter.startTlMention(which - 1);
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.user) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							adapter.startTlUser(index);
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							adapter.startTlUser(which - 1);
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.userfav) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							adapter.startTlFavorite(index);
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							adapter.startTlFavorite(which - 1);
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.addaccount) {
			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.addaccount2).setItems(ITEM2, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					try {
						pref_twtr = getSharedPreferences("Twitter_setting", MODE_PRIVATE);
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which));
						editor.commit();
					} catch (final Exception e) {
					}
					try {
						disconnectTwitter();
					} catch (final Exception e) {
					}
					ITEM2[which] = " - ";
					try {
						connectTwitter();
					} catch (final TwitterException e) {
					}

				}
			}).create().show();

		} else if (item.getItemId() == R.string.check_ratelimit) {
			adapter.showRateLimits(webView1);

		} else if (item.getItemId() == R.string.check_apistatus) {
			adapter.showApiStatuses(webView1);

		} else if (item.getItemId() == R.string.deljustbefore) {
			adapter.deljustbefore(-1);

		} else if (item.getItemId() == R.string.settings) {
			final Intent intent2 = new Intent();
			intent2.setClassName("jp.gr.java_conf.ya.shiobeforandroid2", "jp.gr.java_conf.ya.shiobeforandroid2.Preference");
			startActivity(intent2);

		} else if (item.getItemId() == R.string.settings_import_export) {
			final Date date = new Date();
			final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddkkmmss", Locale.JAPAN);
			final String datestr = simpleDateFormat.format(date);

			final String prefXmlPath = Environment.getDataDirectory().getPath() + "/data/" + getPackageName() + "/shared_prefs/" + getPackageName() + "_preferences.xml";
			final String backupPath = Environment.getExternalStorageDirectory().toString() + "/" + getPackageName() + "_preferences.xml";
			final String backupPath_date = Environment.getExternalStorageDirectory().toString() + "/" + getPackageName() + "_preferences_" + datestr + ".xml";
			WriteLog.write(this, "prefXmlPath: " + prefXmlPath);
			WriteLog.write(this, "backupPath: " + backupPath);

			final File dir = new File(Environment.getExternalStorageDirectory().toString() + "/");
			final File[] files = dir.listFiles(new FileCheckPrefXml());
			final String[] ITEM3 = new String[files.length + 2];
			ITEM3[0] = getString(R.string.settings_export);
			ITEM3[1] = getString(R.string.settings_export) + " (日付を付加)";
			for (int i = 0; i < files.length; i++) {
				ITEM3[i + 2] = files[i].getName();
			}

			adapter.toast(getString(R.string.settings_import_export_message));

			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.settings_import_export).setItems(ITEM3, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						if (prefExport(prefXmlPath, backupPath)) {
							adapter.toast(getString(R.string.settings_export) + ": " + getString(R.string.success));
						}
					} else if (which == 1) {
						if (prefExport(prefXmlPath, backupPath_date)) {
							adapter.toast(getString(R.string.settings_export) + ": " + getString(R.string.success));
						}
					} else {
						if (prefImport(Environment.getExternalStorageDirectory().toString() + "/" + ITEM3[which])) {
							adapter.toast(getString(R.string.settings_import) + ": " + getString(R.string.success));
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.select_tl_fontfilename) {
			final File dir = new File(Environment.getExternalStorageDirectory().toString() + "/");
			WriteLog.write(this, dir.getAbsolutePath());
			final File dir2 = new File(Environment.getExternalStorageDirectory().toString() + "/Fonts/");
			WriteLog.write(this, dir2.getAbsolutePath());

			File[] files2a;
			File[] files2b;
			int length = 0;
			try {
				final File[] files2a_ = dir.listFiles();
				WriteLog.write(this, "/");
				for (final File f : files2a_) {
					WriteLog.write(this, f.getAbsolutePath());
				}

				files2a = dir.listFiles(new FileCheck_font());
				WriteLog.write(this, "/*.ttf");
				for (final File f : files2a) {
					WriteLog.write(this, f.getAbsolutePath());
				}

				length += files2a.length;
			} catch (final Exception e) {
				files2a = null;
			}

			try {
				final File[] files2b_ = dir2.listFiles();
				WriteLog.write(this, "/Fonts/");
				for (final File f : files2b_) {
					WriteLog.write(this, f.getAbsolutePath());
				}

				files2b = dir2.listFiles(new FileCheck_font());
				WriteLog.write(this, "/Fonts/*.ttf");
				for (final File f : files2b) {
					WriteLog.write(this, f.getAbsolutePath());
				}

				length += files2b.length;
			} catch (final Exception e) {
				files2b = null;
			}

			WriteLog.write(this, "length: " + length);
			if (length > 0) {
				final String[] ITEM4 = new String[( length )];

				int k = 0;
				if (files2a != null) {
					for (int i = 0; i < files2a.length; i++) {
						ITEM4[k] = files2a[i].getName();
						k++;
					}
				}
				if (files2b != null) {
					for (int j = 0; j < files2b.length; j++) {
						ITEM4[k] = files2b[j].getName();
						k++;
					}
				}

				new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.select_tl_fontfilename).setItems(ITEM4, new DialogInterface.OnClickListener() {
					@Override
					public final void onClick(final DialogInterface dialog, final int which) {
						final SharedPreferences.Editor editor = pref_app.edit();
						editor.putString("pref_tl_fontfilename", ITEM4[which]);
						editor.commit();
						pref_app = PreferenceManager.getDefaultSharedPreferences(ShiobeForAndroidActivity.this);

						adapter.toast(getString(R.string.select_tl_fontfilename) + ": " + getString(R.string.success) + ": " + ITEM4[which]);
					}
				}).create().show();
			} else {
				adapter.toast(getString(R.string.select_tl_fontfilename_not_found));
			}

		} else if (item.getItemId() == R.string.get_blockusers) {
			final SharedPreferences.Editor editor = pref_twtr.edit();
			final StringBuilder allBlockedUsersStr = new StringBuilder(32);
			for (int j = 0; j < user_index_size; j++) {
				if (isConnected(pref_twtr.getString("status_" + j, ""))) {
					try {
						final PagableResponseList<User> blockedUsers = adapter.getTwitter(j, false).getBlocksList();
						if (blockedUsers.isEmpty() == false) {
							final StringBuilder blockedUsersStr = new StringBuilder(32);
							for (final User user : blockedUsers) {
								blockedUsersStr.append(user.getScreenName() + ",");
							}
							editor.putString("blocked_users_" + Integer.toString(j), "," + blockedUsersStr.toString());
							allBlockedUsersStr.append(blockedUsersStr.toString());
						}
					} catch (final TwitterException e) {
						WriteLog.write(this, e);
						adapter.toast(getString(R.string.cannot_access_twitter));
					} catch (final Exception e) {
						WriteLog.write(this, e);
						adapter.toast(getString(R.string.exception));
					}
				}
			}
			editor.putString("blocked_users", "," + allBlockedUsersStr.toString());
			editor.commit();

		} else if (item.getItemId() == R.string.get_colors) {

			new AlertDialog.Builder(ShiobeForAndroidActivity.this).setTitle(R.string.login).setItems(ITEM1, new DialogInterface.OnClickListener() {
				@Override
				public final void onClick(final DialogInterface dialog, final int which) {
					if (which == 0) {
						Status = pref_twtr.getString("status_" + Integer.toString(index), "");
						if (isConnected(Status)) {
							getColors(index);
						}
					} else {
						final SharedPreferences.Editor editor = pref_twtr.edit();
						editor.putString("index", Integer.toString(which - 1));
						editor.commit();
						Status = pref_twtr.getString("status_" + ( which - 1 ), "");
						if (isConnected(Status)) {
							getColors(index);
						} else {
							try {
								connectTwitter();
							} catch (final TwitterException e) {
							}
						}
					}
				}
			}).create().show();

		} else if (item.getItemId() == R.string.set_colors_white_on_black) {
			setColors("#000000", "#0F1419", "", "#F8F8F8", "#33BFDB", "#77B255");

		} else if (item.getItemId() == R.string.set_colors_black_on_white) {
			setColors("#B7B7B7", "#ffffff", "", "#0F1419", "#0E7AC4", "#77B255");

		} else if (item.getItemId() == R.string.set_colors_white_on_darkgrey) {
			setColors("#B7B7B7", "#383838", "", "#F8F8F8", "#0E7AC4", "#77B255");

		} else if (item.getItemId() == R.string.set_colors_darkgrey_on_black) {
			setColors("#000000", "#0F1419", "#666666", "#7C858E", "#5C82A4", "#7A946A");

		} else if (item.getItemId() == R.string.get_short_url_length) {
			final SharedPreferences.Editor editor1 = pref_twtr.edit();
			try {
				final int pref_short_url_length = adapter.getTwitter(0, false).getAPIConfiguration().getShortURLLength();
				final String pref_short_url_length_string = Integer.toString(pref_short_url_length);
				editor1.putString("pref_short_url_length", pref_short_url_length_string);
				editor1.commit();
				adapter.toast(getString(R.string.short_url_length) + ": " + pref_short_url_length_string);
			} catch (final TwitterException e) {
				WriteLog.write(this, e);
				adapter.toast(getString(R.string.cannot_access_twitter));
			} catch (final Exception e) {
				WriteLog.write(this, e);
				adapter.toast(getString(R.string.exception));
			}

		} else if (item.getItemId() == R.string.copyright) {
			try {
				final PackageInfo packageInfo = getPackageManager().getPackageInfo("jp.gr.java_conf.ya.shiobeforandroid2", PackageManager.GET_META_DATA);
				adapter.toast(getString(R.string.app_name_short) + ": " + getString(R.string.version) + packageInfo.versionName + " (" + packageInfo.versionCode + ")");
			} catch (final NameNotFoundException e) {
			}

			try {
				webView1.loadUrl(ListAdapter.app_uri_about + "?id=" + StringUtil.join("_", ListAdapter.getPhoneIds()) + "&note=" + StringUtil.join("__", adapter.getOurScreenNames()));
				webView1.requestFocus(View.FOCUS_DOWN);
			} catch (final Exception e) {
			}

		} else if (item.getItemId() == R.string.make_shortcut) {
			makeShortcuts();

		} else if (item.getItemId() == R.string.quit) {
			adapter.cancelNotification(ListAdapter.NOTIFY_RUNNING);
			finish();

		}
		return ret;
	}

	private final boolean prefExport(final String backupPath) {
		final File file = new File(backupPath);
		boolean res = false;
		ObjectOutputStream output = null;
		try {
			output = new ObjectOutputStream(new FileOutputStream(file));
			final SharedPreferences pref_app = PreferenceManager.getDefaultSharedPreferences(this);
			output.writeObject(pref_app.getAll());
			res = true;
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
		} finally {
			try {
				if (output != null) {
					output.flush();
					output.close();
				}
			} catch (final IOException ex) {
			}
		}
		return res;
	}

	private final boolean prefExport(final String prefXmlPath, final String backupPath) {
		final boolean result1 = prefExport(backupPath + ".bin");
		final boolean result2 = copyFile(prefXmlPath, backupPath);
		return result1 && result2;
	}

	private final boolean prefImport(final String backupPath) {
		final File file = new File(backupPath);
		boolean res = false;
		ObjectInputStream input = null;
		try {
			input = new ObjectInputStream(new FileInputStream(file));
			final SharedPreferences pref_app = PreferenceManager.getDefaultSharedPreferences(this);
			final Editor prefEdit = pref_app.edit();
			prefEdit.clear();
			@SuppressWarnings("unchecked")
			final Map<String, ?> entries = (Map<String, ?>) input.readObject();
			for (Entry<String, ?> entry : entries.entrySet()) {
				final Object v = entry.getValue();
				final String key = entry.getKey();

				if (v instanceof Boolean)
					prefEdit.putBoolean(key, ( (Boolean) v ).booleanValue());
				else if (v instanceof Float)
					prefEdit.putFloat(key, ( (Float) v ).floatValue());
				else if (v instanceof Integer)
					prefEdit.putInt(key, ( (Integer) v ).intValue());
				else if (v instanceof Long)
					prefEdit.putLong(key, ( (Long) v ).longValue());
				else if (v instanceof String)
					prefEdit.putString(key, ( (String) v ));
			}
			prefEdit.commit();
			res = true;
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
		} catch (final ClassNotFoundException e) {
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (final IOException ex) {
			}
		}
		return res;
	}

	private final void getColors(final int index) {
		try {
			final String screenName = pref_twtr.getString("screen_name_" + Integer.toString(index), "");
			final User user = adapter.getTwitter(index, false).showUser(screenName);

			final String getProfileBackgroundColor = "#" + user.getProfileBackgroundColor();
			final String getProfileLinkColor = "#" + user.getProfileLinkColor();
			//	String getProfileSidebarBorderColor = "#" + user.getProfileSidebarBorderColor();
			//	String getProfileSidebarFillColor = "#" + user.getProfileSidebarFillColor();
			final String getProfileTextColor = "#" + user.getProfileTextColor();

			setColors(getProfileBackgroundColor, getProfileBackgroundColor, "#ffffff", getProfileTextColor, getProfileLinkColor, "#77B255");

			adapter.toast(getString(R.string.done_get_colors));
		} catch (final TwitterException e) {
			WriteLog.write(ShiobeForAndroidActivity.this, e);
			adapter.toast(getString(R.string.cannot_access_twitter));
		} catch (final Exception e) {
			WriteLog.write(ShiobeForAndroidActivity.this, e);
			adapter.toast(getString(R.string.exception));
		}
	}

	private final void setColors(final String listviewBackgroundColor, final String backgroundColor, final String filterColor, final String fontColor, final String linkFontColor,
			final String retweetFontColor) {
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
		final SharedPreferences.Editor editor = pref_app.edit();

		editor.putString("pref_filter_item_inline_img", filterColor);
		editor.putString("pref_filter_item_usericon", filterColor);
		editor.putString("pref_filter_tl_headericon", filterColor);
		editor.putString("pref_header_bgcolor", backgroundColor);
		editor.putString("pref_header_fontcolor", fontColor);
		editor.putString("pref_listview_bgcolor", listviewBackgroundColor);
		editor.putString("pref_tl_bgcolor", backgroundColor);
		editor.putString("pref_tl_bgcolor_buttons", backgroundColor);
		editor.putString("pref_tl_bgcolor_updatetweet", backgroundColor);
		editor.putString("pref_tl_fontcolor_buttons", fontColor);
		editor.putString("pref_tl_fontcolor_createdat", fontColor);
		editor.putString("pref_tl_fontcolor_createdat_retweeted", retweetFontColor);
		editor.putString("pref_tl_fontcolor_createdat_retweetedby", fontColor);
		editor.putString("pref_tl_fontcolor_screenname", fontColor);
		editor.putString("pref_tl_fontcolor_screenname_retweeted", retweetFontColor);
		editor.putString("pref_tl_fontcolor_screenname_retweetedby", fontColor);
		editor.putString("pref_tl_fontcolor_source", fontColor);
		editor.putString("pref_tl_fontcolor_source_retweeted", retweetFontColor);
		editor.putString("pref_tl_fontcolor_source_retweetedby", fontColor);
		editor.putString("pref_tl_fontcolor_statustext", fontColor);
		editor.putString("pref_tl_fontcolor_statustext_hashtag", linkFontColor);
		editor.putString("pref_tl_fontcolor_statustext_location", linkFontColor);
		editor.putString("pref_tl_fontcolor_statustext_retweeted", retweetFontColor);
		editor.putString("pref_tl_fontcolor_statustext_screenname", linkFontColor);
		editor.putString("pref_tl_fontcolor_statustext_uri", linkFontColor);
		editor.putString("pref_tl_fontcolor_text_updatetweet", fontColor);
		editor.putString("pref_tl_fontcolor_text_updatetweet_over", "#ff0000");
		editor.putString("pref_tl_fontcolor_username", fontColor);
		editor.putString("pref_tl_fontcolor_username_retweeted", retweetFontColor);
		editor.putString("pref_userinfo_fontcolor_my_profile", fontColor);

		editor.commit();
		pref_app = PreferenceManager.getDefaultSharedPreferences(this);
	}

	private final void startUpdateTweet() {
		final Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setClassName("jp.gr.java_conf.ya.shiobeforandroid2", "jp.gr.java_conf.ya.shiobeforandroid2.UpdateTweet");
		// intent.putExtra("str2", "hoge");
		startActivityForResult(intent, 0);
	}

}
