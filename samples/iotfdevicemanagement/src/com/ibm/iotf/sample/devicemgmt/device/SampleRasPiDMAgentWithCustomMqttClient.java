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
package com.ibm.iotf.sample.devicemgmt.device;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import com.google.gson.JsonObject;
import com.ibm.iotf.devicemgmt.device.DeviceData;
import com.ibm.iotf.devicemgmt.device.DeviceFirmware;
import com.ibm.iotf.devicemgmt.device.DeviceInfo;
import com.ibm.iotf.devicemgmt.device.DeviceLocation;
import com.ibm.iotf.devicemgmt.device.DeviceMetadata;
import com.ibm.iotf.devicemgmt.device.DiagnosticErrorCode;
import com.ibm.iotf.devicemgmt.device.DiagnosticLog;
import com.ibm.iotf.devicemgmt.device.ManagedDevice;
import com.ibm.iotf.devicemgmt.device.DeviceFirmware.FirmwareState;
import com.ibm.iotf.sample.devicemgmt.device.task.DiagnosticErrorCodeUpdateTask;
import com.ibm.iotf.sample.devicemgmt.device.task.DiagnosticLogUpdateTask;
import com.ibm.iotf.sample.devicemgmt.device.task.LocationUpdateTask;
import com.ibm.iotf.sample.devicemgmt.device.task.ManageTask;
import com.ibm.iotf.sample.devicemgmt.device.task.PublishDeviceEventTask;
/**
 * A sample device management agent code that shows the following core DM capabilities,
 * 
 * 1. Managed device
 * 2. Firmware update
 * 3. Device Reboot
 * 4. Location update 
 * 5. Diagnostic ErrorCode addition & clear
 * 6. Diagnostic Log addition & clear 
 * 7. unmanage
 * 
 * This sample is similar to SampleRasPiDMAgent, but it passes the MQTTClient as
 * an argument to the library code to initiate ManagedDevice object. This sample
 * demonstrates how to use the DM capabilities if one has the custom device
 * functionalities implemented already - but we strongly recommend the users to
 * use the library for both device and management activities.
 * 
 * Performs the following activities based on user input 
 *
 * manage [lifetime in seconds] :: Request to make the device as Managed device in IoTF
 * unmanage :: Request to make the device unmanaged
 * firmware :: Adds a Firmware Handler that listens for the firmware actions from IoTF)
 * reboot :: Adds a Device action Handler that listens for reboot from IoTF)
 * location :: Starts a task that updates a random location at every 30 seconds)
 * errorcode :: Starts a task that appends/clears a ErrorCode at every 30 seconds)
 * log :: Starts a task that appends/clears a Log message at every 30 seconds)
 * quit :: quit this program)
	 
 * This sample takes a properties file where the device informations and Firmware
 * informations are present. There is a default properties file in the sample folder, this
 * class takes the default properties file if one not specified by user.
 * 
 * Refer to this link https://docs.internetofthings.ibmcloud.com/reference/device_mgmt.html
 * for more information about IBM IoT Foundation's DM capabilities 
 */
public class SampleRasPiDMAgentWithCustomMqttClient {
	private final static String PROPERTIES_FILE_NAME = "DMDeviceSample.properties";
	private final static String DEFAULT_PATH = "samples/iotfmanagedclient/src";
	private DeviceData deviceData;
	private ManagedDevice dmClient;
	private MqttClient mqttClient = null;
	
	private ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(1);
	private ScheduledFuture manageTask;
	private ScheduledFuture locationTask;
	private ScheduledFuture errorcodeTask;
	private ScheduledFuture logTask;
	
	public static void main(String[] args) throws Exception {
		
		System.out.println("Starting sample DM agent...");
		String fileName = null;
		if (args.length == 1) {
			fileName = args[0];
		} else {
			fileName = getDefaultFilePath();
		}
		
		SampleRasPiDMAgentWithCustomMqttClient sample = new SampleRasPiDMAgentWithCustomMqttClient();
		try {
			sample.createManagedClient(fileName);
			sample.userAction();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.err.flush();
		} finally {
			sample.terminate();
		}
		System.out.println(" Exiting...");
		System.exit(-1);
	}
	
