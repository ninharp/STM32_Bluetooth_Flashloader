package de.sauernetworks.stm32_bluetooth_flashloader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AboutActivity extends Activity {
	private static Context mContext = null;

	/*
    public AboutDialog(Context context) {
        super(context);
        mContext = context;
    }*/

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialog_about);
		
		TextView legal = (TextView)findViewById(R.id.about_legal_text);
		legal.setText(readRawTextFile(R.raw.legal));

		TextView info = (TextView)findViewById(R.id.about_info_text);
		info.setText(Html.fromHtml(readRawTextFile(R.raw.info)));
		info.setTextColor(Color.BLACK);
		Linkify.addLinks(info, Linkify.WEB_URLS);
	}
	
	public String readRawTextFile(int id) {
		InputStream inputStream = getResources().openRawResource(id);
		InputStreamReader in = new InputStreamReader(inputStream);
		BufferedReader buf = new BufferedReader(in);
		String line;
		StringBuilder text = new StringBuilder();
		try {
			while (( line = buf.readLine()) != null) {
				String version = "1.0";// ((MainActivity) mContext).getVersion();
				line = line.replaceAll("%SW_VERSION%", version);
				text.append(line);
			}
		} catch (IOException e) {
			return null;
		}
		return text.toString();
	}
}