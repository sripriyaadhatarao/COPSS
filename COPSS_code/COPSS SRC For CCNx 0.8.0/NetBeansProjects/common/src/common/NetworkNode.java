package common;

import java.net.*;

/**
 * NetworkNode class creates a thread that listens to a UDP socket
 * (DatagramSocket). On receiving a UDP packet from the remote end, handlePacket
 * function will be called.
 *
 * @author Jiachen Chen
 */
@SuppressWarnings("CallToThreadDumpStack")
public abstract class NetworkNode implements Runnable, AutoCloseable {

    public static final String STR_LOCALHOST = "localhost";

    public static InetAddress GetLocalhostAddress() throws UnknownHostException {
        return Inet4Address.getByName(STR_LOCALHOST);
    }
    /**
     * Indicates if the node is listening
     */
    protected boolean _running = false;
    /**
     * The UDP socket the node listens to
     */
    protected final DatagramSocket _listenSocket;
    private Thread _runningThread;

    /**
     * Creates a NetworkNode using an existing UDP socket.
     *
     * @param listenSocket the existing UDP socket the node will listen to.
     */
    public NetworkNode(DatagramSocket listenSocket) {
        _listenSocket = listenSocket;
    }

    /**
     * Start a thread that runs the node.
     */
    public synchronized void start() throws Exception {
        if (_runningThread != null) {
            throw new Exception("Node already running!");
        }
        _runningThread = new Thread(this);
        _runningThread.start();
    }

    /**
     * Join the node thread.
     */
    public void join() throws InterruptedException {
        if (_runningThread != null) {
            _runningThread.join();
        }
    }

    /**
     * Stop the listen thread.
     */
    public void stop() {
        _running = false;
    }

    @Override
    public void close() throws Exception {
        stop();
        join();
    }

    /**
     * Get the local port of the listening UDP socket.
     *
     * @return the local port of the listening UDP socket.
     */
    public int getLocalPort() {
        return _listenSocket.getLocalPort();
    }

    /**
     * The handler for packets received from remote.
     *
     * @param packet the packet received.
     */
    protected abstract void handlePacket(DatagramPacket packet);

    /**
     * Listen to the UDP socket until _running == false.
     */
    @Override
    public void run() {
        if (_running) {
            return;
        }
        _running = true;

        byte[] buf = new byte[8192];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        //to enable stopping of receive
        try {
            _listenSocket.setSoTimeout(1000);
        } catch (SocketException ex) {
            ex.printStackTrace();
//            Logger.getLogger(NetworkNode.class.getName()).log(Level.SEVERE, "Error in starting network node", ex);
        }

        while (_running) {
            try {
                //System.out.println(_listenSocket.getLocalSocketAddress() + "->" + _listenSocket.getRemoteSocketAddress());
                _listenSocket.receive(dp);
                handlePacket(dp);
            } catch (SocketTimeoutException e) {
                // listen timeout. do nothing.
            } catch (Exception ex) {
                ex.printStackTrace();
//                Logger.getLogger(NetworkNode.class.getName()).log(Level.SEVERE, "Error in receiving packet in network node", ex);
            }
        }
        System.out.println("Listen thread finished!");
        _runningThread = null;
    }
}