package com.andrewchatham;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.util.Log;

/*
 * TODO(chatham): This class tries to send a broadcast packet to discover the boxee service. 
 * This works if I run it with regular java, but not on android, which makes me think that they
 * don't allow broadcast packets. Certainly, that would be problematic on a mobile network,
 * but I just want to do it on wifi.
 */

public class Discoverer extends Thread {
	private static final String TAG = "Discovery";
	private static final String CHALLENGE = "My voice is my passport. Verify me.";

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
						CHALLENGE, getSignature());
		Log.d(TAG, data);

		InetAddress group = InetAddress.getByName("255.255.255.255");
		DatagramPacket packet = new DatagramPacket(data.getBytes(), data
				.length(), group, DISCOVERY_PORT);
		DatagramSocket socket = new DatagramSocket();
		socket.setBroadcast(true);
		socket.send(packet);
	}

	private String getSignature() {
		final String REMOTE_KEY = "b0xeeRem0tE!";
		MessageDigest digest;
		byte[] md5sum = null;
		try {
			digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(CHALLENGE.getBytes());
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
		new Discoverer().start();
		while (true) {
		}
	}
}
