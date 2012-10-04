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

/**
 * Bean for holding results from JSONCompare.
 */
public class JSONCompareResult {
    private boolean _success;
    private String _message;

    /**
     * Default constructor.
     */
    public JSONCompareResult() {
        this(true, null);
    }

    private JSONCompareResult(boolean success, String message) {
        _success = success;
        _message = message == null ? "" : message;
    }

    /**
     * Did the comparison pass?
     * @return True if it passed
     */
    public boolean passed() {
        return _success;
    }

    /**
     * Did the comparison fail?
     * @return True if it failed
     */
    public boolean failed() {
        return !_success;
    }

    /**
     * Result message
     * @return String explaining why if the comparison failed
     */
    public String getMessage() {
        return _message;
    }
    
    protected void fail(String message) {
        _success = false;
        if (_message.length() == 0) {
            _message = message;
        }
        else {
            _message += " ; " + message;
        }
    }

    protected void fail(String field, Object expected, Object actual) {
        StringBuffer message= new StringBuffer();
        message.append(field);
        message.append("\nExpected: ");
        message.append(expected + "");
        message.append("\n     got: ");
        message.append(actual + "");
        message.append("\n");
        fail(message.toString());
    }
}
