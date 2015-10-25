/**
 *****************************************************************************
 Copyright (c) 2015 IBM Corporation and other Contributors.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
 Contributors:
 Sathiskumar Palaniappan - Initial Contribution
 *****************************************************************************
 *
 */
package com.ibm.iotf.sample.devicemgmt.device;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;

import org.eclipse.paho.client.mqttv3.MqttException;

import com.google.gson.JsonObject;
import com.ibm.iotf.devicemgmt.device.DeviceData;
import com.ibm.iotf.devicemgmt.device.DeviceInfo;
import com.ibm.iotf.devicemgmt.device.DeviceMetadata;
import com.ibm.iotf.devicemgmt.device.DiagnosticErrorCode;
import com.ibm.iotf.devicemgmt.device.ManagedDevice;

/**
 * A sample device code that updates the Error code for every 5 seconds to IoT Foundation
 * and clears the error codes randomly.
 * 
 * This sample doesn't wait for the response from IoT Foundation server
 * 
 * This sample takes a properties file where the device informations and location
 * informations are present. There is a default properties file in the sample folder, this
 * class takes the default properties file if one not specified by user.
 */
public class NonBlockingDiagnosticsErrorCodeUpdateSample {
	private final static String PROPERTIES_FILE_NAME = "DMDeviceSample.properties";
	private final static String DEFAULT_PATH = "samples/iotfmanagedclient/src";
	
	private DeviceData deviceData;
	private ManagedDevice dmClient;
	private ErrorCodeUpdaterThread ecUpdaterThread;
	
