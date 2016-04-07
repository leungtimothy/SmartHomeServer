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
import java.util.Queue;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONException;
import com.pi4j.io.serial.*;
import elec291group2.com.SmartHomeServer.Constants;
import elec291group2.com.SmartHomeServer.Alarm;

public class SmartHomeServer {
	private static String AUTHENTICATION_KEY = "1234567";
	private static String hashed_key;

	private Set<String> deviceTokens;
	private ServerSocket serverSocket;

	// This is the status variable that will be true if a new status string
	// needs to be sent

	// This is the status string that the Arduino will send to the Android
	// Device
	String status = "0000000000";
	
	// These flags are used to avoid repeating certain methods
	boolean commandFlag = true;
	boolean alarmFlag = false;
	boolean triggeredFlag = false;
	boolean failedPWFlag = false;
	
	// This thread is used to sound the alarm
	Thread alarmThread = null;
	
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
				// wait for all data to arrive
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				String serialData = event.getData();
				// split the serial data and process each string
				for (String rx : serialData.split(":")) {
					// tell main loop of thread to send next command
					if (rx.contains("r")) {
						commandFlag = true;
						System.out.println("=== READY FOR COMMAND ===");
						continue;	
					} 
					// update the status with the one received on RX
					else if (!rx.matches("[0,1]{14}")) {	// this regex means that it is a new status string
						System.out.println("=== Invalid RX status: " + rx + " ===");
						continue;
					}
					System.out.println("=== NEW STATUS RECIEVED ===");
					System.out.println("RX: " + rx);
					StringBuilder rxSB = new StringBuilder();		// build the new status string for the Android applications

					// systemStatus
					if (rx.charAt(0) == '1') {														// system triggered
						rxSB.append('2');
						if (!triggeredFlag) {
							Thread pushThread1 = new Thread(new Runnable() {
								public void run() {
									sendPushNotification("Intruder Alert !!");
								}
							});
							pushThread1.start();
							triggeredFlag = true;
						}

					}
					else if (rx.charAt(0) == '0' && rx.charAt(1) == '1') {							// password entry failed
						rxSB.append('3');
						if(!failedPWFlag) {
							Thread pushThread = new Thread(new Runnable() {
								public void run() {
									sendPushNotification("Password Entry Failed !!");
								}
							});
							pushThread.start();
							failedPWFlag = true;
						}
					}
					else if (rx.charAt(5) == '1' && rx.charAt(6) == '1' && rx.charAt(7) == '1')		// system fully armed
						rxSB.append('1');
					else																			// system not fully armed
						rxSB.append('0');
					System.out.println("systemStatus: " + rxSB.charAt(0));

					// doorStatus
					if (rx.charAt(2) == '1' && rx.charAt(5) == '1')				// door alarm triggered
						rxSB.append('3');
					else if (rx.charAt(2) == '1' && rx.charAt(5) == '0')		// door armed
						rxSB.append('2');
					else if (rx.charAt(2) == '0' && rx.charAt(5) == '1')		// door disarmed and open
						rxSB.append('1');
					else														// door disarmed and closed
						rxSB.append('0');
					System.out.println("doorStatus: " + rxSB.charAt(1));

					// motionStatus
					if (rx.charAt(3) == '1' && rx.charAt(6) == '1')				// motion alarm triggered
						rxSB.append('3');
					else if (rx.charAt(3) == '1' && rx.charAt(6) == '0')		// motion armed
						rxSB.append('2');
					else if (rx.charAt(3) == '0' && rx.charAt(6) == '1')		// motion detected but disarmed
						rxSB.append('1');
					else
						rxSB.append('0');										// motion not detected and disarmed
					System.out.println("motionStatus: " + rxSB.charAt(2));

					// laser
					if (rx.charAt(4) == '1' && rx.charAt(7) == '1')				// laser alarm triggered
						rxSB.append('2');
					else if (rx.charAt(7) == '1')								// laser armed
						rxSB.append('1');
					else
						rxSB.append('0');										// laser diarmed
					System.out.println("laserStatus: " + rxSB.charAt(3));

					// manualAlarm
					if (rx.charAt(8) == '1') {									// sound alarm
						rxSB.append('1');
						if (!alarmFlag) {
							alarmThread = new Thread(a);
							alarmThread.start();								// start new alarm thread
							a.start();
							alarmFlag = true;
						}
					} else {													// turn off alarm
						rxSB.append('0');
						if (alarmFlag) {
							a.stop();											// kill alarm thread
							alarmFlag = false;
							failedPWFlag = false;
							triggeredFlag = false;
						}
					}
					System.out.println("manualAlarm: " + rxSB.charAt(4));

					// lights
					rxSB.append(rx.substring(9));
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
			serial.open("/dev/ttyACM0", 38400); // open up default USB port for communication
			while (true) {
				// send new command if we have one and Arduino is ready
				if (!commandQueue.isEmpty() && commandFlag) {
					try {
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

					if (s.equals(hashed_key)) {
						authenticated = true;
						System.err.println("The client has been verified.");
						out.println("Verified");
						out.flush();
						break;
					} else {
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

			Thread commandHandle = new Thread(new Runnable() {
				public void run() {
					while (true) {
						String s = null;
						try {
							s = in.readLine();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (s != null) // Retrieve command from Android device,
										// add to
										// device queue
						{
							if (s.equals("exit")) {
								System.err.println("A client has ended the connection.");
								break;

							} else if (s.substring(0, Math.min(s.length(), 8)).equals("register")) {
								// Remaining part of string is the token
								String token = s.substring(8, s.length());
								System.out.println(token);
								registerDeviceToken(token);
							} else {
								System.out.println("The new command is:" + s);
								commandQueue.add(s);
							}
						}
					}
					out.close();
				}
			});
			Thread t = new Thread(commandHandle);
			t.start();
			while (authenticated == true) {
				if (!lastStatus.equals(status)) // Send new status to Android
				// device
				{
					System.out.println("The new status is:" + status);
					out.println(status);
					// out.flush();
					lastStatus = status;

				}
				// System.out.println("Yielding");
				Thread.yield();
				// out.println("bob");
			}
		} catch (Exception e) {
			//e.printStackTrace();
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
			Thread pushThread3 = new Thread(new Runnable() {
				public void run() {
					sendPushNotification("Your device will now receive notifications about your home.");
				}
			});
			pushThread3.start();
		}
	}

	/*
	 * Sends notification message to all tokens/devices registered to the
	 * server. Uses HTTP POST protocol to send a downstream message to GCM
	 * server. Maximum # of recipients per push: 1000
	 */
	private void sendPushNotification(String message) {
		if (!deviceTokens.isEmpty()) {
			System.out.println("devices not empty");
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
				outputStream.flush();
				
				//System.out.println("\nHTTP POST request sent: \n" + jGcmData.toString(4));
				//InputStream inputStream = conn.getInputStream();
				//String resp = IOUtils.toString(inputStream);
				//System.out.println("GCM server response:" + resp);
			} catch (IOException | JSONException e) {
				System.out.println("Unable to send GCM message. ");
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws IOException {
		SmartHomeServer server = new SmartHomeServer(8888);

		hashed_key = encryptionFunction.password_hash(AUTHENTICATION_KEY);
		System.out.println("The key is :" + AUTHENTICATION_KEY + ".");
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
		arduinoThread.start();
		serverThread.start();

	}
}
