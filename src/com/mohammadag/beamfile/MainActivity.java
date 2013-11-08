/*   
 *  File Beam, a quick application to send files using Android Beam.
 *  Copyright (C) 2013 Mohammad Abu-Garbeyyeh
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mohammadag.beamfile;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.espian.showcaseview.ShowcaseView;
import com.google.ads.Ad;
import com.google.ads.AdListener;
import com.google.ads.AdRequest;
import com.google.ads.AdRequest.ErrorCode;
import com.google.ads.AdView;

public class MainActivity extends Activity implements OnNdefPushCompleteCallback {

	private final static String TAG = "File Beam";

	private TextView mSizeTextView = null;
	private TextView mFileNameTextView = null;
	private TextView mContentTypeTextView = null;
	private TextView mStatusTextView = null;
	private TextView mBeamingTextView = null;
	private ImageView mIconImageView = null;
	private AdView mAdView = null;
	private SharedPreferences mPreferences = null;

	protected boolean mSuppressNextAdAnimation = false;

	private NfcAdapter mNfcAdapter;

	private void loadViews() {
		mSizeTextView = (TextView) findViewById(R.id.fileSizeTextView);
		mFileNameTextView = (TextView) findViewById(R.id.fileNameTextView);
		mContentTypeTextView = (TextView) findViewById(R.id.contentTypeTextView);
		mStatusTextView = (TextView) findViewById(R.id.statusTextView);
		mBeamingTextView = (TextView) findViewById(R.id.beamingFileTextView);
		mIconImageView = (ImageView) findViewById(R.id.defaultIconView);
		mAdView = (AdView) findViewById(R.id.adView);

		mAdView.setAdListener(new AdListener() {

			@Override
			public void onReceiveAd(Ad ad) {
				Animation fadeOutAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
						android.R.anim.fade_out);
				final Animation fadeInAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
						android.R.anim.fade_in);
				fadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {}
					@Override
					public void onAnimationEnd(Animation animation) {
						mAdView.startAnimation(fadeInAnimation);
					}
					@Override
					public void onAnimationRepeat(Animation animation) {}
				});
				mAdView.startAnimation(fadeInAnimation);
			}
			@Override
			public void onPresentScreen(Ad arg0) {}
			@Override
			public void onLeaveApplication(Ad arg0) {}
			@Override
			public void onFailedToReceiveAd(Ad arg0, ErrorCode arg1) {}
			@Override
			public void onDismissScreen(Ad arg0) {}
		});
	}

	public void showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
		alertDialog.setTitle(R.string.about_dialog_title);
		alertDialog.setMessage(R.string.about_text);
		alertDialog.show();
	}

	private void setNfcNotAvailable() {
		mSizeTextView.setVisibility(0);
		mFileNameTextView.setVisibility(0);
		mContentTypeTextView.setVisibility(0);

		mStatusTextView.setText(R.string.nfc_not_available);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_about:
			showAbout();
			return true;
		case R.id.menu_settings:
			Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
			startActivity(settingsIntent);
			return true;
		case R.id.menu_donate:
			Intent intent = new Intent(Intent.ACTION_VIEW, 
					Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=QB4BLETCSBGNS"));
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean isDialogModeEnabled = mPreferences.getBoolean("use_dialog_theme", false);
		if (isDialogModeEnabled)
			setTheme(android.R.style.Theme_Holo_Dialog);
		else
			setTheme(android.R.style.Theme_Holo);

		super.onCreate(savedInstanceState);

		if (isDialogModeEnabled) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}

		setContentView(R.layout.activity_main);
		loadViews();

		if (isDialogModeEnabled && !Utils.deviceHasHardwareMenuKey(this)) {
			mIconImageView.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					openOptionsMenu();
					return true;
				}
			});
		}

		boolean enableAds = mPreferences.getBoolean(Constants.SETTINGS_KEY_ENABLE_ADS, true);
		toggleAds((enableAds && !isDialogModeEnabled));
		mSuppressNextAdAnimation = false;

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			setNfcNotAvailable();
			return;
		} else {
			if (!mNfcAdapter.isEnabled()) {
				mStatusTextView.setText(R.string.nfc_turned_off);
			}
		}
		
		processIntent(getIntent());

		mFileNameTextView.setSelected(true);
		
		showOverflowShowcaseIfNeeded(isDialogModeEnabled && !Utils.deviceHasHardwareMenuKey(this));
	}

	private void showOverflowShowcaseIfNeeded(boolean isItEvenNeeded) {
		if (!isItEvenNeeded)
			return;
		
		if (!mPreferences.getBoolean(Constants.SETTINGS_KEY_OVERFLOW_SHOWCASE_SHOWN, false)) {
			ShowcaseView.ConfigOptions co = new ShowcaseView.ConfigOptions();
			co.hideOnClickOutside = true;

			ShowcaseView.insertShowcaseView(mIconImageView, this,
					R.string.settings_showcase_title, R.string.settings_showcase_detail, co);
			mPreferences.edit().putBoolean(Constants.SETTINGS_KEY_OVERFLOW_SHOWCASE_SHOWN, true).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		runOnUiThread(new Runnable() {
			public void run() {
				mStatusTextView.setText(R.string.file_sent);
			}
		});
	}
	
	private void processIntent(Intent intent) {
		Bundle extras = intent.getExtras();
		String action = intent.getAction();
		
		if (Intent.ACTION_SEND.equals(action)) {   
			if (extras.containsKey(Intent.EXTRA_STREAM)) {
				Uri fileUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);

				Log.d(TAG, fileUri.toString());

				mFileNameTextView.setText(Utils.getFileNameByUri(this, fileUri));
				mSizeTextView.setText(Utils.getHumanReadableFileSize(fileUri.getPath()));
				mContentTypeTextView.setText(intent.getType());

				final Drawable icon = Utils.getImageForFile(this, fileUri, intent);
				if (icon != null)
					mIconImageView.setImageDrawable(icon);

				mNfcAdapter.setBeamPushUris(new Uri[] {fileUri}, this);
				mNfcAdapter.setOnNdefPushCompleteCallback(this, this); 
			} else {
				String text = extras.getCharSequence(Intent.EXTRA_TEXT).toString();
				NdefMessage message;

				mFileNameTextView.setText(text);
				mSizeTextView.setText("");
				mContentTypeTextView.setText("plain/text");
				mBeamingTextView.setText(R.string.beaming_text);

				try {
					new URL(text);

					NdefRecord uriRecord = new NdefRecord(
							NdefRecord.TNF_ABSOLUTE_URI ,
							text.getBytes(Charset.forName("US-ASCII")),
							new byte[0], new byte[0]);

					message = new NdefMessage(uriRecord);
				} catch (MalformedURLException e) {
					NdefRecord record = Utils.createTextRecord(text, Locale.getDefault(), true);
					message = new NdefMessage(record);
				}


				mNfcAdapter.setNdefPushMessage(message, this);
				mNfcAdapter.setOnNdefPushCompleteCallback(this, this); 
			}
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
			mBeamingTextView.setText(R.string.beaming_files);
			ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);

			long size = 0;
			for (Uri uri: fileUris) {
				Log.d(TAG, uri.toString());
				size = size + Utils.getFileSize(uri.getPath());
			}

			Uri[] uriList = fileUris.toArray(new Uri[0]);

			if (uriList.length == 1) {
				Uri fileUri = uriList[0];
				mFileNameTextView.setText(Utils.getFileNameByUri(this, fileUri));
				mSizeTextView.setText(Utils.getHumanReadableFileSize(fileUri.getPath()));
				mContentTypeTextView.setText(Utils.getMimeType(fileUri.getPath()));
			} else {
				mFileNameTextView.setText(R.string.multiple_files);
				mSizeTextView.setText(Utils.humanReadableByteCount(size, true));
				mContentTypeTextView.setText(R.string.multiple_file_types);
			}

			mNfcAdapter.setBeamPushUris(uriList, this);
			mNfcAdapter.setOnNdefPushCompleteCallback(this, this); 
		}
	}

	private void toggleAds(boolean enable) {
		final boolean shouldShow = enable;
		int animationResId = android.R.anim.fade_in;
		if (enable) {
			animationResId = android.R.anim.fade_in;
			showAd();
		} else {
			animationResId = android.R.anim.fade_out;
			hideAd();
		}

		Animation animation = AnimationUtils.loadAnimation(getApplicationContext(),
				animationResId);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
				mSuppressNextAdAnimation = true;
			}
			@Override
			public void onAnimationRepeat(Animation animation) {}
			@Override
			public void onAnimationEnd(Animation animation) {
				if (shouldShow) {
					showAd();
				} else {
					hideAd();
				}
			}
		});
		mAdView.startAnimation(animation);
	}

	private void showAd() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mAdView.setEnabled(true);
				mAdView.setVisibility(View.VISIBLE);
				mAdView.loadAd(new AdRequest());
			}
		});
	}

	private void hideAd() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mAdView.setEnabled(false);
				mAdView.setVisibility(View.GONE);
			}
		});
	}
}
