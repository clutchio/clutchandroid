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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.util.Log;

import com.clutch.ClutchStats.ABRow;

public class ClutchAB {
	private static final String TAG = "ClutchAB";
	private static ClutchStats stats = null;
	private static Context context = null;
	private static Random random = new Random();
	
	private static void sendABLogs() {
		ArrayList<ABRow> logs = stats.getABLogs();
		if(logs.size() == 0) {
			return;
		}
		final ABRow lastRow = logs.get(logs.size() - 1);
		JSONArray jsonLogs = new JSONArray();
		for(ABRow row : logs) {
			JSONObject rowObj = new JSONObject();
			try {
				rowObj.put("uuid", row.uuid);
				rowObj.put("ts", row.ts);
				rowObj.put("data", row.data);
			} catch (JSONException e1) {
				Log.e(TAG, "Could not properly encode the AB logs into JSON for upload to Clutch. Discarding the row."); // TODO: Don't discard the row.
				continue;
			}
			jsonLogs.put(rowObj);
		}
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("logs", jsonLogs);
		params.put("guid", ClutchAPIClient.getFakeGUID());
		ClutchAPIClient.callMethod("send_ab_logs", params, new ClutchAPIResponseHandler() {
			@Override
			public void onSuccess(JSONObject response) {
				if("ok".equals(response.optString("status"))) {
					stats.deleteABLogs(lastRow.ts);
				} else {
					Log.e(TAG, "Failed to send the Clutch AB logs to the server.");
				}
			}
			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				Log.e(TAG, "Failed to send AB logs to Clutch: " + errorResponse);
			}
		});
	}
	private static void downloadABMeta() {
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("guid", ClutchAPIClient.getFakeGUID());
		ClutchAPIClient.callMethod("get_ab_metadata", params, new ClutchAPIResponseHandler() {
			@Override
			public void onSuccess(JSONObject response) {
				JSONObject metadata = response.optJSONObject("metadata");
				try {
					FileOutputStream fos = context.openFileOutput("__clutchab.json", Context.MODE_PRIVATE);
					fos.write(metadata.toString().getBytes());
					fos.flush();
					fos.close();
				} catch (FileNotFoundException e) {
					Log.e(TAG, "Could not open the Clutch AB metadata file for output");
				} catch (IOException e) {
					Log.e(TAG, "Could not write to the Clutch AB metadata file");
				}
			}
			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				Log.e(TAG, "Failed to connect to the Clutch server to send AB logs: " + errorResponse);
			}
		});
	}
	public static void setup(Context inContext, String appKey, String rpcUrl) {
		context = inContext;
		stats = new ClutchStats(inContext);
		ClutchAPIClient.setup(context, appKey, rpcUrl);
		ClutchConf.setup(context);
		sendABLogs();
		downloadABMeta();
	}
	public static void onResume() {
		sendABLogs();
		downloadABMeta();
	}
	public static void onPause() {
		sendABLogs();
	}
	private static JSONObject getLatestMeta() {
		// TODO: Cache
		try {
			FileInputStream fis = context.openFileInput("__clutchab.json");
			StringBuffer strContent = new StringBuffer("");
			int ch;
			while( (ch = fis.read()) != -1) {
				strContent.append((char)ch);
			}
			fis.close();
			return new JSONObject(new JSONTokener(strContent.toString()));
		} catch (FileNotFoundException e) {
			return new JSONObject();
		} catch (JSONException e) {
			return new JSONObject();
		} catch (IOException e) {
			return new JSONObject();
		}
	}
	private static int weightedChoice(List<Double> weights) {
		double total = 0;
		int winner = 0;
		for(int i = 0; i < weights.size(); ++i) {
			double weight = weights.get(i).doubleValue();
			total += weight;
			if(random.nextDouble() * total < weight) {
				winner = i;
			}
		}
		if(random.nextDouble() <= total) {
			return winner;
		}
		return -1;
	}
	private static int cachedChoice(String name, List<Double> weights) {
		int resp = stats.getCachedChoice(name);
		if(resp == -1) {
			resp = weightedChoice(weights);
			stats.setCachedChoice(name, resp);
		}
		return resp;
	}
	private static List<Double> doubleJSONArrayToList(JSONArray ary) {
		ArrayList<Double> resp = new ArrayList<Double>(ary.length());
		for(int i = 0; i < ary.length(); ++i) {
			resp.add(ary.optDouble(i));
		}
		return resp;
	}
	public static void test(String name, ClutchABDataTest testInstance) {
		JSONObject metaMeta = getLatestMeta();
		if(metaMeta.length() == 0) {
			stats.testFailure(name, "no-meta");
			stats.setNumChoices(name, 0, true);
			return;
		}
		JSONObject meta = metaMeta.optJSONObject(name);
		if(meta == null) {
			stats.testFailure(name, "no-meta-name");
			stats.setNumChoices(name, 0, true);
			return;
		}
		JSONArray weights = meta.optJSONArray("weights");
		JSONArray allData = meta.optJSONArray("data");
		if(allData == null || weights == null) {
			stats.testFailure(name,  "no-data");
			return;
		}
		if(weights.length() == 0) {
			return;
		}
		int choice = cachedChoice(name, doubleJSONArrayToList(weights));
		stats.testChosen(name, choice, weights.length());
		if(choice == -1) {
			return;
		}
		JSONObject data = allData.optJSONObject(choice);
		testInstance.action(data);
	}
	public static void test(String name, ClutchABTest testInstance) {
		JSONObject metaMeta = getLatestMeta();
		Method m[] = testInstance.getClass().getDeclaredMethods();
		if(metaMeta.length() == 0) {
			stats.testFailure(name, "no-meta");
			stats.setNumChoices(name, m.length, false);
			return;
		}
		JSONObject meta = metaMeta.optJSONObject(name);
		if(meta == null) {
			stats.testFailure(name, "no-meta-name");
			stats.setNumChoices(name, m.length, false);
			return;
		}
		JSONArray weights = meta.optJSONArray("weights");
		if(weights == null) {
			stats.testFailure(name, "no-weights");
			stats.setNumChoices(name, m.length, false);
			return;
		}
		int choice = cachedChoice(name, doubleJSONArrayToList(weights));
		stats.testChosen(name, choice, m.length);
		switch(choice) {
			case 0: testInstance.A();
					break;
			case 1: testInstance.B();
					break;
			case 2: testInstance.C();
					break;
			case 3: testInstance.D();
					break;
			case 4: testInstance.E();
					break;
			case 5: testInstance.F();
					break;
			case 6: testInstance.G();
					break;
			case 7: testInstance.H();
					break;
			case 8: testInstance.I();
					break;
			case 9: testInstance.J();
					break;
		}
	}
	public static void goalReached(String name) {
		stats.goalReached(name);
	}
}
