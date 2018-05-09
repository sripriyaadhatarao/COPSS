/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author root
 */
public class NetworkListenerTester extends NetworkListener {

    public NetworkListenerTester(int listenPort) throws SocketException {
        super(listenPort);


    }

    @Override
    protected void handlePacket(DatagramPacket packet) {
        try {
            String msg = new String(packet.getData(), "UTF-8");
            System.out.printf("Got packet: addr=%s, msg=%s%n", packet.getAddress(), msg);
            scheduleSend((InetSocketAddress)packet.getSocketAddress(), "ECHO: " + msg);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(NetworkListenerTester.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }
    
    private void scheduleSend(InetSocketAddress address, String message) {
        try {
            Thread.sleep(10000);
            System.out.printf("Send packet: addr=%s, msg=%s%n", address, message);
            send(address, message.getBytes("UTF-8"));
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(NetworkListenerTester.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    public static void main(String[] args) throws SocketException {
        NetworkListenerTester tester = new NetworkListenerTester(9090);

        new Thread(tester).start();
        
    }
}