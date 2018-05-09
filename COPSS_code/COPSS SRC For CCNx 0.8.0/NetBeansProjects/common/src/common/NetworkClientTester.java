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

/**
 *
 * @author root
 */
public class NetworkClientTester extends NetworkClient {

    public NetworkClientTester(InetSocketAddress firstHopAddress) throws SocketException {
        super(firstHopAddress);
    }

    @Override
    protected void handlePacket(DatagramPacket packet) {
        System.out.printf("Got packet: len=%d, addr=%s", packet.getLength(), packet.getAddress());
    }

    public static void main(String[] args) throws SocketException, UnsupportedEncodingException, IOException {
        byte[] buf = "This is a test".getBytes("UTF-8");
        NetworkClientTester tester = new NetworkClientTester(new InetSocketAddress(STR_LOCALHOST, 9090));
        new Thread(tester).start();
        
        tester.send(buf);
        tester.stop();
        
    }
}