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

import android.content.Context;

public class Clutch {
	private static ClutchStats clutchStats = null;
	
	public static void setup(Context context, String appKey, String tunnelUrl, String rpcUrl) {
		clutchStats = new ClutchStats(context);
		ClutchAPIClient.setup(context, appKey, rpcUrl);
	    ClutchConf.setup(context);
	    ClutchSync.setup(context, appKey, tunnelUrl);
	    ClutchSync.sync(clutchStats);
	}
	
	public static void onResume() {
		ClutchSync.foreground(clutchStats);
	}
	
	public static void onPause() {
		ClutchSync.background(clutchStats);
	}
	
	public static ClutchStats getStats() {
		return clutchStats;
	}
}
