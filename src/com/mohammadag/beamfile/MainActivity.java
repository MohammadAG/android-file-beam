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
import java.util.List;
import java.util.Locale;

import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;

public class MainActivity extends Activity implements OnNdefPushCompleteCallback {
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1024 : 1000;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	
	public String getFileNameByUri(Uri uri)
	{
	    String fileName = uri.toString(); //default fileName
	    Uri filePathUri = uri;
	    if (uri.getScheme().toString().compareTo("content")==0)
	    {      
	    	Cursor cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null);
	        if (cursor.moveToFirst())
	        {
	            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);//Instead of "MediaStore.Images.Media.DATA" can be used "_data"
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

        // Setting Dialog Title
        alertDialog.setTitle(R.string.about_dialog_title);
 
        // Setting Dialog Message
        alertDialog.setMessage(R.string.about_text);
 
        // Showing Alert Message
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
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_about:
                showAbout();
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
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
            	
            	File f = new File(fileUri.getPath());
            	long size = f.length();
            	
            	TextView sizeView = (TextView)findViewById(R.id.fileSizeTextView);
            	sizeView.setText(humanReadableByteCount(size, true));
            	
            	TextView view = (TextView)findViewById(R.id.fileNameTextView);
            	view.setText(getFileNameByUri(fileUri));
            	
            	TextView contentTypeView = (TextView)findViewById(R.id.contentTypeTextView);
            	contentTypeView.setText(intent.getType());
            		
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
                
                try {
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
    
}
