package com.mohammadag.beamfile;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefRecord;
import android.provider.MediaStore;
import android.view.ViewConfiguration;
import android.webkit.MimeTypeMap;

public class Utils {
	private static Boolean mHasHardwareMenuKey = null;
	
	public static boolean deviceHasHardwareMenuKey(Context context) {
		if (mHasHardwareMenuKey == null)
			mHasHardwareMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
		return mHasHardwareMenuKey;
	}
	
	public static long getFileSize(String pathToFile) {
		File f = new File(pathToFile);
		return f.length();
	}

	public static String getMimeType(String url) {
		if (url.endsWith("TiBkp"))
			return "Titanium Backup file";
		String type = null;
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			MimeTypeMap mime = MimeTypeMap.getSingleton();
			type = mime.getMimeTypeFromExtension(extension);
		}
		return type;
	}

	@SuppressLint("DefaultLocale")
	public static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1024 : 1000;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public static String getHumanReadableFileSize(String pathToFile) {
		return humanReadableByteCount(getFileSize(pathToFile), true);
	}

	public static String getFileNameByUri(Context context, Uri uri) {
		String fileName = "";
		if (uri != null) {
			fileName = uri.toString();
			Uri filePathUri = uri;
			if (uri.getScheme().toString().compareTo("content") == 0) {      
				Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
					if (column_index == -1)
						return context.getString(R.string.unable_to_get_filename);
					filePathUri = Uri.parse(cursor.getString(column_index));
					fileName = filePathUri.getLastPathSegment().toString();
				}
			}
			else if (uri.getScheme().compareTo("file") == 0)
				fileName = filePathUri.getLastPathSegment().toString();
			else
				fileName = fileName + "_" + filePathUri.getLastPathSegment().toString();
		}
		return fileName;
	}

	public static NdefRecord createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {
		byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
		Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
		byte[] textBytes = payload.getBytes(utfEncoding);
		int utfBit = encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], data);
		return record;
	}

	public static Drawable getImageForFile(Context context, Uri fileUri, Intent intent) {
		Intent iconIntent = new Intent(Intent.ACTION_VIEW);
		iconIntent.setData(fileUri);
		iconIntent.setType(intent.getType());

		context.getPackageManager();
		List<ResolveInfo> matches = context.getPackageManager().queryIntentActivities(iconIntent, PackageManager.MATCH_DEFAULT_ONLY);
		for (ResolveInfo match : matches) {
			final Drawable icon = match.loadIcon(context.getPackageManager());
			return icon;
		}

		return null;
	}
}
