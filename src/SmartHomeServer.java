import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

import com.pi4j.io.serial.*;

public class SmartHomeServer
{

	public static final int DEFAULT_PORT = 1234;

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
	}

	public void arduino()
	{
		System.out.println("...Arduino communication thread started...");
		// create an instance of the serial communications class
		final Serial serial = SerialFactory.createInstance();

		// create and register the serial data listener
		serial.addListener(new SerialDataListener() {
			@Override
			public void dataReceived(SerialDataEvent event) {
				// update the status with the one received on RX
				System.out.println("...data recieved");
				String rx = event.getData();
				String proRX = "";
				
				// systemStatus
				if(rx.charAt(0) == '1')
					proRX.concat("2");
				else if(rx.charAt(4) == '0' || rx.charAt(5) == '0' || rx.charAt(6) == '0')
					proRX.concat("1");
				else
					proRX.concat("0");
				
				// doorStatus
				if(rx.charAt(1) == '1' && rx.charAt(4) == '1')
					proRX.concat("3");
				else if(rx.charAt(1) == '1' && rx.charAt(4) == '0')
					proRX.concat("2");
				else if(rx.charAt(1) == '0' && rx.charAt(4) == '1')
					proRX.concat("1");
				else
					proRX.concat("0");
				
				// motionStatus
				if(rx.charAt(2) == '1' && rx.charAt(5) == '1')
					proRX.concat("3");
				else if(rx.charAt(2) == '1' && rx.charAt(5) == '0')
					proRX.concat("2");
				else if(rx.charAt(2) == '0' && rx.charAt(5) == '1')
					proRX.concat("1");
				else
					proRX.concat("0");
				
				// laser
				if(rx.charAt(3) == '1' && rx.charAt(6) == '1')
					proRX.concat("2");
				else if(rx.charAt(6) == '1')
					proRX.concat("1");
				else
					proRX.concat("0");
				
				// manualAlarm
				if(rx.charAt(7) == '1')
					proRX.concat("1");
				else
					proRX.concat("0");
				
				// lights
				proRX.concat(rx.substring(8));
				
				// update status
				status = proRX;	
			}
		});

		
		try {
			serial.open("/dev/ttyACM0", 38400); // open up default USB port for	communication
			while (true) {
				if (!commandQueue.isEmpty()) { // New command
					try {
						// Retrieve and send the command through serial
						String commandOut = commandQueue.poll();
						System.out.println("Command sent: " + commandOut);

						// process the command then send it to the Arduino
						// PROCESS / DECIPHER COMMAND HERE
						serial.write("commandOut");
					} catch (Exception e) {e.printStackTrace();}
				}
				Thread.yield();
			}
		} catch (SerialPortException ex) {
			System.out.println(" ==>> SERIAL SETUP FAILED : " + ex.getMessage()); return;}
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
					
					System.out.println("The new command is: " + s);
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
