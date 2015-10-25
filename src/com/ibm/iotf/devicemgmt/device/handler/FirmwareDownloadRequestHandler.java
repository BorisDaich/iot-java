/**
 *****************************************************************************
 Copyright (c) 2015 IBM Corporation and other Contributors.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
 Contributors:
 Mike Tran - Initial Contribution
 Sathiskumar Palaniappan - Added Resource Model
 *****************************************************************************
 *
 */
package com.ibm.iotf.devicemgmt.device.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ibm.iotf.devicemgmt.device.DeviceFirmware;
import com.ibm.iotf.devicemgmt.device.ManagedDevice;
import com.ibm.iotf.devicemgmt.device.internal.ResponseCode;
import com.ibm.iotf.devicemgmt.device.internal.ServerTopic;
import com.ibm.iotf.util.LoggerUtility;

/**
 * Request handler for <code>MMqttClient.SERVER_TOPIC_INITIATE_FIRMWARE_DOWNLOAD</code>
 * <br>Expected request message format
 * <blockquote>
 * {
 * 	"reqId": "string"
 * }
 */	
public class FirmwareDownloadRequestHandler extends DMRequestHandler {

	private static final String CLASS_NAME = FirmwareDownloadRequestHandler.class.getName();
	
	public FirmwareDownloadRequestHandler(ManagedDevice dmClient) {
		setDMClient(dmClient);
	}
	
	/**
	 * Return initiate firmware download topic
	 */
	@Override
	protected ServerTopic getTopic() {
		return ServerTopic.INITIATE_FIRMWARE_DOWNLOAD;
	}
	
	/**
	 * subscribe to initiate firmware download topic
	 */
	@Override
	protected void subscribe() {
		subscribe(ServerTopic.INITIATE_FIRMWARE_DOWNLOAD);
	}

	/**
	 * Unsubscribe initiate firmware download topic
	 */
	@Override
	protected void unsubscribe() {
		unsubscribe(ServerTopic.INITIATE_FIRMWARE_DOWNLOAD);
	}

	/**
	 * Following are actions that needs to be taken after receiving the command
	 * 
	 * If mgmt.firmware.state is not 0 ("Idle") an error should be reported with 
	 * response code 400, and an optional message text.
	 * 
	 * If the action can be initiated immediately, set rc to 202.
	 * 
	 * If mgmt.firmware.url is not set or is not a valid URL, set rc to 400.
	 * 
	 * If firmware download attempt fails, set rc to 500 and optionally set message accordingly.
	 * 
	 * If firmware download is not supported, set rc to 501 and optionally set message accordingly.
	 */
	@Override
	public void handleRequest(JsonObject jsonRequest) {
		final String METHOD = "handleRequest";
		ResponseCode rc = ResponseCode.DM_INTERNAL_ERROR;
		
		JsonObject response = new JsonObject();
		response.add("reqId", jsonRequest.get("reqId"));
		DeviceFirmware deviceFirmware = getDMClient().getDeviceData().getDeviceFirmware();
		if (deviceFirmware == null) {
			rc = ResponseCode.DM_FUNCTION_NOT_IMPLEMENTED;
		} else if(deviceFirmware.getState() != DeviceFirmware.FirmwareState.IDLE.getState()) {
			rc = ResponseCode.DM_BAD_REQUEST;		
		} else {
			if (deviceFirmware.getUrl() != null) {
				LoggerUtility.fine(CLASS_NAME, METHOD, "fire event(" 
						+ DeviceFirmware.FIRMWARE_DOWNLOAD_START + ")" );
				getDMClient().getDeviceData().getDeviceFirmware().fireEvent(DeviceFirmware.FIRMWARE_DOWNLOAD_START);
				rc = ResponseCode.DM_ACCEPTED;
			} else {
				rc = ResponseCode.DM_BAD_REQUEST;
				LoggerUtility.severe(CLASS_NAME, METHOD, "No URL mentioned in the request");
			}
		} 
		response.add("rc", new JsonPrimitive(rc.getCode()));
		respond(response);
	}
}
