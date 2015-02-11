package jp.gr.java_conf.ya.shiobeforandroid2.util; // Copyright (c) 2013-2015 YA <ya.androidapp@gmail.com> All rights reserved.

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.widget.TextView;

public final class FontUtil {

	private Typeface font;

	private final Typeface getFont() {
		return ( font == null ) ? Typeface.DEFAULT : font;
	}

	private final Typeface getFontFromZip(final String fontFileName, final Context context) {
		if (fontFileName.equals("")) {
			return null;
		}

		Typeface ret;
		File unzipedFile;

		if (fontFileName.endsWith(".ttf")) {
			WriteLog.write(context, "(fontFileName.endsWith(\".ttf\"))");

			try {
				unzipedFile = new File(Environment.getExternalStorageDirectory().getPath() + "/Fonts/" + fontFileName);
				if (unzipedFile.exists()) {
					WriteLog.write(context, "unzipedFile.getPath(): " + unzipedFile.getPath());
					ret = Typeface.createFromFile(unzipedFile.getPath());
				} else {
					unzipedFile = new File(Environment.getExternalStorageDirectory().getPath() + "/" + fontFileName);
					if (unzipedFile.exists()) {
						WriteLog.write(context, "unzipedFile.getPath(): " + unzipedFile.getPath());
						ret = Typeface.createFromFile(unzipedFile.getPath());
					} else {
						ret = null;
					}
				}
			} catch (final Exception e) {
				WriteLog.write(context, e);
				ret = null;
			}
		} else if (fontFileName.endsWith(".zip")) {
			WriteLog.write(context, "(fontFileName.endsWith(\".zip\"))");

			try {
				InputStream is;
				try {
					if (new File(Environment.getExternalStorageDirectory().getPath() + "/Fonts/" + fontFileName).exists()) {
						is = new FileInputStream(Environment.getExternalStorageDirectory().getPath() + "/Fonts/" + fontFileName);
					} else {
						if (new File(Environment.getExternalStorageDirectory().getPath() + "/" + fontFileName).exists()) {
							is = new FileInputStream(Environment.getExternalStorageDirectory().getPath() + "/" + fontFileName);
						} else {
							is = null;
						}
					}
				} catch (Exception e1) {
					WriteLog.write(context, e1);
					is = null;
				}

				final ZipInputStream zis = new ZipInputStream(is);
				final ZipEntry ze = zis.getNextEntry();
				if (ze != null) {
					unzipedFile = new File(context.getFilesDir(), ze.getName());

					//フォントがすでに解凍されていればなにもしない
					if (unzipedFile.exists()) {
						return Typeface.createFromFile(unzipedFile.getPath());
					}
					WriteLog.write(context, "fontfile unzipedFile: " + unzipedFile.getPath());
					final FileOutputStream fos = new FileOutputStream(unzipedFile, false);
					final byte[] buf = new byte[1024];
					int size = 0;
					while (( size = zis.read(buf, 0, buf.length) ) > -1) {
						fos.write(buf, 0, size);
					}
					fos.close();
					zis.closeEntry();
					ret = Typeface.createFromFile(unzipedFile.getPath());
				} else {
					ret = null;
				}
				zis.close();
			} catch (final Exception e) {
				WriteLog.write(context, e);
				ret = null;
			}
		} else {
			ret = null;
		}

		return ret;
	}

	public final void loadFont(final String fontFileName, final Context context) {
		try {
			font = getFontFromZip(fontFileName, context);
		} catch (final Exception e) {
		}
	}

	public final void setFont(TextView textView, Context context) {
		final SharedPreferences pref_app = PreferenceManager.getDefaultSharedPreferences(context);
		final String pref_tl_fontfilename = pref_app.getString("pref_tl_fontfilename", "");
		final String pref_tl_fontfamily_name = pref_app.getString("pref_tl_fontfamily_name", "0");
		final String pref_tl_fontstyle_name = pref_app.getString("pref_tl_fontstyle_name", "0");

		if (pref_tl_fontfilename.equals("") == false) {
			Typeface typeface;
			try {
				typeface = getFont();
			} catch (final Exception e) {
				typeface = null;
				WriteLog.write(context, e);
			}
			if (typeface != null) {
				textView.setTypeface(typeface);
			}
		} else if (pref_tl_fontfamily_name.equals("0") == false) {
			Typeface typeface;
			switch (Integer.parseInt(pref_tl_fontfamily_name)) {
			case 0:
				typeface = Typeface.DEFAULT;
				break;
			case 1:
				typeface = Typeface.DEFAULT_BOLD;
				break;
			case 2:
				typeface = Typeface.SANS_SERIF;
				break;
			case 3:
				typeface = Typeface.SERIF;
				break;
			case 4:
				typeface = Typeface.MONOSPACE;
				break;
			default:
				typeface = Typeface.DEFAULT;
				break;
			}
			textView.setTypeface(( ( pref_tl_fontstyle_name.equals("0") ) ? typeface : ( Typeface.create(typeface, Integer.parseInt(pref_tl_fontstyle_name)) ) ));
		}
	}
}
