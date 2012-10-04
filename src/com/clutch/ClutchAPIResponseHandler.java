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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ClutchAPIResponseHandler {
	public void onSuccess(JSONObject response) {
		
	}
	
	public void onFailure(Throwable e, JSONObject errorResponse) {
		
	}
	
    protected void handleSuccessMessage(String responseBody) {        
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONObject error = jsonResponse.optJSONObject("error");
            if(error != null) {
            	onFailure(null, error);
            	return;
            }
            onSuccess(jsonResponse.getJSONObject("result"));
        } catch(JSONException e) {
            try {
				onFailure(e, new JSONObject(responseBody));
			} catch (JSONException e1) {
				onFailure(e, null);
			}
        }
	}
	
	protected Object parseResponse(String responseBody) throws JSONException {
        return new JSONTokener(responseBody).nextValue();
    }
	
    protected void handleFailureMessage(Throwable e, String responseBody) {
        if (responseBody == null) {
        	onFailure(e, (JSONObject)null);
        } else {
        	try {
	            JSONObject jsonResponse = new JSONObject(responseBody);
	            onFailure(e, (JSONObject)jsonResponse);
	        } 
	        catch(JSONException ex) {
	            onFailure(e, (JSONObject)null);
	        }
        }
    }
}
