/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package leeang6969.lab6;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;


/**
 * My Config class.
 */
public class MyConfig extends Config<ApplicationId>{

	// The JSON file should contain one field "name".
	public static final String SERVER_ID = "DHCP_server_ID";
	public static final String SERVER_PORT = "DHCP_server_PORT";
	public static final String SERVER_MAC = "DHCP_server_MAC";
	public static final String SERVER_IP = "DHCP_server_IP";

	// For ONOS to check whether an uploaded configuration is valid.
	@Override
	public boolean isValid(){
		return hasFields(SERVER_ID, SERVER_PORT, SERVER_MAC, SERVER_IP);
	}

	// To retreat the value.
	public String DHCP_server_ID(){
		return get(SERVER_ID, null);
	}

	public String DHCP_server_PORT(){
		return get(SERVER_PORT, null);
	}

	public String DHCP_server_MAC(){
		return get(SERVER_MAC, null);
	}

	public String DHCP_server_IP(){
		return get(SERVER_IP, null);
	}

	// To set or clear the value.
	//public BasicElementConfig myname(String name){
	//    return (BasicElementConfig) setOrClear(MY_NAME, name);
	//}
}