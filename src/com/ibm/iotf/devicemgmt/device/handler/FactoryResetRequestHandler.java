/**
 *****************************************************************************
 Copyright (c) 2015 IBM Corporation and other Contributors.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
 Contributors:
 Mike Tran - Initial Contribution
 Sathiskumar Palaniappan - Initial Contribution
 *****************************************************************************
 *
 */
package com.ibm.iotf.devicemgmt.device.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ibm.iotf.devicemgmt.device.DeviceAction;
import com.ibm.iotf.devicemgmt.device.ManagedDevice;
import com.ibm.iotf.devicemgmt.device.internal.ResponseCode;
import com.ibm.iotf.devicemgmt.device.internal.ServerTopic;
import com.ibm.iotf.util.LoggerUtility;

/**
 * Request handler for <code>MMqttClient.SERVER_TOPIC_INITIATE_FACTORY_RESET</code>
 * <br>Expected request message format
 * <blockquote>
 * {
 * 	"reqId": "string"
 * }
 */	
public class FactoryResetRequestHandler extends DMRequestHandler {

	public FactoryResetRequestHandler(ManagedDevice dmClient) {
		setDMClient(dmClient);
		
	}
	
	/**
	 * return initiate factory reset topic
	 */
	@Override
	protected ServerTopic getTopic() {
		return ServerTopic.INITIATE_FACTORY_RESET;
	}
	
	/**
	 * subscribe to initiate factory reset topic
	 */
	@Override
	protected void subscribe() {
		subscribe(ServerTopic.INITIATE_FACTORY_RESET);
	}

	/**
	 * Unsubscribe initiate factory reset topic
	 */
	@Override
	protected void unsubscribe() {
		unsubscribe(ServerTopic.INITIATE_FACTORY_RESET);
	}

	/**
	 * Handle the initiate factory reset messages from IBM IoT Foundation 
	 */
	@Override
	protected void handleRequest(JsonObject jsonRequest) {
		final String METHOD = "handleRequest";
		ResponseCode rc = ResponseCode.DM_ACCEPTED;
		
		JsonObject response = new JsonObject();
		response.add("reqId", jsonRequest.get("reqId"));
		DeviceAction action = getDMClient().getDeviceData().getDeviceAction();
		if (action == null) {
			rc = ResponseCode.DM_FUNCTION_NOT_IMPLEMENTED;
		} else {
			LoggerUtility.fine(CLASS_NAME, METHOD, " fire event(" 
					+ DeviceAction.DEVICE_FACTORY_RESET_START + ")" );
				
			action.fireEvent(DeviceAction.DEVICE_FACTORY_RESET_START);
			rc = ResponseCode.DM_ACCEPTED;
		} 
		response.add("rc", new JsonPrimitive(rc.getCode()));
		respond(response);
		
	}

}
