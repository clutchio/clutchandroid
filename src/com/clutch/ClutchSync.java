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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.util.Log;

import com.clutch.ClutchStats.StatRow;

public class ClutchSync {
	private static final String TAG = "ClutchSync";
	@SuppressWarnings("unused")
	private static String appKey;
	@SuppressWarnings("unused")
	private static String cursor = null;
	@SuppressWarnings("unused")
	private static String tunnelUrl = null;
	@SuppressWarnings("unused")
	private static boolean shouldWatchForChanges = false;
	private static boolean pendingReload = false;
	private static boolean thisIsHappening = false;
	private static int currentFile = 0; // HACK
	private static boolean newFilesDownloaded = false; // HACK
	private static JSONObject conf = null; // HACK
	private static ArrayList<ClutchView> clutchViews = new ArrayList<ClutchView>();
	private static Context context;
	
	public static void setup(Context ctx, String inAppKey, String inTunnelUrl) {
		context = ctx;
		appKey = inAppKey;
		tunnelUrl = inTunnelUrl;
	}

	public static void sync(ClutchStats clutchStats) {
		if(thisIsHappening) {
			return;
		}
		thisIsHappening = true;
		if(pendingReload) {
			pendingReload = false;
			for(ClutchView clutchView : clutchViews) {
				clutchView.contentChanged();
			}
		}
		ClutchAPIClient.callMethod("sync", null, new ClutchAPIResponseHandler() {
			@Override
			public void onSuccess(JSONObject response) {
				final AssetManager mgr = context.getAssets();
				
				File parentCacheDir = context.getCacheDir();
				final File tempDir;
				try {
					tempDir = File.createTempFile("clutchtemp", Long.toString(System.nanoTime()), parentCacheDir);
					if(!tempDir.delete()) {
						Log.e(TAG, "Could not delete temp file: " + tempDir.getAbsolutePath());
						return;
					}
					if(!tempDir.mkdir()) {
						Log.e(TAG, "Could not create temp directory: " + tempDir.getAbsolutePath());
						return;
					}
				} catch (IOException e) {
					Log.e(TAG, "Could not create temp file");
					return;
				}
				
				File cacheDir = getCacheDir();
				if(cacheDir == null) {
					try {
						if(!copyAssetDir(mgr, tempDir)) {
							return;
						}
					} catch (IOException e) {
						Log.e(TAG, "Couldn't copy the asset dir files to the temp dir: " + e);
						return;
					}
				} else {
					try {
						if(!copyDir(cacheDir, tempDir)) {
							return;
						}
					} catch (IOException e) {
						Log.e(TAG, "Couldn't copy the cache dir files to the temp dir: " + e);
						return;
					}
				}
				
				conf = response.optJSONObject("conf");
				String version = "" + conf.optInt("_version");
				newFilesDownloaded = false;
				try {
					JSONCompareResult confCompare = JSONCompare.compareJSON(ClutchConf.getConf(), conf, JSONCompareMode.NON_EXTENSIBLE);
					if(confCompare.failed()) {
						newFilesDownloaded = true;
						// This is where in the ObjC version we write out the conf, but I don't think we need to anymore
					}
				} catch (JSONException e1) {
					Log.i(TAG, "Couldn't compare the conf file with the cached conf file: " + e1);
				}
				
				File cachedFiles = new File(tempDir, "__files.json");
				JSONObject cached = null;
				if(cachedFiles.exists()) {
					StringBuffer strContent = new StringBuffer("");
					try {
						FileInputStream in = new FileInputStream(cachedFiles);
						int ch;
						while( (ch = in.read()) != -1) {
							strContent.append((char)ch);
						}
						in.close();
						cached = new JSONObject(new JSONTokener(strContent.toString()));
					} catch (IOException e) {
						Log.e(TAG, "Could not read __files.json from cache file: " + e);
					} catch (JSONException e) {
						Log.e(TAG, "Could not parse __files.json from cache file: " + e);
					}
				}
				if(cached == null) {
					cached = new JSONObject();
				}
				
				final JSONObject files = response.optJSONObject("files");
				try {
					JSONCompareResult filesCompare = JSONCompare.compareJSON(cached, files, JSONCompareMode.NON_EXTENSIBLE);
					if(filesCompare.passed()) {
						complete(tempDir, files);
						return;
					}
				} catch (JSONException e1) {
					Log.i(TAG, "Couldn't compare the file hash list with the cached file hash list: " + e1);
				}
				
				try {
					BufferedWriter bw = new BufferedWriter(new FileWriter(cachedFiles));
					bw.write(files.toString());
					bw.flush();
					bw.close();
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
				
				currentFile = 0;
				final int numFiles = files.length();
				Iterator<?> it = files.keys();
				while(it.hasNext()) {
					final String fileName = (String)it.next();
					final String hash = files.optString(fileName);
					final String prevHash = cached.optString(fileName);
					
					// If they equal, then just continue
					if(hash.equals(prevHash)) {
						if(++currentFile == numFiles) {
		                    complete(tempDir, files);
		                    return;
		                }
						continue;
					}
					
					// Looks like we've seen a new file, so we should reload when this is all done
					newFilesDownloaded = true;
										
					// Otherwise we need to download the new file
					ClutchAPIClient.downloadFile(fileName, version, new ClutchAPIDownloadResponseHandler() {
						@Override
						public void onSuccess(String response) {
							try {
								File fullFile = new File(tempDir, fileName);
								fullFile.getParentFile().mkdirs();
								fullFile.createNewFile();
								BufferedWriter bw = new BufferedWriter(new FileWriter(fullFile));
								bw.write(response);
								bw.flush();
								bw.close();
							} catch (IOException e) {
								final Writer result = new StringWriter();
								final PrintWriter printWriter = new PrintWriter(result);
								e.printStackTrace(printWriter);
								Log.e(TAG, "Tried, but could not write file: " + fileName + " : " + result);
							}

							if(++currentFile == numFiles) {
								complete(tempDir, files);
								return;
							}
						}					 
						@Override
						public void onFailure(Throwable e, String content) {
							final Writer result = new StringWriter();
							final PrintWriter printWriter = new PrintWriter(result);
							e.printStackTrace(printWriter);
							Log.e(TAG, "Error downloading file from server: " + fileName + " " + result + " " + content);
							if(++currentFile == numFiles) {
								complete(tempDir, files);
								return;
							}
						}
					});
				}
			}
			
			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				Log.e(TAG, "Failed to sync with the Clutch server: " + errorResponse);
			}
		});
		background(clutchStats);
	}

