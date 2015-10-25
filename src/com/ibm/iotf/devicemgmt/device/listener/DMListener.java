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
package com.ibm.iotf.devicemgmt.device.listener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import com.ibm.iotf.devicemgmt.device.DeviceLocation;
import com.ibm.iotf.devicemgmt.device.DiagnosticErrorCode;
import com.ibm.iotf.devicemgmt.device.DiagnosticLog;
import com.ibm.iotf.devicemgmt.device.ManagedDevice;
import com.ibm.iotf.devicemgmt.device.internal.DeviceDiagnostic;
import com.ibm.iotf.devicemgmt.device.internal.DeviceTopic;
import com.ibm.iotf.devicemgmt.device.resource.Resource;
import com.ibm.iotf.util.LoggerUtility;

public class DMListener implements PropertyChangeListener {
	
	private static Map<ManagedDevice, DMListener> dmListeners = new HashMap<ManagedDevice, DMListener>();
	private static final String CLASS_NAME = DMListener.class.getName();
	
	private ManagedDevice dmClient;
	
	// List of Notifiers
	private LocationChangeNotifier locationNotifier;
	private DiagnosticErrorCodeChangeNotifier errorCodeNotifier;
	private DiagnosticLogChangeNotifier logNotifier;
	
	public DMListener(ManagedDevice client) {
		this.dmClient = client;
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		switch(event.getPropertyName()) {
			case DiagnosticLog.LOG_CHANGE_EVENT:
				this.logNotifier.handleEvent(event);
				break;
								
			case DiagnosticErrorCode.ERRORCODE_CHANGE_EVENT:
				this.errorCodeNotifier.handleEvent(event);
				break;
								
			case DiagnosticErrorCode.ERRORCODE_CLEAR_EVENT:
				this.errorCodeNotifier.clearEvent(event);
				break;
								
			case DiagnosticLog.LOG_CLEAR_EVENT:
				this.logNotifier.clearEvent(event);
				break;
								
			case DeviceLocation.RESOURCE_NAME:
				this.locationNotifier.handleEvent(event);
				break;
		}
	}
	
	
	public static void start(ManagedDevice dmClient) {
		final String METHOD = "start";
		LoggerUtility.fine(CLASS_NAME, METHOD, "MQTT Connected(" + dmClient.isConnected() + ")");
		
		DMListener dmListener = dmListeners.get(dmClient);
		if(dmListener == null) {
			dmListener = new DMListener(dmClient);
			dmListeners.put(dmClient, dmListener);
			
		}
		dmListener.createNotifiers();
	}
	
	private void createNotifiers() {
		final String METHOD = "createNotifiers";
		if (dmClient.getDeviceData().getDeviceLocation() != null) {
			if (locationNotifier == null) {
				locationNotifier = new LocationChangeNotifier(this.dmClient);
				locationNotifier.setNotifyTopic(DeviceTopic.UPDATE_LOCATION);
				dmClient.getDeviceData().getDeviceLocation().addPropertyChangeListener(
						Resource.ChangeListenerType.INTERNAL, this);
			}
		} else {
			LoggerUtility.info(CLASS_NAME, METHOD,  "The device does not support location notification.");
		}
		
		DiagnosticErrorCode errorcode = dmClient.getDeviceData().getDiagnosticErrorCode(); 
		if (errorcode != null && this.errorCodeNotifier == null) {
			this.errorCodeNotifier = new DiagnosticErrorCodeChangeNotifier(dmClient);
			errorCodeNotifier.setNotifyTopic(DeviceTopic.CREATE_DIAG_ERRCODES);
			errorcode.addPropertyChangeListener(Resource.ChangeListenerType.INTERNAL, this);
		}
		
		DiagnosticLog log = dmClient.getDeviceData().getDiagnosticLog();
		if(log != null && this.logNotifier == null) {
				logNotifier = new DiagnosticLogChangeNotifier(dmClient);
				logNotifier.setNotifyTopic(DeviceTopic.ADD_DIAG_LOG);
				log.addPropertyChangeListener(Resource.ChangeListenerType.INTERNAL, this);
		}
	 
		if(errorcode == null && log == null) {
			LoggerUtility.info(CLASS_NAME, METHOD,  "The device does not support Diagnostic notification.");
		}

	}
	
	public static void stop(ManagedDevice dmClient) {
	
		DMListener dmListener = dmListeners.remove(dmClient);
		
		if(null != dmListener && dmListener.locationNotifier != null) {
			dmClient.getDeviceData().getDeviceLocation().removePropertyChangeListener(dmListener);
		}
			
		if(null != dmListener && dmListener.errorCodeNotifier != null) {
			dmClient.getDeviceData().getDiagnosticErrorCode().removePropertyChangeListener(dmListener);
		}
			
		if(null != dmListener && dmListener.logNotifier != null) {
			dmClient.getDeviceData().getDiagnosticLog().removePropertyChangeListener(dmListener);
		}
	}
	
}
