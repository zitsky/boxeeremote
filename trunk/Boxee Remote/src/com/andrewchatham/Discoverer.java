package com.andrewchatham;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
	
	// TODO: Vary the challenge, or it's not much of a challenge :)
	private static final String mChallenge = "My voice is my passport. Verify me.";
	private WifiManager mWifi;
	
	Discoverer(WifiManager wifi) {
		mWifi = wifi;
	}

	public void run() {
		try {
			sendDiscoveryRequest();
		} catch (IOException e) {
			Log.e(TAG, "Could not send discovery request", e);
		}
	}
	
	private void sendDiscoveryRequest() throws IOException {
		final int DISCOVERY_PORT = 2562;

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
			String s = Integer.toHexString(md5sum[k]);
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
