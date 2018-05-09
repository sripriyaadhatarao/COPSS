package simplecopssclient;

import common.NetworkListener;
import copss.protocol.COPSSProtocolDTags;
import copss.protocol.Control;
import copss.protocol.Multicast;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccnx.ccn.impl.encoding.XMLCodecFactory;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;


public class SimpleCOPSSClient extends NetworkListener {

    public static final InetSocketAddress COPSS_ADDRESS = new InetSocketAddress(STR_LOCALHOST, 9696);

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar SimpleCOPSSClient.jar %listenPort%");
            return;
        }
        int port = Integer.parseInt(args[0]);
        SimpleCOPSSClient client;
        try {
            client = new SimpleCOPSSClient(port);
            client.start();
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            return;
        }

        LinkedList<ContentName> subscribedCDs = new LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            System.out.println("Type help to get help.");
            System.out.print("> ");
            boolean exit = false;
            while ((line = reader.readLine()) != null) {
                switch (line.toLowerCase()) {
                    case "sub": {
                        System.out.println("Please input CDs to subscribe (1 CD per line), end with an empty line");
                        while (true) {
                            System.out.print("? ");
                            line = reader.readLine();
                            if (line.equals("")) {
                                break;
                            }
                            try {
                                ContentName CD = ContentName.fromNative(line);
                                if (!subscribedCDs.contains(CD)) {
                                    LinkedList<ContentName> add = new LinkedList<>(), del = new LinkedList<>();
                                    add.add(CD);
                                    client.Subscribe(add, del);
                                    subscribedCDs.add(CD);
                                    System.out.printf("Subscription to %s done.%n", CD);
                                } else {
                                    System.out.printf("%s already subscribed.%n", CD);
                                }
                            } catch (MalformedContentNameStringException ex) {
                                System.out.println("Invalid CD. Should be in the format of a ContentName.");
                            }
                        }
                        break;
                    }
                    case "unsub": {
                        System.out.println("Please input CDs to unsubscribe (1 CD per line), end with an empty line");
                        while (true) {
                            System.out.print("? ");
                            line = reader.readLine();
                            if (line.equals("")) {
                                break;
                            }
                            try {
                                ContentName CD = ContentName.fromNative(line);
                                if (subscribedCDs.contains(CD)) {
                                    LinkedList<ContentName> add = new LinkedList<>(), del = new LinkedList<>();
                                    del.add(CD);
                                    client.Subscribe(add, del);
                                    subscribedCDs.remove(CD);
                                    System.out.printf("Subscription to %s removed.%n", CD);
                                } else {
                                    System.out.printf("%s not subscribed.%n", CD);
                                }
                            } catch (MalformedContentNameStringException ex) {
                                System.out.println("Invalid CD. Should be in the format of a ContentName.");
                            }
                        }
                        break;
                    }
                    case "pub": {
                        LinkedList<ContentName> CDs = new LinkedList<>();
                        System.out.println("Please input CDs to publish (1 CD per line), end with an empty line");
                        while (true) {
                            System.out.print("? ");
                            line = reader.readLine();
                            if (line.equals("")) {
                                break;
                            }
                            try {
                                ContentName CD = ContentName.fromNative(line);
                                if (!CDs.contains(CD)) {
                                    CDs.add(CD);
                                    System.out.printf("CDs: %s%n", Arrays.toString(CDs.toArray()));
                                } else {
                                    System.out.printf("%s already in the CD list.%n", CD);
                                }
                            } catch (MalformedContentNameStringException ex) {
                                System.out.println("Invalid CD. Should be in the format of a ContentName.");
                            }
                        }
                        if (CDs.size() == 0) {
                            System.out.println("No CD listed. Cannot publish. Please try pub again.");
                            break;
                        }
                        System.out.println("Please input content publish, end with an empty line");
                        StringBuilder buffer = new StringBuilder();
                        while (true) {
                            System.out.print("? ");
                            line = reader.readLine();
                            if (line.equals("")) {
                                break;
                            }
                            buffer.append(line);
                            buffer.append("\r\n");
                        }
                        ContentName[] tmp = new ContentName[CDs.size()];
                        CDs.toArray(tmp);
                        client.Publish(buffer.toString(), tmp);
                        System.out.println("Message sent.");
                        break;
                    }
                    case "help": {
                        System.out.println("Help:");
                        System.out.println("Type one of the following commands after the prompt. You will get further instructions after that.");
                        System.out.println("sub: subscribe to a set of CDs");
                        System.out.println("unsub: unsubscribe from a set of CDs");
                        System.out.println("pub: publish a message");
                        System.out.println("help: print this message");
                        System.out.println("stop: cleanup the states and exit the program");
                        break;
                    }
                    case "stop": {
                        LinkedList<ContentName> add = new LinkedList<>(), del = new LinkedList<>(subscribedCDs);
                        client.Subscribe(add, del);
                        subscribedCDs.clear();
                        System.out.printf("Subscriptions %s removed.%n", Arrays.toString(del.toArray()));
                        exit = true;
                        break;
                    }
                    default: {
                        System.out.println("Invalid command. Type \"help\" to view all the commands.");
                        break;
                    }
                }
                if (exit) {
                    break;
                }

                System.out.print("> ");
            }
            client.stop();
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
    }

    public SimpleCOPSSClient(int listenPort) throws SocketException {
        super(listenPort);
    }

    public void Subscribe(LinkedList<ContentName> cdsAdd, LinkedList<ContentName> cdsRemove) throws IOException {
        Control ctrl = new Control(Control.ControlType.STChange, cdsAdd, cdsRemove, 0, -1);
        send(ctrl.encode());
    }

    public void Publish(String message, ContentName[] CDs) throws IOException {
        LinkedList<ContentName> cds = new LinkedList<>();
        cds.addAll(Arrays.asList(CDs));

        Multicast multicast = new Multicast(cds, message.getBytes("UTF8"));

        send(multicast.encode());
    }

    protected void send(byte[] buf) throws IOException {
        send(COPSS_ADDRESS, buf);
    }

    @Override
    protected void handlePacket(DatagramPacket packet) {
//        System.out.println("HERE");
        InetSocketAddress remoteAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

        if (!remoteAddress.equals(COPSS_ADDRESS)) {
            System.out.printf("Ignore packet from: %s%n", remoteAddress);
        }

        byte[] content = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, content, 0, content.length);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(content)) {
//        	System.out.println("INSIDE TRY");
            XMLDecoder decoder = XMLCodecFactory.getDecoder();
            decoder.beginDecoding(bais);
            int type = decoder.peekStartElementAsLong().intValue();
//            System.out.println("TYPE IS " + type);
            switch (type) {
                case COPSSProtocolDTags.Multicast:
                    Multicast m = new Multicast();
                    m.decode(decoder);
                    HandleMulticast(m);
                    break;
                default:
                    System.out.printf("Invalid packet type: %d. Ignore.%n", type);
//                    throw new Exception("Illegal packet type");
            }

        } catch (Exception ex) {

            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error in packet handling", ex);
        }
    }

    private void HandleMulticast(Multicast multicast) {
        ContentName[] CDs = new ContentName[multicast.contentNames().size()];
        multicast.contentNames().toArray(CDs);
        byte[] content = multicast.content();
        String msg = null;
        try {
            msg = new String(content, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
        }
        System.out.printf("Got msg CDs=%s, msg=%s%n", Arrays.toString(CDs), msg);
    }

}