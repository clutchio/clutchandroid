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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.res.AssetManager;
import android.provider.Settings.Secure;


public class ClutchUtils {
	public static String getUDID() {
		return Secure.ANDROID_ID;
	}
	
	public static String getUUID() {
		return UUID.randomUUID().toString();
	}
	
	public static String findAsset(AssetManager mgr, String path, String stop) {
		String files[] = null;
		try {
			files = mgr.list(path);
		} catch (IOException e) {
			return null;
		}
		if(files != null) {
			for(String file : files) {
				String subPath = path;
				if(subPath.length() > 0) {
					subPath += "/" + file;
				} else {
					subPath += file;
				}
				if(file.indexOf(stop) != -1) {
					return subPath;
				} else {
					String resp = findAsset(mgr, subPath, stop);
					if(resp != null) {
						return resp;
					}
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings("rawtypes")
	public static String jsonEncode(Object result) {
		if(result instanceof JSONObject) {
			return ((JSONObject)result).toString();
		} else if(result instanceof JSONArray) {
			return ((JSONArray)result).toString();
		}  else if(result instanceof Map) {
			return new JSONObject((Map)result).toString();
		} else if(result instanceof List) {
			return new JSONArray((List)result).toString();
		} else if(result instanceof Integer) {
			return ((Integer)result).toString();
		} else if(result instanceof Double) {
			return ((Double)result).toString();
		} else if(result instanceof Float) {
			return ((Float)result).toString();
		} else if(result instanceof String) {
			String str = (String)result;
			return "\"" + str.replace("\"", "\\\"") + "\"";
		} else if(result instanceof Boolean) {
			if(((Boolean)result).booleanValue()) {
				return "true";
			}
			return "false";
		} else if(result == null) {
			return "null";
		}
		return null;
	}

}
