/*
	Copyright 2011 Skyscreamer
	
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

package org.skyscreamer.jsonassert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple JSON parsing utility.
 */
public class JSONParser {
    /**
     * Takes a JSON string and returns either a {@link org.json.JSONObject} or {@link org.json.JSONArray},
     * depending on whether the string represents an object or an array.
     *
     * @param s Raw JSON string to be parsed
     * @return
     * @throws JSONException
     */
    public static Object parseJSON(String s) throws JSONException {
        if (s.trim().startsWith("{")) {
            return new JSONObject(s);
        }
        else if (s.trim().startsWith("[")) {
            return new JSONArray(s);
        }
        throw new JSONException("Unparsable JSON string: " + s);
    }
}
