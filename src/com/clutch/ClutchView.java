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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class ClutchView extends WebView {
	private static final String TAG = "ClutchWebView";
	private String slug = null;
	private File dir = null;
	private ClutchViewMethodDispatcher dispatcher = null;
	private ProgressDialog loading = null;
	
	public ClutchView(Context context) {
		super(context);
		this.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		this.setHorizontalScrollBarEnabled(false);
		this.clutchInit();
	}
	
	public ClutchView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		this.setHorizontalScrollBarEnabled(false);
		this.clutchInit();
	}
	
	public ClutchView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		this.setHorizontalScrollBarEnabled(false);
		this.clutchInit();
	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private void clutchInit() {
		loading = new ProgressDialog(this.getContext());
		loading.hide();
		
		this.setWebChromeClient(new WebChromeClient() {
			// TODO: Clutch Debug Toolbar
			@Override
			public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
				if(!"methodCalled".equals(defaultValue)) {
					return false;
				}
				JSONObject jsonMessage = null;
				String methodName = null;
				String callbackId = null;
				JSONObject params = null;
				try {
					jsonMessage = new JSONObject(message);
					methodName = jsonMessage.getString("method");
					callbackId = jsonMessage.getString("callbackNum");
					params = jsonMessage.getJSONObject("args");
				} catch (JSONException e) {
					Log.e(TAG, "Could not decode JSON: " + message);
					result.cancel();
					return true;
				}
				if(methodName.equals("clutch.loading.begin")) {
					String loadingMessage = params.optString("text");
					if(loadingMessage == null) {
						loadingMessage = "Loading...";
					} else if("null".equals(loadingMessage)) {
						loadingMessage = "Loading...";
					}
					loading.setMessage(loadingMessage);
					loading.show();
				} else if(methodName.equals("clutch.loading.end")) {
					loading.hide();
				}
				ClutchCallback callback = null;
				if(!"0".equals(callbackId)) {
					callback = new ClutchCallback(ClutchView.this, callbackId);
				}
				dispatcher.methodCalled(methodName, params, callback);
				result.cancel();
				return true;
			}
		});
		
		ClutchSync.addClutchView(this);
		WebSettings settings = this.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setSupportZoom(true);
		settings.setAppCacheEnabled(false);
	}
	
	public void callMethod(String methodName) {
		this.loadUrl("javascript:Clutch.Core.callMethod('" + methodName + "', {})");
	}
	
	public void callMethod(String methodName, Map<String, ?> params) {
		String encodedParams = ClutchUtils.jsonEncode(params);
		if(encodedParams == null) {
			Log.e(TAG, "Params (" + params + ") couldn't be encoded and sent to Clutch JavaScript.");
			return;
		}
		this.loadUrl("javascript:Clutch.Core.callMethod('" + methodName + "', " + encodedParams + ")");
	}
	
	public void close() {
		ClutchSync.removeClutchView(this);
	}
	
	protected void finalize() throws Throwable {
		ClutchSync.removeClutchView(this);
		super.finalize();
	}
	
	public void configure(String slug, ClutchViewMethodDispatcher dispatcher) {
		this.slug = slug;
		this.dispatcher = dispatcher;
	}
	
	private File getDir() {
		if(this.dir != null) {
			return this.dir;
		}
		
		String versionName = "";
		try {
			versionName = this.getContext().getPackageManager().getPackageInfo(this.getContext().getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Could not get bundle version");
			e.printStackTrace();
		}
		File dir = this.getContext().getDir("_clutch" + versionName, 0);
		File clutch = null;
		for(File f : dir.listFiles()) {
			if(f.getName().equals("clutch.json")) {
				clutch = f;
				break;
			}
		}
		
		if(clutch != null) {
			this.dir = clutch.getParentFile();
			return this.dir;
		}
		
		final AssetManager mgr = this.getContext().getAssets();
		String asset = ClutchUtils.findAsset(mgr, "", "clutch.json");
		if(asset == null) {
			Log.e(TAG, "Could not find a Clutch directory in either internal storage or assets.");
			return null;
		}
		
		// Coerce the asset-relative clutch.json file path to a File obj
		asset = asset.replace("/clutch.json", "/").replace("./", "");
		this.dir = (new File("/android_asset/" + asset, "clutch.json")).getParentFile();
		return this.dir;
	}
	
	public void contentChanged() {
		this.dir = null;
		this.render();
	}
	
	public void render() {
		if(slug == null) {
			Log.e(TAG, "Slug must be set on ClutchWebView before rendering or calling contentChanged");
			return;
		}
		File d = getDir();
		if(d == null) {
			return;
		}
		File page = new File(d, slug + "/index.html");
		this.loadUrl("file://" + page.getAbsolutePath());
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	    int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
	    double zoomDouble = parentWidth / 640.0;
	    int zoom = (int)Math.floor(zoomDouble * 100.0);
	    this.setInitialScale(zoom);
	    String js = "javascript:(function() { var d = document.getElementById('view'); if(d) { d.setAttribute('content', 'user-scalable=yes, width=device-width, minimum-scale=" + zoomDouble + ", maximum-scale=" + zoomDouble + "'); } else { var e = document.createElement('meta'); e.setAttribute('name', 'viewport'); e.setAttribute('id', 'view'); e.setAttribute('content', 'user-scalable=yes, width=device-width minimum-scale=" + zoomDouble + ", maximum-scale=" + zoomDouble + "'); document.getElementsByTagName('head')[0].appendChild(e); }})();";
	    this.loadUrl(js);
	}
	
	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		
		ClutchStats stats = Clutch.getStats();
		HashMap<String, String> data = new HashMap<String, String>();
		data.put("slug", this.slug);
		if(direction > 0) {
			stats.logAction("viewDidAppear", data);
		} else {
			stats.logAction("viewDidDisappear", data);
		}
	}

}
