package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * NetworkClient connects to a remote address using a random local port.
 * 
 * On receiving a packet from the remote address, handlePacket will be called.
 * The send function sends a packet to the remote address.
 * 
 * @author Jiachen Chen
 */
public abstract class NetworkClient extends NetworkNode {

    /**
     * Create a UDP socket and connect it to the firstHopAddress.
     * 
     * @param firstHopAddress the first hop address the UDP socket is going to connect.
     * @return the new UDP socket.
     * @throws SocketException connection failure.
     */
    private static DatagramSocket getDatagramSocket(InetSocketAddress firstHopAddress) throws SocketException {
        DatagramSocket socket = new DatagramSocket();
        socket.connect(firstHopAddress.getAddress(), firstHopAddress.getPort());
        return socket;
    }
    
    /** The first hop address the client linked to. */
    protected InetSocketAddress _firstHopAddress;

    /**
     * Create a network client and connect it to the first hop address.
     * 
     * @param firstHopAddress the first hop address specified.
     * @throws SocketException connection failure.
     */
    public NetworkClient(InetSocketAddress firstHopAddress) throws SocketException {
        super(getDatagramSocket(firstHopAddress));
        _firstHopAddress = firstHopAddress;
    }

    /**
     * Send a packet to the first hop address.
     * 
     * @param buf the content of the packet.
     * @throws IOException send failure.
     */
    protected void send(byte[] buf) throws IOException {
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        _listenSocket.send(dp);
    }
}