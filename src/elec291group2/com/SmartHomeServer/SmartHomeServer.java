package elec291group2.com.SmartHomeServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Queue;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONException;

import elec291group2.com.SmartHomeServer.Constants;

public class SmartHomeServer
{
    private Set<String> deviceTokens;

	private ServerSocket serverSocket;

	// This is the status variable that will be true if a new status string
	// needs to be sent

	// This is the status string that the Arduino will send to the Android
	// Device
	String status = "";

	// This is the Queue of commands that the Android device is sending
	Queue<String> commandQueue = new LinkedList<String>();

	/**
	 * @param port
	 *            Port Number to create server on
	 */
	public SmartHomeServer(int port) throws IOException
	{
		serverSocket = new ServerSocket(port);
		deviceTokens = new HashSet<String>();
	}

	public void arduino()
	{

		System.out.println("Arduino communication thread started.");
		while (true)
		{
			// PLACE ANDROID SERIAL COMMUNICATION STUFF HERE

			if (!commandQueue.isEmpty()) // New command
			{
				// Retrieve and send the command through serial
				String commandOut = commandQueue.poll();
				System.out.println("Command sent: " + commandOut);

				// If command begins with 'register'
				if (commandOut
					.substring( 0, Math.min(commandOut.length(), Constants.REGISTER.length()) )
					.equals(Constants.REGISTER))
				{
					// Remaining part of string is the token
					String token = commandOut.substring(Constants.REGISTER.length(), commandOut.length());
					registerDeviceToken(token);
				}
			}

			// Process the serial in and update the status/ status flag

			Thread.yield();
		}
	}

	/**
	 * Run the server, listening for connections and handling them.
	 *
	 * @throws IOException
	 *             if the main server socket is broken
	 */
	public void serve() throws IOException
	{
		System.out.println("Connection handling server started.");
		while (true)
		{
			// block until a client connects
			final Socket socket = serverSocket.accept();
			// create a new thread to handle that client
			Thread handler = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						try
						{
							handle(socket);
						} finally
						{
							socket.close();
						}
					} catch (IOException ioe)
					{
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
	private void handle(Socket socket) throws IOException
	{

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
		try
		{
			String lastStatus = "";

			while (true)
			{


				if (in.ready())  // Retrieve command from Android device, add to device queue
				{
					String s = in.readLine();
					if(s.equals("exit"))
					{
						System.err.println("A client has ended the connection.");
						break;
					}

					//System.out.println("The new command is: " + s);
					commandQueue.add(s);
				}

				if (!lastStatus.equals(status)) // Send new status to Android device
				{
					System.out.println("The new status is : " + status);
					out.println(status);
					out.flush();
					lastStatus = status;

				}
				//out.println("hi");
				Thread.yield();
			}
		} catch (Exception e)
		{

		}
		System.err.println("Thread closed.");
	}

	/*
	 * 	Registers a mobile device token retrieved from the GCM server to the local
	 *  database.
	 */
    private void registerDeviceToken(String token)
    {
    	if (token != null)
    	{
    		deviceTokens.add(token);
    		System.out.println("Device token successfully registered!");
    		sendPushNotification("Push notification test");
    	}
    }

    /*
     *  Sends notification message to all tokens/devices registered to the server.
     *  Uses HTTP POST protocol to send a downstream message to GCM server.
     *  Maximum # of recipients per push: 1000
     */
    private void sendPushNotification(String message)
    {
        try
        {
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
        }
        catch (IOException | JSONException e)
        {
            System.out.println("Unable to send GCM message. ");
            e.printStackTrace();
        }
    }


	public static void main(String[] args) throws IOException
	{
		SmartHomeServer server = new SmartHomeServer(90);


		// Socket communication between Server and Android device
		Thread serverThread = new Thread(new Runnable()
		{
			public void run()
			{
				// Start the multithreaded server
				try
				{
					server.serve();
				} catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// Run the server

			}
		});


		// Serial communication between server and Arduino
		Thread arduinoThread = new Thread(new Runnable()
		{
			public void run()
			{
				server.arduino();
			}
		});

		// Start the threads
		arduinoThread.start();
		serverThread.start();

	}
}