	/**
	 * Device Event publish Task  - publish an event every 1 minute,
	 * 
	 * this is to showcase that one can publish events while carrying 
	 * out DM activities
	 */
	private void scheduleDeviceEventPublishTask() {
		PublishDeviceEventTask task = new PublishDeviceEventTask(dmClient);
		scheduledThreadPool.scheduleAtFixedRate(task, 0, 60, TimeUnit.SECONDS);
	}

	/**
	 * Location Update Task  - updates random location at every 30th second
	 */
	private void scheduleLocationTask() {
		if(locationTask == null) {
			LocationUpdateTask locTask = new LocationUpdateTask(this.deviceData.getDeviceLocation());
			this.locationTask = scheduledThreadPool.scheduleAtFixedRate(locTask, 0, 30, TimeUnit.SECONDS);
			System.out.println("Location Update Task started successfully");
		} else {
			System.out.println("Location task is already scheduled !!");
		}
	}
	
	/**
	 * ErrorCode Update Task - Appends/clears an errorcode at every 30th second
	 */
	private void scheduleErrorCodeTask() {
		if(errorcodeTask == null) {
			DiagnosticErrorCodeUpdateTask ecTask = new DiagnosticErrorCodeUpdateTask(deviceData.getDiagnosticErrorCode());
			this.errorcodeTask = scheduledThreadPool.scheduleAtFixedRate(ecTask, 0, 30, TimeUnit.SECONDS);
			System.out.println("ErrorCode Update Task started successfully");
		} else {
			System.out.println("ErrorCode update task is already running !!");
		}
	}
	
	/**
	 * Log Update Task - Appends/clears a log information at every 30th second
	 */
	
	private void scheduleLogTask() {
		
		if(this.logTask == null) {
			DiagnosticLogUpdateTask logTask = new DiagnosticLogUpdateTask(deviceData.getDiagnosticLog());
			this.logTask = scheduledThreadPool.scheduleAtFixedRate(logTask, 0, 30, TimeUnit.SECONDS);
			System.out.println("Log Update Task started successfully");
		} else {
			System.out.println("Log update task is already running !!");
		}
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
		
		DeviceFirmware firmware = new DeviceFirmware.Builder().
				version(trimedValue(deviceProps.getProperty("DeviceFirmware.version"))).
				name(trimedValue(deviceProps.getProperty("DeviceFirmware.name"))).
				url(trimedValue(deviceProps.getProperty("DeviceFirmware.url"))).
				verifier(trimedValue(deviceProps.getProperty("DeviceFirmware.verifier"))).
				state(FirmwareState.IDLE).				
				build();
		
		/**
		 * Create a DeviceLocation object
		 */
		DeviceLocation location = new DeviceLocation.Builder(30.28565, -97.73921).
												elevation(10).build();
		
		/**
		 * Create a DeviceDiagnostic Object With default ErrorCode & Log
		 */
		
		DiagnosticErrorCode errorCode = new DiagnosticErrorCode(0);
		
		DiagnosticLog log = new DiagnosticLog(
				"Creating a Managed Client", 
				new Date(),
				DiagnosticLog.LogSeverity.informational);
		
		/**
		 * Create a DeviceMetadata object
		 */
		JsonObject data = new JsonObject();
		data.addProperty("customField", "customValue");
		DeviceMetadata metadata = new DeviceMetadata(data);
		
		this.deviceData = new DeviceData.Builder().
						 typeId(trimedValue(deviceProps.getProperty("Device-Type"))).
						 deviceId(trimedValue(deviceProps.getProperty("Device-ID"))).
						 deviceInfo(deviceInfo).
						 deviceFirmware(firmware).
						 deviceLocation(location).
						 deviceErrorCode(errorCode).
						 deviceLog(log).
						 metadata(metadata).
						 build();

		createMqttClient(deviceProps);
				
		dmClient = new ManagedDevice(this.mqttClient, deviceData);
	}
	
