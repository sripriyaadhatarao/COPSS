package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 *
 * @author Jiachen Chen
 */
public abstract class NetworkListener extends NetworkNode {

    protected final int _listenPort;

    public NetworkListener(int listenPort) throws SocketException {
        super(new DatagramSocket(listenPort));
        _listenPort = listenPort;
    }

    protected void send(InetSocketAddress target, byte[] buf) throws IOException {
        DatagramPacket dp = new DatagramPacket(buf, buf.length, target);
        _listenSocket.send(dp);
    }
}