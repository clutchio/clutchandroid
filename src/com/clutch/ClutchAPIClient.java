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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

public class ClutchAPIClient {
	private static final String TAG = "ClutchAPIClient";
	private static final String VERSION = "2";
	private static String appKey = null;
	private static String rpcUrl = null;
	private static String versionName = "UNKNOWN";
	private static String fakeGUID = null;
	private static long callId = 0;
	private static Context context;
	private static AndroidHttpClient client = null;
	
	public static void setup(Context inContext, String inAppKey, String inRpcUrl) {
		context = inContext;
		client = AndroidHttpClient.newInstance("Clutch-Android", context);
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Could not get bundle version");
			e.printStackTrace();
		}
		appKey = inAppKey;
		rpcUrl = inRpcUrl;
		SharedPreferences prefs = context.getSharedPreferences("clutchab" + context.getPackageName(), Context.MODE_PRIVATE);
		fakeGUID = prefs.getString("fakeGUID", null);
		if(fakeGUID == null) {
			fakeGUID = ClutchUtils.getUUID();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("fakeGUID", fakeGUID);
			editor.commit();
		}
	}
	
	public static String getFakeGUID() {
		return fakeGUID;
	}
	
	private static void sendRequest(String url, boolean post, JSONObject payload, String version, final ClutchAPIResponseHandler responseHandler) {
		BasicHeader[] headers = {
			new BasicHeader("X-App-Version", version),
			new BasicHeader("X-UDID", ClutchUtils.getUDID()),
			new BasicHeader("X-API-Version", VERSION),
			new BasicHeader("X-App-Key", appKey),
			new BasicHeader("X-Bundle-Version", versionName),
			new BasicHeader("X-Platform", "Android"),
		};
		StringEntity entity = null;
		try {
			entity = new StringEntity(payload.toString());
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Could not encode the JSON payload and attach it to the request: " + payload.toString());
			return;
		}
		HttpRequestBase request = null;
		if(post) {
			request = new HttpPost(url);
			((HttpEntityEnclosingRequestBase)request).setEntity(entity);
			request.setHeaders(headers);
		} else {
			request = new HttpGet(url);
		}
		
		class StatusCodeAndResponse {
			public int statusCode;
			public String response; 
			
			public StatusCodeAndResponse(int statusCode, String response) {
				this.statusCode = statusCode;
				this.response = response;
			}
		}
		
		new AsyncTask<HttpRequestBase, Void, StatusCodeAndResponse>() {
			@Override
			protected StatusCodeAndResponse doInBackground(HttpRequestBase... requests) {
				try {
					HttpResponse resp = client.execute(requests[0]);
					
					HttpEntity entity = resp.getEntity();
					InputStream inputStream = entity.getContent();
					
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					
					int readBytes = 0;
					byte[] sBuffer = new byte[512];
					while ((readBytes = inputStream.read(sBuffer)) != -1) {
					    content.write(sBuffer, 0, readBytes);
					}
					
					inputStream.close();
					  
					String response = new String(content.toByteArray());
					
					content.close();
					  
					return new StatusCodeAndResponse(resp.getStatusLine().getStatusCode(), response);
				} catch (IOException e) {
					if(responseHandler instanceof ClutchAPIDownloadResponseHandler) {
						((ClutchAPIDownloadResponseHandler)responseHandler).onFailure(e, "");
					} else {
						responseHandler.onFailure(e, null);
					}
				}
				return null;
			}
			
			@Override
			protected void onPostExecute(StatusCodeAndResponse resp) {
				if(responseHandler instanceof ClutchAPIDownloadResponseHandler) {
					if(resp.statusCode == 200) {
						((ClutchAPIDownloadResponseHandler)responseHandler).onSuccess(resp.response);
					} else {
						((ClutchAPIDownloadResponseHandler)responseHandler).onFailure(null, resp.response);
					}
				} else {
					if(resp.statusCode == 200) {
						responseHandler.handleSuccessMessage(resp.response);
					} else {
						responseHandler.handleFailureMessage(null, resp.response);
					}
				}
			}
		}.execute(request);
	}
	
	public static void callMethod(String methodName, Map<String, ?> params) {
		callMethod(methodName, params, null);
	}

	public static void callMethod(String methodName, Map<String, ?> params, ClutchAPIResponseHandler responseHandler) {
		JSONObject payload = new JSONObject();
		if(params == null) {
			params = new HashMap<String, String>();
		}
		try {
			payload.put("method", methodName);
			payload.put("params", new JSONObject(params));
			payload.put("id", ++callId); // Not thread safe, but not a big deal.
		} catch (JSONException e) {
			Log.e(TAG, "Calling " + methodName + " with args '" + params.toString() + "' failed.");
			responseHandler.onFailure(e, payload);
			return;
		}
		String url = rpcUrl + (rpcUrl.endsWith("/") ? "rpc/" : "/rpc/");
		sendRequest(url, true, payload, "" + ClutchConf.getVersion(), responseHandler);
	}

	public static void downloadFile(String fileName, String version, final ClutchAPIDownloadResponseHandler responseHandler) {
		JSONObject payload = new JSONObject();
		
		try {
			// Build params first
			JSONObject params = new JSONObject();
			params.put("filename", fileName);
			// Put params inside payload
			payload.put("params", params);
			payload.put("method", "get_file");
			payload.put("id", ++callId); // Not thread safe, but not a big deal.
		} catch (JSONException e) {
			Log.e(TAG, "Getting " + fileName + " download redirect failed.");
			responseHandler.onFailure(e, fileName);
			return;
		}
				
		String tmpRpcUrl = rpcUrl + (rpcUrl.endsWith("/") ? "rpc/" : "/rpc/");
		sendRequest(tmpRpcUrl, true, payload, version, new ClutchAPIResponseHandler() {
			@Override
			public void onSuccess(JSONObject response) {
				String url = response.optString("url");
				if(url == null) {
					Log.e(TAG, "The server response didn't include a url: " + response);
					return;
				}
				sendRequest(url, false, null, "" + ClutchConf.getVersion(), responseHandler);
			}

			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				Log.e(TAG, "Failed to get logs to Clutch: " + errorResponse);
			}
		});
	}
	
}
