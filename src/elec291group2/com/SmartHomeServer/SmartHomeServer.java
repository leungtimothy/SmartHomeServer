package elec291group2.com.SmartHomeServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.Queue;
import java.util.Scanner;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONException;
import com.pi4j.io.serial.*;
import elec291group2.com.SmartHomeServer.Constants;
import elec291group2.com.SmartHomeServer.Alarm;


public class SmartHomeServer
{
	private static String AUTHENTICATION_KEY = "1234567";
	private static String hashed_key;
	
    private Set<String> deviceTokens;
	private ServerSocket serverSocket;

	// This is the status variable that will be true if a new status string
	// needs to be sent

	// This is the status string that the Arduino will send to the Android
	// Device
	String status = "0000000000";
	boolean commandFlag = true;
	// This is the Queue of commands that the Android device is sending
	Queue<String> commandQueue = new LinkedList<String>();

	/**
	 * @param port
	 *            Port Number to create server on
	 */
	public SmartHomeServer(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		deviceTokens = new HashSet<String>();
	}

	public void arduino() {
		System.out.println("...Arduino communication thread started...");
		// create an instance of the serial communications class
		final Serial serial = SerialFactory.createInstance();
		Alarm a = new Alarm();
		// create and register the serial data listener
		serial.addListener(new SerialDataListener() {
			@Override
			public void dataReceived(SerialDataEvent event) {
				// update the status with the one received on RX
				System.out.println("=== NEW STATUS RECIEVED ===");
				String serialData = event.getData();
				for (String rx : serialData.split(":")) {
					if (rx.contains("ready")) {
						commandFlag = true;
						continue;
					} else if (!rx.matches("[0,1]{13}")) {
						System.out.println("=== Invalid RX status: " + rx + " ===");
						continue;
					}
					System.out.println("RX: " + rx);
					StringBuilder rxSB = new StringBuilder();

					// systemStatus
					if (rx.charAt(0) == '1') {
						rxSB.append('2');
						// sendPushNotification("ALERT: INTRUDER DETECTED");
					} else if (rx.charAt(4) == '1' && rx.charAt(5) == '1' && rx.charAt(6) == '1')
						rxSB.append('1');
					else
						rxSB.append('0');
					System.out.println("systemStatus: " + rxSB.charAt(0));

					// doorStatus
					if (rx.charAt(1) == '1' && rx.charAt(4) == '1')
						rxSB.append('3');
					else if (rx.charAt(1) == '1' && rx.charAt(4) == '0')
						rxSB.append('2');
					else if (rx.charAt(1) == '0' && rx.charAt(4) == '1')
						rxSB.append('1');
					else
						rxSB.append('0');
					System.out.println("doorStatus: " + rxSB.charAt(1));

					// motionStatus
					if (rx.charAt(2) == '1' && rx.charAt(5) == '1')
						rxSB.append('3');
					else if (rx.charAt(2) == '1' && rx.charAt(5) == '0')
						rxSB.append('2');
					else if (rx.charAt(2) == '0' && rx.charAt(5) == '1')
						rxSB.append('1');
					else
						rxSB.append('0');
					System.out.println("motionStatus: " + rxSB.charAt(2));

					// laser
					if (rx.charAt(3) == '1' && rx.charAt(6) == '1')
						rxSB.append('2');
					else if (rx.charAt(6) == '1')
						rxSB.append('1');
					else
						rxSB.append('0');
					System.out.println("laserStatus: " + rxSB.charAt(3));

					// manualAlarm
					if (rx.charAt(7) == '1'){
						rxSB.append('1');
						Thread t = new Thread(a);
						t.start();
						a.start();
					}
					else{
						rxSB.append('0');
						a.stop();
					}
					System.out.println("manualAlarm: " + rxSB.charAt(4));

					// lights
					rxSB.append(rx.substring(8));
					System.out.println("Light 0: " + rxSB.charAt(5));
					System.out.println("Light 1: " + rxSB.charAt(6));
					System.out.println("Light 2: " + rxSB.charAt(7));
					System.out.println("Light 3: " + rxSB.charAt(8));
					System.out.println("Light 4: " + rxSB.charAt(9));

					// update status
					status = rxSB.toString();
					System.out.println("=== statusString: " + status + " ===");
				}
			}
		});

		try {
			serial.open("/dev/ttyACM0", 38400); // open up default USB port for
												// communication
			while (true) {
				if (!commandQueue.isEmpty() && commandFlag) { // New command
					try {
						// Retrieve and send the command through serial
						String commandOut = commandQueue.poll();
						System.out.println("Command sent: " + commandOut);
						serial.write(commandOut);
						commandFlag = false;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				Thread.yield();
			}
		} catch (SerialPortException ex) {
			System.out.println(" ==>> SERIAL SETUP FAILED : " + ex.getMessage());
			return;
		}
	}

	/**
	 * Run the server, listening for connections and handling them.
	 * 
	 * @throws IOException
	 *             if the main server socket is broken
	 */
	public void serve() throws IOException {
		System.out.println("Connection handling server started.");
		while (true) {
			// block until a client connects
			final Socket socket = serverSocket.accept();
			// create a new thread to handle that client
			Thread handler = new Thread(new Runnable() {
				public void run() {
					try {
						try {
							handle(socket);
						} finally {
							socket.close();
						}
					} catch (IOException ioe) {
						// this exception wouldn't terminate serve(),
						// since we're now on a different thread, but
						// we still need to handle it
						ioe.printStackTrace();
					}
				}
			});
			// start the thread
			handler.start();
		}
	}

	/**
	 * Handle one client connection. Returns when client disconnects.
	 * 
	 * @param socket
	 *            socket where client is connected
	 * @throws IOException
	 *             if connection encounters an error
	 */
	private void handle(Socket socket) throws IOException {

		System.err.println("\nA client has connected, new communication thread started.");

		// get the socket's input stream, and wrap converters around it
		// that convert it from a byte stream to a character stream,
		// and that buffer it so that we can read a line at a time
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		// similarly, wrap character=>bytestream converter around the
		// socket output stream, and wrap a PrintWriter around that so
		// that we have more convenient ways to write Java primitive
		// types to it.
		PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
		boolean authenticated = false;
		int authentication_timeout = 1000;

		try {

			long startTime = System.currentTimeMillis();
			while ((System.currentTimeMillis() - startTime) < authentication_timeout) // Set
																						// time
																						// out
			{
				if (in.ready()) // If command is retrieved
				{
					String s = in.readLine();
					System.out.println("The key recieved is:" + s);

					if(s.equals(hashed_key))
					{
						authenticated = true;
						System.err.println("The client has been verified.");
						out.println("Verified");
						out.flush();
						break;
					}
					else {
						authenticated = false;
						System.err.println("The client has sent an incorrect key.");
						out.println("Wrong key");
						out.flush();
						break;
					}
				} else {
					System.err.println("Waiting for authentication key.");
				}
				Thread.sleep(750);
			}

			String lastStatus = "";
			while (authenticated == true) {
				String s = in.readLine();
				if (s != null) // Retrieve command from Android device, add to
								// device queue
				{	
					if (s.equals("exit")) {
						System.err.println("A client has ended the connection.");
						break;
					}
					else if (s
							.substring( 0, 8 ).equals("register"))
					{
							// Remaining part of string is the token
							String token = s.substring(8, s.length());
							System.out.println(token);
							registerDeviceToken(token);
					}

					System.out.println("The new command is:" + s);
					commandQueue.add(s);
				}
						
				if (!lastStatus.equals(status)) // Send new status to Android
												// device
				{
					System.out.println("The new status is:" + status);
					out.println(status);
					out.flush();
					lastStatus = status;

				}
				Thread.sleep(250);
				//System.out.println("Yielding");
				Thread.yield();
				//out.println("bob");
			}
		} catch (Exception e) {

		}
	
		if (authenticated == false) {
			System.err.println("Authentication failed, invalid key or timeout reached.");
		}
		System.err.println("Thread closed.");
	}

	/*
	 * Registers a mobile device token retrieved from the GCM server to the
	 * local database.
	 */
	private void registerDeviceToken(String token) {
		if (token != null) {
			deviceTokens.add(token);
			System.out.println("Device token successfully registered!");
			sendPushNotification("Your device will recieve emergency push notifications about your house.");
		}
	}

	/*
	 * Sends notification message to all tokens/devices registered to the
	 * server. Uses HTTP POST protocol to send a downstream message to GCM
	 * server. Maximum # of recipients per push: 1000
	 */
	private void sendPushNotification(String message) {
		try {
			JSONObject jMessage = new JSONObject();
			JSONObject jGcmData = new JSONObject();
			String[] recipients = deviceTokens.toArray(new String[0]);

			// Set main message 'data' field
			jMessage.put("message", message);
			jGcmData.put("data", jMessage);
			// Set message recipients (which device tokens to push to)
			jGcmData.put("registration_ids", recipients);

			// Create connection to send GCM Message Request
			URL url = new URL("https://android.googleapis.com/gcm/send");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "key=" + Constants.API_KEY);
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);

			// Send GCM message content.
			OutputStream outputStream = conn.getOutputStream();
			outputStream.write(jGcmData.toString().getBytes());

			System.out.println("\nHTTP POST request sent: \n" + jGcmData.toString(4));

			InputStream inputStream = conn.getInputStream();
			String resp = IOUtils.toString(inputStream);
			System.out.println("GCM server response:" + resp);
		} catch (IOException | JSONException e) {
			System.out.println("Unable to send GCM message. ");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		SmartHomeServer server = new SmartHomeServer(6969);

		hashed_key = encryptionFunction.password_hash(AUTHENTICATION_KEY);
		System.out.println("The key is :" + AUTHENTICATION_KEY+".");
		System.out.println("The hashed key is :" + hashed_key);
		System.out.println("The server's IP is " + InetAddress.getLocalHost() + ".");

		// Socket communication between Server and Android device
		Thread serverThread = new Thread(new Runnable() {
			public void run() {
				// Start the multithreaded server
				try {
					server.serve();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// Run the server

			}
		});

		// Serial communication between server and Arduino
		Thread arduinoThread = new Thread(new Runnable() {
			public void run() {
				server.arduino();
			}
		});

		// Start the threads
		//arduinoThread.start();
		serverThread.start();

	}
}
