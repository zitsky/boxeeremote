package com.andrewchatham;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

//import android.util.Log;

/*
 * This class tries to send a broadcast UDP packet over your wifi network to discover the boxee service. 
 */

public class Discoverer extends Thread {
	private static final String TAG = "Discovery";
	private static final String REMOTE_KEY = "b0xeeRem0tE!";
	private static final int DISCOVERY_PORT = 2562;
	private static final int TIMEOUT_MS = 5000;

	// TODO: Vary the challenge, or it's not much of a challenge :)
	private static final String mChallenge = "myvoice";
	private WifiManager mWifi;

	interface DiscoveryReceiver {
		void addAnnouncedServers(InetAddress[] host, int port[]);
	}

	Discoverer(WifiManager wifi) {
		mWifi = wifi;
	}

	public void run() {
		try {
			sendDiscoveryRequest();
			listenForResponses();
		} catch (IOException e) {
			Log.e(TAG, "Could not send discovery request", e);
		}
	}

	/**
	 * Send a broadcast UDP packet containing a request for boxee services to
	 * announce themselves.
	 * 
	 * @throws IOException
	 */
	private void sendDiscoveryRequest() throws IOException {
		String data = String
				.format(
						"<bdp1 cmd=\"discover\" application=\"iphone_remote\" challenge=\"%s\" signature=\"%s\"/>",
						mChallenge, getSignature());
		Log.d(TAG, data);

		DhcpInfo dhcp = mWifi.getDhcpInfo();
		if (dhcp == null) {
			Log.d(TAG, "Could not get dhcp info");
			return;
		} else {
			Log.d(TAG, "Dhcp info: " + dhcp);
		}

		// Calculate the broadcast IP we need to send the packet along. If we
		// send it to 255.255.255.255, it never gets sent. I guess this has
		// something to do with the mobile network not wanting to do broadcast.

		int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] address = new byte[4];
		for (int k = 0; k < 4; k++)
			address[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		InetAddress group = InetAddress.getByAddress(address);
		Log.d(TAG, "Send broadcast to " + group);

		DatagramPacket packet = new DatagramPacket(data.getBytes(), data
				.length(), group, DISCOVERY_PORT);
		DatagramSocket socket = new DatagramSocket();
		socket.setBroadcast(true);
		socket.send(packet);
	}

	private void listenForResponses() throws IOException {
		DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT);
		socket.setSoTimeout(TIMEOUT_MS);
		byte[] buf = new byte[2048];
		try {
			while (true) {
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				if (packet.getLength() > 0) {
					Log.d(TAG, "Received response " + buf);
				} else {
					Log.d(TAG, "Received empty response");
				}
			}
		} catch (SocketTimeoutException e) {
			Log.d(TAG, "Receive timed out");
		}
	}

	/**
	 * Calculate the signature we need to send with the request. It is a string
	 * containing the hex md5sum of the challenge and REMOTE_KEY.
	 * 
	 * @return signature string
	 */
	private String getSignature() {
		MessageDigest digest;
		byte[] md5sum = null;
		try {
			digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(mChallenge.getBytes());
			digest.update(REMOTE_KEY.getBytes());
			md5sum = digest.digest();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		StringBuffer hexString = new StringBuffer();
		for (int k = 0; k < md5sum.length; ++k) {
			String s = Integer.toHexString((int) md5sum[k] & 0xFF);
			if (s.length() == 1)
				hexString.append('0');
			hexString.append(s);
		}
		return hexString.toString();
	}

	public static void main(String[] args) {
		new Discoverer(null).start();
		while (true) {
		}
	}
}