	public static void main(String[] args) throws Exception {
		
		System.out.println("Starting DM Java Client sample Location Update test...");

		String fileName = null;
		if (args.length == 1) {
			fileName = args[0];
		} else {
			fileName = getDefaultFilePath();
		}

		NonBlockingDiagnosticsErrorCodeUpdateSample sample = new NonBlockingDiagnosticsErrorCodeUpdateSample();
		try {
			sample.createManagedClient(fileName);
			sample.connect();
			sample.sendManageRequest();
			sample.startErrorCodeUpdaterThread();
			sample.publishDeviceEvents();
			sample.disConnect();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		
		System.out.println(" Exiting...");
	}
	
	private void startErrorCodeUpdaterThread() {
		this.ecUpdaterThread.start();
	}

	private String trimedValue(String value) {
		if(value == null || value == "") {
			return "";
		} else {
			return value.trim();
		}
	}
	
	/**
	 * This method builds the device objects required to create the
	 * ManagedClient
	 * 
	 * @param propertiesFile
	 * @throws Exception
	 */
	private void createManagedClient(String propertiesFile) throws Exception {
		/**
		 * Load device properties
		 */
		Properties deviceProps = loadPropertiesFile(propertiesFile);

		/**
		 * To create a DeviceData object, we will need the following objects:
		 *   - DeviceInfo
		 *   - DeviceMetadata
		 *   - DeviceLocation (optional)
		 *   - DiagnosticErrorCode (optional)
		 *   - DiagnosticLog (optional)
		 *   - DeviceFirmware (optional)
		 */
		
		DeviceInfo deviceInfo = new DeviceInfo.Builder().
				serialNumber(trimedValue(deviceProps.getProperty("DeviceInfo.serialNumber"))).
				manufacturer(trimedValue(deviceProps.getProperty("DeviceInfo.manufacturer"))).
				model(trimedValue(deviceProps.getProperty("DeviceInfo.model"))).
				deviceClass(trimedValue(deviceProps.getProperty("DeviceInfo.deviceClass"))).
				description(trimedValue(deviceProps.getProperty("DeviceInfo.description"))).
				fwVersion(trimedValue(deviceProps.getProperty("DeviceInfo.swVersion"))).
				hwVersion(trimedValue(deviceProps.getProperty("DeviceInfo.hwVersion"))).
				descriptiveLocation(trimedValue(deviceProps.getProperty("DeviceInfo.descriptiveLocation"))).
				build();
		
		/**
		 * Build the ErrorCode & DeviceDiagnostic Object
		 */
		
		DiagnosticErrorCode errorCode = new DiagnosticErrorCode(0);
		errorCode.waitForResponse(false);
		
		/**
		 * Create a DeviceMetadata object
		 */
		JsonObject data = new JsonObject();
		data.addProperty("customField", "customValue");
		DeviceMetadata metadata = new DeviceMetadata(data);
		
		
		this.deviceData = new DeviceData.Builder().
						 deviceInfo(deviceInfo).
						 deviceErrorCode(errorCode).						 
						 metadata(metadata).
						 build();
		
		// create the location updater thread 
		this.ecUpdaterThread = new ErrorCodeUpdaterThread();
		this.ecUpdaterThread.diag = errorCode;
		
		// Options to connect to IoT Foundation
		Properties options = new Properties();
		options.setProperty("Organization-ID", trimedValue(deviceProps.getProperty("Organization-ID")));
		options.setProperty("Device-Type", trimedValue(deviceProps.getProperty("Device-Type")));
		options.setProperty("Device-ID", trimedValue(deviceProps.getProperty("Device-ID")));
		options.setProperty("Authentication-Method", trimedValue(deviceProps.getProperty("Authentication-Method")));
		options.setProperty("Authentication-Token", trimedValue(deviceProps.getProperty("Authentication-Token")));
		

		dmClient = new ManagedDevice(options, deviceData);
	}
	
	/**
	 * This method connects the device to the IoT Foundation as normal device
	 */
	private void connect() throws Exception {		
		dmClient.connect();
	}
	
	private void sendManageRequest() throws MqttException {
		if (dmClient.manage(0)) {
			System.out.println("Device connected as Managed device now!");
		} else {
			System.err.println("Managed request failed!");
		}
	}
	
	private void disConnect() throws Exception {
		dmClient.disconnect();
	}
	
	
	/**
	 * Thread class that updates the Error code to IoT Foundation 
	 * for every 5 seconds
	 */
	private static class ErrorCodeUpdaterThread extends Thread {
		private DiagnosticErrorCode diag;
		Random random = new Random();
		
		public void run() {
			
			for (int i=0; i<1000; i++) {
				// Don't check for response code as we don't wait 
				// for response from the IoT Foundation server
				diag.append(random.nextInt(500));
				System.out.println("Current Errorcode (" + diag + ")");
				
				if(i % 25 == 0) {
					diag.clear();
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * This method publishes a sample device event for every 5 seconds
	 * for 1000 times.
	 * 
	 *  This sample shows that one can publish events while carrying out
	 *  the device management operations.
	 */
	private void publishDeviceEvents() {
		
		Random random = new Random();
		//Lets publish an event for every 5 seconds for 1000 times
		for(int i = 0; i < 1000; i++) {
			//Generate a JSON object of the event to be published
			JsonObject event = new JsonObject();
			event.addProperty("name", "foo");
			event.addProperty("cpu",  random.nextInt(100));
			event.addProperty("mem",  random.nextInt(100));
		
			System.out.println("Publishing device event:: "+event);
			//Registered flow allows 0, 1 and 2 QoS	
			dmClient.publishEvent("status", event);
			
			try {
				Thread.sleep(5000 * 2);
			} catch(InterruptedException ie) {
				
			}
		}
	}
	
	private static Properties loadPropertiesFile(String propertiesFilePath) {
		File propertiesFile = new File(propertiesFilePath);
		Properties clientProperties = new Properties();
		FileInputStream in;
		try {
			in = new FileInputStream(propertiesFile);
			clientProperties.load(in);
			in.close();
		} catch (FileNotFoundException e) {
		
			InputStream stream =
					SampleRasPiDMAgent.class.getClass().getResourceAsStream(PROPERTIES_FILE_NAME);
			try {
				clientProperties.load(stream);
			} catch (IOException e1) {
				System.err.println("Could not find file "+ PROPERTIES_FILE_NAME+
						" Please run the application with file specified as an argument");
				System.exit(-1);
			}
			    
			return clientProperties;
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Could not find file "+ PROPERTIES_FILE_NAME+
					" Please run the application with file specified as an argument");
			System.exit(-1);
		}
		return clientProperties;
	}
	
	private static String getDefaultFilePath() {
		System.out.println("Trying to look for the default properties file :: " + PROPERTIES_FILE_NAME);
		
		// look for the file in current directory
		File f = new File(PROPERTIES_FILE_NAME);
		if(f.isFile()) {
			System.out.println("Found one in - "+ f.getAbsolutePath());
			return f.getAbsolutePath();
		}
		
		// look for the file in default path
		f = new File(DEFAULT_PATH + File.separatorChar + PROPERTIES_FILE_NAME);
		if(f.isFile()) {
			System.out.println("Found one in - "+ f.getAbsolutePath());
			return f.getAbsolutePath();
		}
		// Check whether its present in the bin folder
		f = new File("bin" + File.separatorChar + PROPERTIES_FILE_NAME);
		if(f.isFile()) {
			System.out.println("Found one in - "+ f.getAbsolutePath());
			return f.getAbsolutePath();
		} 
		System.out.println("Not found - try to load it using the classpath");
		return PROPERTIES_FILE_NAME;

	}
}
