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

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ClutchStats extends SQLiteOpenHelper {
	private static final String TAG = "ClutchStats";
	private static final String DATABASE_NAME = "clutch.db";
	private static final int DATABASE_VERSION = 1;
	
	public class StatRow {
		public String uuid;
		public double ts;
		public String action;
		public JSONObject data;
		
		public StatRow(String uuid, double ts, String action, JSONObject data) {
			this.uuid = uuid;
			this.ts = ts;
			this.action = action;
			this.data = data;
		}
	}
	
	public class ABRow {
		public String uuid;
		public double ts;
		public JSONObject data;
		
		public ABRow(String uuid, double ts, JSONObject data) {
			this.uuid = uuid;
			this.ts = ts;
			this.data = data;
		}
	}
	
	public ClutchStats(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
	}
	
	public ClutchStats(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS stats (uuid TEXT, ts REAL, action TEXT, data TEXT)");
		db.execSQL("CREATE TABLE IF NOT EXISTS abcache (name TEXT, choice INTEGER)");
		db.execSQL("CREATE TABLE IF NOT EXISTS ablog (uuid TEXT, ts REAL, data TEXT)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
		db.execSQL("DROP TABLE IF EXISTS stats");
		db.execSQL("DROP TABLE IF EXISTS abcache");
		db.execSQL("DROP TABLE IF EXISTS ablog");
		onCreate(db);
	}
	
	public void logAction(String action, Map<String, ?>data) {
		SQLiteDatabase db = getWritableDatabase();
		Object[] args = {
			ClutchUtils.getUUID(),
			System.currentTimeMillis() / 1000.0,
			action,
			new JSONObject(data).toString()
		};
		db.execSQL("INSERT INTO stats (uuid, ts, action, data) VALUES (?, ?, ?, ?)", args);
		db.close();
	}
	
	public void logAction(String action) {
		logAction(action, null);
	}
	
	public ArrayList<StatRow> getLogs() {
		SQLiteDatabase db = getReadableDatabase();
		String[] args = {};
		ArrayList<StatRow> res = new ArrayList<StatRow>();
		Cursor cur = db.rawQuery("SELECT uuid, ts, action, data FROM stats ORDER BY ts", args);
		cur.moveToFirst();
		while(!cur.isAfterLast()) {
			String uuid = cur.getString(0);
			double ts = cur.getDouble(1);
			String action = cur.getString(2);
			JSONObject data;
			try {
				data = new JSONObject(cur.getString(3));
			} catch (JSONException e) {
				Log.w(TAG, "Could not serialize to JSON: " + cur.getString(3));
				cur.moveToNext();
				continue;
			}
			res.add(new StatRow(uuid, ts, action, data));
			cur.moveToNext();
		}
		db.close();
		return res;
	}
	
	public void deleteLogs(double beforeOrEqualTo) {
		SQLiteDatabase db = this.getWritableDatabase();
		Object[] args = {beforeOrEqualTo};
		db.execSQL("DELETE FROM stats WHERE ts <= ?", args);
		db.close();
	}

	private void logABData(JSONObject data) {
		SQLiteDatabase db = getWritableDatabase();
		Object[] args = {
			ClutchUtils.getUUID(),
			System.currentTimeMillis() / 1000.0,
			data.toString()
		};
		db.execSQL("INSERT INTO ablog (uuid, ts, data) VALUES (?, ?, ?)", args);
		db.close();
	}
	
	public void testFailure(String name, String type) {
		JSONObject data = new JSONObject();
		try {
			data.put("action", "failure");
			data.put("name", name);
			data.put("type", type);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create JSON for insert into testFailure (name: " + name + ", type: " + type + ")");
			return;
		}
		logABData(data);
	}

	public void testChosen(String name, int choice, int numChoices) {
		JSONObject data = new JSONObject();
		try {
			data.put("action", "test");
			data.put("name", name);
			data.put("choice", choice);
			data.put("num_choices", numChoices);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create JSON for insert into testChosen (name: " + name + ", choice: " + choice + ", numChoies: " + numChoices + ")");
			return;
		}
		logABData(data);
	}

	public void goalReached(String name) {
		JSONObject data = new JSONObject();
		try {
			data.put("action", "goal");
			data.put("name", name);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create JSON for insert into goalReached (name: " + name + ")");
			return;
		}
		logABData(data);
	}
	
	public void setNumChoices(String name, int numChoices, boolean hasData) {
		JSONObject data = new JSONObject();
		try {
			data.put("action", "num-choices");
			data.put("name", name);
			data.put("num_choices", numChoices);
			data.put("has_data", hasData);
		} catch (JSONException e) {
			Log.e(TAG, "Could not create JSON for insert into testChosen (name: " + name + ", numChoices: " + numChoices + ")");
			return;
		}
		logABData(data);
	}

	public ArrayList<ABRow> getABLogs() {
		SQLiteDatabase db = getReadableDatabase();
		String[] args = {};
		ArrayList<ABRow> res = new ArrayList<ABRow>();
		Cursor cur = db.rawQuery("SELECT uuid, ts, data FROM ablog ORDER BY ts", args);
		cur.moveToFirst();
		while(!cur.isAfterLast()) {
			String uuid = cur.getString(0);
			double ts = cur.getDouble(1);
			JSONObject data;
			try {
				data = new JSONObject(cur.getString(2));
			} catch (JSONException e) {
				Log.w(TAG, "Could not serialize to JSON: " + cur.getString(3));
				cur.moveToNext();
				continue;
			}
			res.add(new ABRow(uuid, ts, data));
			cur.moveToNext();
		}
		db.close();
		return res;
	}

	public void deleteABLogs(double ts) {
		SQLiteDatabase db = this.getWritableDatabase();
		Object[] args = {ts};
		db.execSQL("DELETE FROM ablog WHERE ts <= ?", args);
		db.close();
	}

	public int getCachedChoice(String name) {
		int resp = -1;
		SQLiteDatabase db = getReadableDatabase();
		String[] args = {name};
		Cursor cur = db.rawQuery("SELECT choice FROM abcache WHERE name = ?", args);
		cur.moveToFirst();
		while(!cur.isAfterLast()) {
			resp = cur.getInt(0);
			cur.moveToNext();
		}
		db.close();
		return resp;
	}

	public void setCachedChoice(String name, int choice) {
		SQLiteDatabase db = this.getWritableDatabase();
		Object[] args = {name, choice};
		db.execSQL("INSERT INTO abcache (name, choice) VALUES (?, ?)", args);
		db.close();
	}

}
