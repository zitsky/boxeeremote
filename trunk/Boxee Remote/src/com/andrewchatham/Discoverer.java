package com.andrewchatham;

import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

class BoxeeServer {
  HashMap<String, String> mAttr;

  private String mVersion;
  private String mName;
  private boolean mAuthRequired;
  private int mPort;
  private InetAddress mAddr;

  public BoxeeServer(HashMap<String, String> attributes, InetAddress address) {
    mAddr = address;
    mAttr = attributes;
    mVersion = attributes.get("version");
    mName = attributes.get("name");
    mPort = Integer.parseInt(attributes.get("httpPort"));

    String auth = attributes.get("httpAuthRequired");
    mAuthRequired = auth != null && auth.equals("true");
  }

  public String version() {
    return mVersion;
  }

  public String name() {
    return mName;
  }

  public boolean authRequired() {
    return mAuthRequired;
  }

  public int port() {
    return mPort;
  }

  public String toString() {
    return String.format("addr=%s port=%d version=%s name=%s auth_required=%s",
        mAddr, mPort,
        mVersion, mName, mAuthRequired);
  }
}

/*
 * This class tries to send a broadcast UDP packet over your wifi network to
 * discover the boxee service.
 */

public class Discoverer extends Thread {
  private static final String TAG = "Discovery";
  private static final String REMOTE_KEY = "b0xeeRem0tE!";
  private static final int DISCOVERY_PORT = 2562;
  private static final int TIMEOUT_MS = 500;

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
      DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT);
      socket.setBroadcast(true);
      socket.setSoTimeout(TIMEOUT_MS);

      sendDiscoveryRequest(socket);
      listenForResponses(socket);
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
  private void sendDiscoveryRequest(DatagramSocket socket) throws IOException {
    String data = String
        .format(
            "<bdp1 cmd=\"discover\" application=\"iphone_remote\" challenge=\"%s\" signature=\"%s\"/>",
            mChallenge, getSignature(mChallenge));
    Log.d(TAG, "Sending data " + data);

    DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(),
        getBroadcastAddress(), DISCOVERY_PORT);
    socket.send(packet);
  }

  /**
   * Calculate the broadcast IP we need to send the packet along. If we send it
   * to 255.255.255.255, it never gets sent. I guess this has something to do
   * with the mobile network not wanting to do broadcast.
   */
  private InetAddress getBroadcastAddress() throws IOException {
    DhcpInfo dhcp = mWifi.getDhcpInfo();
    if (dhcp == null) {
      Log.d(TAG, "Could not get dhcp info");
      return null;
    }

    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
    byte[] quads = new byte[4];
    for (int k = 0; k < 4; k++)
      quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
    return InetAddress.getByAddress(quads);
  }

  /**
   * Listen on socket for responses, timing out after TIMEOUT_MS
   * 
   * @param socket
   *          socket on which the announcement request was sent
   * @throws IOException
   */
  private void listenForResponses(DatagramSocket socket) throws IOException {
    byte[] buf = new byte[1024];
    try {
      while (true) {
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        String s = new String(packet.getData(), 0, packet.getLength());
        Log.d(TAG, "Received response " + s);
        parseResponse(s, ((InetSocketAddress) packet.getSocketAddress()).getAddress());
      }
    } catch (SocketTimeoutException e) {
      Log.d(TAG, "Receive timed out");
    }
  }

  private void parseResponse(String response, InetAddress address) throws IOException {
    SAXParserFactory spf = SAXParserFactory.newInstance();
    SAXParser sp;
    final HashMap<String, String> values = new HashMap<String, String>();
    try {
      sp = spf.newSAXParser();
      XMLReader xr = sp.getXMLReader();

      // Parse the document, sticking all elements from <BDP1/> into values.
      xr.setContentHandler(new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name,
            Attributes attributes) throws SAXException {
          if (localName == "BDP1") {
            for (int k = 0; k < attributes.getLength(); ++k) {
              String key = attributes.getLocalName(k);
              String value = attributes.getValue(k);
              values.put(key, value);
            }
          }
        }
      });
      xr.parse(new InputSource(new StringReader(response)));

    } catch (SAXException e) {
      Log.e(TAG, "Sax error on " + response, e);
    } catch (ParserConfigurationException e) {
      Log.e(TAG, "Sax error on " + response, e);
    }

    String challenge_response = values.get("response");
    String signature = values.get("signature");

    if (challenge_response != null && signature != null) {
      String legit_response = getSignature(challenge_response).toLowerCase();
      if (!legit_response.equals(signature.toLowerCase())) {
        Log.e(TAG, "Signature verification failed " + legit_response + " vs "
            + signature);
        return;
      }
    }
    Log.d(TAG, "Legit signature");

    String cmd = values.get("cmd");
    String app = values.get("application");
    
    if (cmd == null || !cmd.equals("found")) {
      Log.e(TAG, "Bad cmd " + response);
      return;
    }
    if (app ==null || !app.equals("boxee")) {
      Log.e(TAG, "Bad app " + app);
      return;
    }

    BoxeeServer server = new BoxeeServer(values, address);
    Log.d(TAG, "Discovered server " + server);
  }

  /**
   * Calculate the signature we need to send with the request. It is a string
   * containing the hex md5sum of the challenge and REMOTE_KEY.
   * 
   * @return signature string
   */
  private String getSignature(String challenge) {
    MessageDigest digest;
    byte[] md5sum = null;
    try {
      digest = java.security.MessageDigest.getInstance("MD5");
      digest.update(challenge.getBytes());
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
