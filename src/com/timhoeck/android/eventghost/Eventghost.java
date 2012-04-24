package com.timhoeck.android.eventghost;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class Eventghost {
	private static final String TAG = "EventGhost";
	
	//Default settings
	public static final int DEFAULT_TIMEOUT = 5000;	//connection timeout in milliseconds
	public static final int DEFAULT_PORT = 1024;
	public static final String DEFAULT_PASSWORD = "eventghost";

	//Connection settings to server
	private String serverHost;
	private int serverPort;
	private String serverPassword;
	
	private static EventghostListener listener;
	
	public Eventghost(EventghostListener listener) {
		setListener(listener);
	}
	
	public interface EventghostListener {
		//occurs when an event has been received from an eventghost server
		void onServerStatusChanged(String status);
		void onReceivedEvent(String event, String payload);
	}
	
	//set the listener
	public void setListener(EventghostListener listener) {
		this.listener = listener;
	}
	
	//connect to an eventghost server
	public void useServer(String host, int port, String pw) {
		serverHost = host;
		serverPort = port;
		serverPassword = pw;
	}
	
	public void useServer(String host, String pw) {
		useServer(host, DEFAULT_PORT, pw);
	}
	
	//start listening for incoming connections
	
	public void startListening(Context context) {
		startListening(context, DEFAULT_PORT);
	}
	
	public void startListening(Context context, int port) {
		Intent startIntent = new Intent();
		startIntent.putExtra("port", port);
		startIntent.setClass(context, EventghostService.class);
		context.startService(startIntent);	
	}
	
	//stop listening for incoming connections
	public void stopListening(Context context) {
		Intent startIntent = new Intent();
		startIntent.setClass(context, EventghostService.class);
		context.stopService(startIntent);
	}
	
	//send event to the eventghost server
	public void sendEvent(String event) {
		sendEvent(event, null);
	}
	
	//send event to the eventghost server with a payload
	public void sendEvent(String event, String payload) {
		new sendTask().execute(event, payload);
	}
	
	private class sendTask extends AsyncTask<String, String, Void> {

		@Override
		protected Void doInBackground(String... params) {
			String event = params[0];
			String payload = params[1];
			Log.d(TAG, "Sending " + event + " with payload " + payload);
			try {
				Log.d(TAG, "creating connection to " + serverHost + ":" + serverPort);
				
				SocketAddress sockaddr = new InetSocketAddress(serverHost, serverPort);
				Socket connection = new Socket();
				connection.connect(sockaddr, DEFAULT_TIMEOUT);
				Log.d(TAG, "Connection created");
				
				//BufferedReader is = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				DataInputStream is = new DataInputStream(connection.getInputStream());
				DataOutputStream os = new DataOutputStream(connection.getOutputStream());
				os.writeBytes("quintessence\n\r");
				String responseLine = new String(is.readLine());
				String cookie = responseLine + ":" + serverPassword;
				String md5 = MD5Hex.MD5Hex(cookie);
				os.writeBytes(md5 + "\n");
				String accept = new String(is.readLine());
				if (accept.equals("accept")) {
					// Send all payloads (split with |)
					if (payload != null && payload.length() > 0) {
						String[] list = payload.split("\\|");
						for (int i = 0; i < list.length; i++) {
							String p = list[i];
							os.writeBytes("payload " + p + "\n");
						}
					}
					os.writeBytes(event + "\n");
					publishProgress("Sent Event: " + event + " with payload: " + payload);
				}
				publishProgress("Data exchanged");

				// clean up
				os.writeBytes("close\n");
				os.close();
				is.close();
				connection.close();
				publishProgress("Connection closed");
			} catch (SocketTimeoutException to) {
				Log.e(TAG, "Error sending event: Connection timed out");
			} catch (Exception e) {
				Log.e(TAG, "Error sending event: Incorrect password or you didn't specify useServer(host, pw)");
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
			Log.d(TAG, values[0]);
		}
		
	}
	
	private static class MD5Hex {
	    public static String MD5Hex(String s) {
	        String result = null;
	        try {
	            MessageDigest md5 = MessageDigest.getInstance("MD5");
	            byte[] digest = md5.digest(s.getBytes());
	            result = toHex(digest);
	        }
	        catch (NoSuchAlgorithmException e) {
	            // this won't happen, we know Java has MD5!
	        }
	        return result;
	    }

	    public static String toHex(byte[] a) {
	        StringBuilder sb = new StringBuilder(a.length * 2);
	        for (int i = 0; i < a.length; i++) {
	            sb.append(Character.forDigit((a[i] & 0xf0) >> 4, 16));
	            sb.append(Character.forDigit(a[i] & 0x0f, 16));
	        }
	        return sb.toString();
	    }
	}
	
	public static class EventghostService extends Service {

		private MultiServerThread serverThread;
		private ServerSocket serverSocket;

		@Override
		public IBinder onBind(Intent intent) {
			return new LocalBinder();
		}
		
		public class LocalBinder extends Binder {
			EventghostService getService() {
				return EventghostService.this;
			}
		}
		 
		// This is the old onStart method that will be called on the pre-2.0
		// platform.  On 2.0 or later we override onStartCommand() so this
		// method will not be called.
		@Override
		public void onStart(Intent intent, int startId) {
			startServer(intent);
		}

		// Used on 2.0 or later to start service
		@Override
		public int onStartCommand(Intent intent, int flags, int startId) {
			startServer(intent);
			return Service.START_STICKY;
		}
		
		@Override
		public void onDestroy() {
			super.onDestroy();
			try {
				if (serverThread != null) serverThread.close();		
				if (serverSocket != null) serverSocket.close();
			} catch (Exception e) {}
			Log.i(TAG, "Server stopped");
			if (listener != null) listener.onServerStatusChanged("stopped");
		}

		//start listening
		private void startServer(Intent intent) {
			InetAddress serverInetAddress = getServerAddress();
			try { 
				if (serverInetAddress != null) {
					//Find all addresses (IPv4/IPv6) associated with this interface
					NetworkInterface n = NetworkInterface.getByInetAddress(serverInetAddress);
					Enumeration<InetAddress> ips = n.getInetAddresses();
					String ipString = "";
					while (ips.hasMoreElements()) {
						InetAddress i = ips.nextElement();
						ipString += i.getHostAddress();
						if (ips.hasMoreElements()) ipString += " / ";
					}
					
					//TODO add server Port and Pass preferences
					int port = intent.getIntExtra("port", DEFAULT_PORT);
					if (port < DEFAULT_PORT || port > 65535) port = DEFAULT_PORT;
					String password = DEFAULT_PASSWORD;
					
					//If we have an available address, create a server socket
					serverSocket = new ServerSocket(port);
					serverThread = new MultiServerThread();
					serverThread.execute(password);
					Log.i(TAG, "Server started: Listening on " + port + " at address: " + ipString);
					if (listener != null) listener.onServerStatusChanged("listening: " + ipString + ":" + port);
				} else {
					Log.e(TAG, "Could not start server, unable to initialize network");
				}
			} catch (BindException e) {
				Log.w(TAG, "Could not start server, may already be running");
			} catch (Exception e) {
				Log.e(TAG, "Unable to start server");
				e.printStackTrace();
			}
		}


		/** 
		 * Searches network interfaces for available inet address
		 * @return looks for the first interface that is not the loopback
		 */
		private InetAddress getServerAddress() {
			try {
				Enumeration<NetworkInterface> ni = NetworkInterface
				.getNetworkInterfaces();
				// Find the appropriate network interface to bind to
				while (ni.hasMoreElements()) {
					NetworkInterface n = ni.nextElement();
					// we aren't interested in binding to localhost
					if (!n.getDisplayName().equals("lo")) {
						Enumeration<InetAddress> in = n.getInetAddresses();
						InetAddress i = null;
						while (in.hasMoreElements()) {
							i = in.nextElement();
						}
						return i;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
		//handles multiple incoming connections
		private class MultiServerThread extends AsyncTask<String, String, Void> {
			Socket acceptSocket;
			boolean listening = false;
			
			private void close() {
				listening = false;
				if (acceptSocket != null) {
					try {
						acceptSocket.close();
						acceptSocket = null;
					} catch (Exception e) {}
				}
			}
			
			@Override
			protected Void doInBackground(String... params) {
				try {
					listening = true;
					while (listening) {
						String password = params[0];
						acceptSocket = serverSocket.accept();
						publishProgress(acceptSocket.getInetAddress().getHostAddress());
						new ClientConnection().execute(acceptSocket, password);
					}
				} catch (SocketException se) {
					//socket was probably closed
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}

			@Override
			protected void onProgressUpdate(String... values) {
				super.onProgressUpdate(values);
				Log.w(TAG, "New client connection: " + values[0]);
			}
			
		}
		
		//handles a single incoming connection
		private class ClientConnection extends AsyncTask<Object, Void, Void> {
			private Socket client;
			private String password;
			
			@Override
			protected Void doInBackground(Object... params) {
				client = (Socket) params[0];
				password = (String) params[1];
				try {
					//client.setKeepAlive(true);
					client.setSoTimeout(DEFAULT_TIMEOUT);
					BufferedReader inData = new BufferedReader(new InputStreamReader(client.getInputStream()));
					DataOutputStream outData = new DataOutputStream(client.getOutputStream());
					String currentLine = inData.readLine();
					Log.d(TAG, "Received: " + currentLine);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
			
		}
	}
}