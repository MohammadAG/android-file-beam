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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.pm.PackageManager;

public class MainActivity extends Activity implements OnNdefPushCompleteCallback {
	
	public static long getFileSize(String pathToFile) {
    	File f = new File(pathToFile);
    	return f.length();
	}
	
	public static String getMimeType(String url)
	{
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
	
	public String getFileNameByUri(Uri uri)
	{
	    String fileName = uri.toString();
	    Uri filePathUri = uri;
	    if (uri.getScheme().toString().compareTo("content")==0)
	    {      
	    	Cursor cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null);
	        if (cursor.moveToFirst())
	        {
	        	int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
	        	if (column_index == -1)
	        		return getString(R.string.unable_to_get_filename);
	            filePathUri = Uri.parse(cursor.getString(column_index));
	            fileName = filePathUri.getLastPathSegment().toString();
	        }
	    }
	    else if (uri.getScheme().compareTo("file")==0)
	    {
	        fileName = filePathUri.getLastPathSegment().toString();
	    }
	    else
	    {
	        fileName = fileName+"_"+filePathUri.getLastPathSegment().toString();
	    }
	    return fileName;
	}
	
	public NdefRecord createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {
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
	
	public void showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setTitle(R.string.about_dialog_title);
        alertDialog.setMessage(R.string.about_text);
        alertDialog.show();
	}
	
	public Drawable getImageForFile(Uri fileUri, Intent intent) {
    	final Intent iconIntent = new Intent(Intent.ACTION_VIEW);
    	iconIntent.setData(fileUri);
    	iconIntent.setType(intent.getType());

    	getPackageManager();
		final List<ResolveInfo> matches = getPackageManager().queryIntentActivities(iconIntent, PackageManager.MATCH_DEFAULT_ONLY);
    	for (ResolveInfo match : matches) {
    	    final Drawable icon = match.loadIcon(getPackageManager());
    	    return icon;
    	}
    	
		return null;
	}
	
	private void setNfcNotAvailable() {
    	TextView sizeView = (TextView)findViewById(R.id.fileSizeTextView);
    	sizeView.setVisibility(0);
    	
    	TextView view = (TextView)findViewById(R.id.fileNameTextView);
    	view.setVisibility(0);
    	
    	TextView contentTypeView = (TextView)findViewById(R.id.contentTypeTextView);
    	contentTypeView.setVisibility(0);
    	
    	setStatus(getString(R.string.nfc_not_available));
	}
	
	private void setStatus(String text) {
		TextView statusView = (TextView)findViewById(R.id.statusTextView);
		statusView.setText(text);
	}
	
	private void setFileSizeText(String fileSizeText) {
    	TextView sizeView = (TextView)findViewById(R.id.fileSizeTextView);
    	sizeView.setText(fileSizeText);
	}
	
	private void setFileNameText(String fileNameText) {
    	TextView view = (TextView)findViewById(R.id.fileNameTextView);
    	view.setText(fileNameText);
	}
	
	private void setContentTypeText(String contentTypeText) {
    	TextView contentTypeView = (TextView)findViewById(R.id.contentTypeTextView);
    	contentTypeView.setText(contentTypeText);
	}
	
	private void setTextForAllFields(String sizeText, String fileNameText, String contentTypeText) {
		setFileSizeText(sizeText);
		setFileNameText(fileNameText);
		setContentTypeText(contentTypeText);
	}
	
	private void setBeamingText(String text) {
		TextView beamingView = (TextView)findViewById(R.id.beamingFileTextView);
		beamingView.setText(text);
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
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDialogModeEnabled = preferences.getBoolean("use_dialog_theme", false);
        if (isDialogModeEnabled)
        	setTheme(android.R.style.Theme_Holo_Dialog);
        else
        	setTheme(android.R.style.Theme_Holo);
    	
        super.onCreate(savedInstanceState);
        
        if (isDialogModeEnabled)
        	requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.activity_main);

        boolean enableAds = preferences.getBoolean("enable_ads", true);
        toggleAds((enableAds && !isDialogModeEnabled));
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        
        System.out.println(extras.keySet());
        
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
        	setNfcNotAvailable();
        	return;  // NFC not available on this device
        } else {
        	if (!nfcAdapter.isEnabled()) {
        		setStatus(getString(R.string.nfc_turned_off));
        	}
        }

        // if this is from the share menu
        if (Intent.ACTION_SEND.equals(action)) {   
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
            	Uri fileUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            	
            	Log.d("FileBeam", fileUri.toString());
            		
            	setTextForAllFields(getHumanReadableFileSize(fileUri.getPath()),
            			getFileNameByUri(fileUri), intent.getType());
            	
            	final Drawable icon = getImageForFile(fileUri, intent);
            	if (icon != null) {
            		ImageView imageView =  (ImageView)findViewById(R.id.defaultIconView);
            		imageView.setImageDrawable(icon);
            	}
            	
                nfcAdapter.setBeamPushUris(new Uri[] {fileUri}, this);
                nfcAdapter.setOnNdefPushCompleteCallback(this, this); 
            } else {
                String text = (String) extras.getCharSequence(Intent.EXTRA_TEXT);
                NdefMessage message;
                
                setTextForAllFields("", text, "plain/text");
                setBeamingText(getString(R.string.beaming_text));
                
                try {
                  @SuppressWarnings("unused")
				URL url = new URL(text);
                  
                  NdefRecord uriRecord = new NdefRecord(
                		    NdefRecord.TNF_ABSOLUTE_URI ,
                		    text.getBytes(Charset.forName("US-ASCII")),
                		    new byte[0], new byte[0]);
                  
                  message = new NdefMessage(uriRecord);
                } catch (MalformedURLException e) {
                    NdefRecord record = createTextRecord(text, Locale.getDefault(), true);
                    message = new NdefMessage(record);
                }
                

                nfcAdapter.setNdefPushMessage(message, this);
                nfcAdapter.setOnNdefPushCompleteCallback(this, this); 
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
        	setBeamingText(getString(R.string.beaming_files));
        	ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    		
        	long size = 0;
        	for (Uri uri: fileUris) {
        		Log.d("FileBeam", uri.toString());
        		size = size + getFileSize(uri.getPath());
        	}
        	
        	Uri[] uriList = fileUris.toArray(new Uri[0]);
        	
        	String fileNameText;
        	String fileSizeText;
        	String fileTypeText;
        	
        	if (uriList.length == 1) {
        		Uri fileUri = uriList[0];
        		fileNameText = getFileNameByUri(fileUri);
        		fileSizeText = getHumanReadableFileSize(fileUri.getPath());
        		fileTypeText = getMimeType(fileUri.getPath());
        	} else {
        		fileNameText = getString(R.string.multiple_files);
        		fileSizeText = humanReadableByteCount(size, true);
        		fileTypeText = getString(R.string.multiple_file_types);
        	}
        	
        	setTextForAllFields(fileSizeText, fileNameText, fileTypeText);
        	
            nfcAdapter.setBeamPushUris(uriList, this);
            nfcAdapter.setOnNdefPushCompleteCallback(this, this); 
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		runOnUiThread(new Runnable() {
		     public void run() {
		    	 setStatus(getString(R.string.file_sent));
		    }
		});
	}
    
	private void toggleAds(boolean enable) {
		if (enable) {
			showAd();
		} else {
			hideAd();
		}
	}
	 
	private void showAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(true);
				adLayout.setVisibility(View.VISIBLE);
				adLayout.loadAd(new AdRequest());
			}
		});
	}
	 
	 
	private void hideAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(false);
				adLayout.setVisibility(View.GONE);
			}
		});
	}
}
