/*
	Copyright 2012 Twitter
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	   http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.clutch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.util.Log;

public class ClutchConf {
	private static final String TAG = "ClutchConf";
	private static Context context;
	private static JSONObject conf = null;
	
	public static void setup(Context ctx) {
		context = ctx;
	}

	public static void setConf(JSONObject inConf) {
		conf = inConf;
	}

	public static JSONObject getConf() {
		if(conf != null) {
			return conf;
		}
		
		String versionName = "";
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Could not get bundle version");
			e.printStackTrace();
		}
		
		// First we look for a saved plist file that ClutchSync creates
		File dir = context.getDir("_clutch" + versionName, 0);
		File clutch = null;
		for(File f : dir.listFiles()) {
			if(f.getName().equals("clutch.json")) {
				clutch = f;
				break;
			}
		}
		
		// If we found it, then parse it and set ourselves, then return the data.
		if(clutch != null) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(clutch));
				StringBuffer sb = new StringBuffer();
				String line = br.readLine();
				while(line != null) {
					sb.append(line);
					line = br.readLine();
				}
				conf = new JSONObject(sb.toString());
				br.close();
				return conf;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		// If we didn't find it in the filesystem, check in the assets
		final AssetManager mgr = context.getAssets();
		String asset = ClutchUtils.findAsset(mgr, "./", "clutch.json");
		if(asset == null) {
			conf = new JSONObject();
			return conf;
		}
		
		try {
			InputStream fin = mgr.open(asset);
			String data = new Scanner(fin).useDelimiter("\\A").next();
			conf = new JSONObject(data);
			return conf;
		} catch (java.util.NoSuchElementException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		conf = new JSONObject();
		
		return conf;
	}
	
	public static int getVersion() {
		return getConf().optInt("_version", -1);
	}
	
	
}