	private static void complete(File tempDir, JSONObject files) {
		String versionName = "";
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Could not get bundle version");
			e.printStackTrace();
		}
		
		if(newFilesDownloaded) {
			File dir = context.getDir("_clutch" + versionName, 0);
			
			// Write out __files.json
			OutputStream outFilesList;
			try {
				outFilesList = new FileOutputStream(new File(tempDir, "__files.json"));
				outFilesList.write(files.toString().getBytes());
				outFilesList.flush();
				outFilesList.close();
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Could not write out the Clutch files hash cache: " + e);
				thisIsHappening = false;
				return;
			} catch (IOException e) {
				Log.e(TAG, "Could not write out the Clutch files hash cache: " + e);
				thisIsHappening = false;
				return;
			}
			
			deleteDir(dir);
			if(!tempDir.renameTo(dir)) {
				Log.e(TAG, "Could not rename " + tempDir.getPath() + " to " + dir.getPath());
			}
			
			ClutchConf.setConf(null); // In ObjC we actually write the contents of the conf in setConf
			                          // Let's see if this works.
			ClutchConf.getConf();
			
			if(conf.optBoolean("_dev") || newFilesDownloaded) {
				pendingReload = true;
			}
			
			if(conf.optBoolean("_dev")) {
				for(ClutchView clutchView : clutchViews) {
					clutchView.contentChanged();
				}
				if(!conf.optBoolean("_toolbar")) {
					shouldWatchForChanges = true;
					watchForChanges();
				}
			}
		}
		