	private void createMqttClient(Properties deviceProps) throws MqttException, 
	NoSuchAlgorithmException, 
	KeyManagementException {
		
		StringBuilder serverURI = new StringBuilder();
		StringBuilder clientId = new StringBuilder();
		
		serverURI.append("ssl://")
				 .append(trimedValue(deviceProps.getProperty("Organization-ID")))
				 .append(".messaging.internetofthings.ibmcloud.com:8883");
		
		clientId.append("d:")
		         .append(trimedValue(deviceProps.getProperty("Organization-ID")))
		         .append(":")
		         .append(trimedValue(deviceProps.getProperty("Device-Type")))
		         .append(":")
		         .append(trimedValue(deviceProps.getProperty("Device-ID")));
		
		MqttConnectOptions conOpt = new MqttConnectOptions();
		conOpt.setCleanSession(false);
		conOpt.setUserName("use-token-auth");
		conOpt.setPassword(trimedValue(deviceProps.getProperty("Authentication-Token")).toCharArray());
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, null, null);
		conOpt.setSocketFactory(sslContext.getSocketFactory());
		
		
		mqttClient = new MqttClient(serverURI.toString(), clientId.toString());
		System.out.println("Trying to connect to URI -> "+serverURI.toString() +" ClientID: "+clientId.toString());
		System.out.println("Username : "+conOpt.getUserName());
		System.out.println("password : "+new String(conOpt.getPassword()));
		mqttClient.connect(conOpt);
	}
	
	private boolean sendManageRequest(int lifetime) throws MqttException {
		if(this.manageTask != null) {
			manageTask.cancel(false);
		}

		if(lifetime > 0) {
			ManageTask task = new ManageTask(this.dmClient, lifetime);
			int twoMinutes =  60 * 2;
			scheduledThreadPool.scheduleAtFixedRate(task, 
											(lifetime - twoMinutes), 
											(lifetime - twoMinutes),
											TimeUnit.SECONDS);
		}
		if (dmClient.manage(lifetime)) {
			return true;
		} else {
			System.err.println("Managed request failed!");
		}
		return false;
	}

	private void terminate() throws Exception {
		if(this.dmClient != null) {
			if(scheduledThreadPool != null)
				scheduledThreadPool.shutdown();
			this.sendUnManageRequest();
		}
		this.mqttClient.disconnect();
		System.out.println("Bye !!");
		System.exit(-1);
	}
	
	/**
	 * This method does two things.
	 * 
	 * 1. Informs the Device management server that this device supports Firmware actions
	 * 
	 * 2. Adds a Firmware handler where the device agent will get notified
	 *    when there is a firmware action from the server. 
	 */
	private void addFirmwareHandler() throws Exception {
		if(this.dmClient != null) {
			RasPiFirmwareHandlerSample fwHandler = new RasPiFirmwareHandlerSample();
			deviceData.addFirmwareHandler(fwHandler);
			dmClient.supportsFirmwareActions(true);
			
			// Need to send another manage request as we need to
			// inform IoTF that this device supports firmware actions now
			this.sendManageRequest(0);
			
			System.out.println("Added Firmware Handler successfully !!");
		}
	}
	
	/**
	 * This method does two things.
	 * 
	 * 1. Informs the Device management server that this device supports Firmware actions
	 * 
	 * 2. Adds a Firmware handler where the device agent will get notified
	 *    when there is a firmware action from the server. 
	 */
	private void addDeviceActionHandler() throws Exception {
		if(this.dmClient != null) {
			DeviceActionHandlerSample actionHandler = new DeviceActionHandlerSample();
			deviceData.addDeviceActionHandler(actionHandler);
			dmClient.supportsDeviceActions(true);
			
			// Need to send another manage request as we need to
			// inform IoTF that this device supports device action now
			this.sendManageRequest(0);
			System.out.println("Added Device Action Handler successfully !!");
		}
	}
	
	
	private void sendUnManageRequest() throws MqttException {
		dmClient.unmanage();
		
		System.out.println("Stopping Tasks !!");
		if(null != this.manageTask) {
			this.manageTask.cancel(false);
			manageTask = null;
		}
		
		if(this.locationTask != null) {
			this.locationTask.cancel(false);
			locationTask = null;
		}
		 
		if(this.errorcodeTask != null) {
			this.errorcodeTask.cancel(false);
			this.errorcodeTask = null;
		}
		
		if(logTask != null) {
			this.logTask.cancel(false);
			this.logTask = null;
		}
		System.out.println("Tasks Stopped!!");
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
					SampleRasPiDMAgentWithCustomMqttClient.class.getClass().getResourceAsStream(PROPERTIES_FILE_NAME);
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
	
	private static void printOptions() {
		System.out.println("List of device management operations that this agent can perform are:");
		System.out.println("manage [lifetime in seconds] :: Request to make the device as Managed device in IoTF");
		System.out.println("unmanage   :: Request to make the device unmanaged ");
		System.out.println("firmware   :: Adds a Firmware Handler that listens for the firmware requests from IoTF");
		System.out.println("reboot     :: Adds a Device action Handler that listens for reboot request from IoTF");
		System.out.println("location   :: Starts a task that updates a random location at every 30 seconds");
		System.out.println("errorcode  :: Starts a task that appends/clears a simulated ErrorCode at every 30 seconds");
		System.out.println("log        :: Starts a task that appends/clears a simulated Log message at every 30 seconds");
		System.out.println("quit       :: quit this sample agent");
	}
	
	private void userAction() {
    	Scanner in = new Scanner(System.in);
    	
    	printOptions();
		
    	while(true) {
    		try {
	    		System.out.println("Enter the command ");	
	            String input = in.nextLine();
	            
	            String[] parameters = input.split(" ");
	            
	            switch(parameters[0]) {
	            
	            	case "manage":
	            		boolean status = false;
	            		if(parameters.length == 2) {
	            			int lifetime = 0;
	            			try {
	            				lifetime = Integer.parseInt(parameters[1]);
	            				// The minimum lifetime should be 1 hour
	            				if(lifetime != 0 && lifetime < 3600) {
	            					System.err.println("Lifetime "+lifetime + " is less than minimum "
	            							+ "value (1 hour), so setting it to 1 hour");
	            					lifetime = 3600;
	            				}
	
	            			} catch(Exception e) {
	            				System.err.println("lifetime should be an integer");
	            				continue;
	            			}
	            			status = this.sendManageRequest(lifetime);
	            		} else {
	            			status = this.sendManageRequest(0);
	            		}
	            		if(status) {
	            			System.out.println("Device is connected as Managed device now !!");
	            		} 
	            		break;
	            		
	            	case "unmanage":
	            		this.sendUnManageRequest();
	            		break;
	            		
	            	case "firmware":
	            		this.addFirmwareHandler();;
	            		break;
	
	            	case "reboot":
	            		this.addDeviceActionHandler();
	            		break;
	
	            	case "location":
	            		this.scheduleLocationTask();
	            		break;
	
	            	case "errorcode":
	            		this.scheduleErrorCodeTask();
	            		break;
	            		
	            	case "log":
	            		this.scheduleLogTask();
	            		break;
	            		
	            	case "quit":
	            		this.terminate();
	            		System.out.println("Bye !!");
	            		break;
	
	            	default:
	            		System.out.println("Unknown command received :: "+input);
	            		printOptions();
	            		
	            }
    		} catch(Exception e) {
    			System.out.println("Operation failed with exception "+e.getMessage());
    			printOptions();
    			continue;
    		}
    	}
    	
    }

	
	
}