		thisIsHappening = false;
	}
	
	private static void watchForChanges() {
		// TODO: Implement long poller watching for changes
	}
	
	private static File getCacheDir() {
		String versionName = "";
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Could not get bundle version");
			e.printStackTrace();
		}
		File dir = context.getDir("_clutch" + versionName, 0);
		for(File f : dir.listFiles()) {
			if(f.getName().equals("clutch.json")) {
				return f.getParentFile();
			}
		}
		
		return null;
	}
	
	private static boolean copyAssetDir(AssetManager mgr, File tempDir) throws IOException {
		String asset = ClutchUtils.findAsset(mgr, "", "clutch.json");
		if(asset == null) {
			Log.e(TAG, "Could not find a Clutch directory in either internal storage or assets.");
			return false;
		}
		
		// All of this code to coerce the asset-relative clutch.json file path to a File obj
		asset = asset.replace("/clutch.json", "");
		return copyAssetDir(mgr, asset, tempDir);
	}
	
	private static boolean copyAssetDir(AssetManager mgr, String path, File outFile) throws IOException {
		String[] assets = mgr.list(path);
		if(assets.length == 0) {
			if(!outFile.getParentFile().exists()) {
				outFile.getParentFile().mkdirs();
			}
			if(!outFile.exists()) {
				outFile.createNewFile();
			}
			InputStream in = mgr.open(path);
			OutputStream out = new FileOutputStream(outFile);
			byte[] buffer = new byte[1024];
			int read;
			while((read = in.read(buffer)) != -1){
				out.write(buffer, 0, read);
		    }
			out.close();
			return true;
		}
		
		for(String asset : assets) {
			if(!copyAssetDir(mgr, path + "/" + asset, new File(outFile, asset))) {
				return false;
			}
		}
		
		return true;
	}

	private static boolean deleteDir(File dir) {
	    if (dir.isDirectory()) {
	        String[] children = dir.list();
	        for (int i=0; i<children.length; i++) {
	            boolean success = deleteDir(new File(dir, children[i]));
	            if (!success) {
	                return false;
	            }
	        }
	    }
	    // The directory is now empty so delete it
	    return dir.delete();
	}
	
	private static boolean copyDir(File src, File dest) throws IOException {
		if(!src.exists()) {
			Log.e(TAG, "Copy source does not exist: " + src);
			return false;
		}
		String[] files = src.list();
		if(files == null) {
			if(!dest.getParentFile().exists()) {
				dest.getParentFile().mkdirs();
			}
			if(!dest.exists()) {
				dest.createNewFile();
			}
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dest);
			byte[] buffer = new byte[1024];
			int read;
			while((read = in.read(buffer)) != -1){
				out.write(buffer, 0, read);
		    }
			in.close();
			out.close();
			return true;
		}

		for(String file : files) {
			if(!copyDir(new File(src, file), new File(dest, file))) {
				return false;
			}
		}
		
		return true;
	}
	
	public static void background(final ClutchStats clutchStats) {
		ArrayList<StatRow> logs = clutchStats.getLogs();
		if(logs.size() == 0) {
			return;
		}
		final StatRow lastRow = logs.get(logs.size() - 1);
		JSONArray jsonLogs = new JSONArray();
		for(StatRow row : logs) {
			JSONObject rowObj = new JSONObject();
			try {
				rowObj.put("uuid", row.uuid);
				rowObj.put("ts", row.ts);
				rowObj.put("action", row.action);
				rowObj.put("data", row.data);
			} catch (JSONException e1) {
				Log.e(TAG, "Could not properly encode the logs into JSON for upload to Clutch. Discarding the row."); // TODO: Don't discard the row.
				continue;
			}
			jsonLogs.put(rowObj);
		}
		HashMap<String, JSONArray> params = new HashMap<String, JSONArray>();
		params.put("logs", jsonLogs);
		ClutchAPIClient.callMethod("stats", params, new ClutchAPIResponseHandler() {
			@Override
			public void onSuccess(JSONObject response) {
				if("ok".equals(response.optString("status"))) {
					clutchStats.deleteLogs(lastRow.ts);
				} else {
					Log.e(TAG, "Failed to send the Clutch stats logs to the server.");
				}
			}
			
			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				Log.e(TAG, "Failed to send logs to Clutch: " + errorResponse);
			}
		});
	}
	
	public static void foreground(ClutchStats clutchStats) {
		sync(clutchStats);
	}
	
	public static void addClutchView(ClutchView view) {
		clutchViews.add(view);
	}
	
	public static void removeClutchView(ClutchView view) {
		try {
			clutchViews.remove(view);
		} catch (IndexOutOfBoundsException e) {
			// Don't care.
		}
	}
}
