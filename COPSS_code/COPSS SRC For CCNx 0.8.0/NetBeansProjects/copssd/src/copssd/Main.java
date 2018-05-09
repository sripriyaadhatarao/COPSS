package copssd;

import copss.protocol.Control;
import copss.protocol.Multicast;
import copss.util.Utility;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccnx.ccn.impl.InterestTable;
import org.ccnx.ccn.io.content.ContentDecodingException;
import org.ccnx.ccn.io.content.ContentEncodingException;
import org.ccnx.ccn.profiles.ccnd.CCNDaemonException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 *
 * @author Jiachen Chen
 */
@SuppressWarnings("CallToThreadDumpStack")
public class Main {

    private static COPSSD copssd;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        runCOPSSD(args);
    }

    public static void runCOPSSD(String[] args) throws Exception {
//        if (args.length < 2) {
//            System.out.println("Usage: java COPSSD.java %listenAddress% %listenPort%");
//            return;
//        }
//        String listenAddress = args[0];
//        int listenPort = Integer.parseInt(args[1]);
        copssd = new COPSSD(Utility.DEFAULT_COPSS_PORT, Utility.DEFAULT_CCN_PORT);

        copssd.start();

        try (BufferedReader reader = new BufferedReader(new FileReader("Command.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (handleCommand(line)) {
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(copssd);
        help();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("COPSSD started, you can type commands now. 18.10");
        for (String line = reader.readLine(); !handleCommand(line); line = reader.readLine()) {
        }


    }

    public static boolean handleCommand(String line) {
        String[] parts = line.split(" ");
        if (parts[0].equals("s") || parts[0].equals("stop")) {
            try {
                copssd.close();
            } catch (Exception ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Cannot stop COPSSD?", ex);
            }
            return true;
        }
        if (parts[0].equals("help")) {
            help();
            return false;
        }
        if (parts[0].equals("link")) {
            if (parts.length != 4) {
                System.out.println("Usage: link %address% %port% %isRouter%");
                return false;
            }
            String address = parts[1];
            int port = Integer.parseInt(parts[2]);
            boolean isRouter = Boolean.parseBoolean(parts[3]);
            try {
                InetSocketAddress destination = new InetSocketAddress(InetAddress.getByName(address), port);
                int faceID = copssd.link(destination, isRouter);
                if (faceID == -1) {
                    System.out.printf("Already linked to %s%n", destination);
                } else {
                    System.out.printf("Linked to: %s(%b)=%d%n", destination, isRouter, faceID);
                }
            } catch (IOException | CCNDaemonException e) {
                e.printStackTrace();
            }
            return false;
        }
        if (parts[0].equals("status")) {
            System.out.println(copssd);
            return false;
        }
        if (parts[0].equals("FIB")) {
            if (parts.length != 4) {
                System.out.println("Usage: FIB %name% %address% %port%");
                return false;
            }
            try {
                ContentName name = ContentName.fromNative(parts[1]);
                String address = parts[2];
                int port = Integer.parseInt(parts[3]);
                InetSocketAddress destination = new InetSocketAddress(InetAddress.getByName(address), port);
                if (copssd.addFIB(name, destination)) {
                    System.out.printf("Add FIB: %s -> %s%n", name, destination);
                } else {
                    System.out.printf("Face not exist: %s%n", name, destination);
                }
            } catch (MalformedContentNameStringException | NumberFormatException | UnknownHostException | CCNDaemonException e) {
                e.printStackTrace();
            }
            return false;
        }
        if (parts[0].equals("RP")) {
            if (parts.length != 2) {
                System.out.println("Usage: RP %RPName%");
                return false;
            }
            try {
                ContentName name = ContentName.fromNative(parts[1]);
                copssd.setRP(name);
                System.out.printf("RP %s set.%n", name);
            } catch (MalformedContentNameStringException | IOException e) {
                e.printStackTrace();
            }
            return false;
        }
        System.out.println("Invalid Command!");
        return false;
    }

    public static void help() {
        System.out.println("Commands available:");
        System.out.println("link %address% %port% %isRouter%: link to a node on address:port and tells if the node is a router.");
        System.out.println("FIB %name% %address% %port%: add an FIB entry name->address:port");
        System.out.println("RP %RPName%: starts an RP module using RPName");
        System.out.println("status: show the status of the COPSSD");
        System.out.println("help: show this message.");
        System.out.println("stop: stop COPSSD.");
    }

    public static InterestTable<ContentName> getCD2RPMappingTable() throws MalformedContentNameStringException {
        InterestTable<ContentName> CD2RPMapping = new InterestTable<>();
        CD2RPMapping.add(ContentName.fromNative("/sports"), ContentName.fromNative("/RP1"));
        CD2RPMapping.add(ContentName.fromNative("/sports2"), ContentName.fromNative("/RP2"));
        CD2RPMapping.add(ContentName.fromNative("/sports3"), ContentName.fromNative("/RP1"));
        return CD2RPMapping;
    }

    public static void ControlSplitTest(String[] args) throws MalformedContentNameStringException, ContentEncodingException, ContentDecodingException, IOException {

        InterestTable<ContentName> CD2RPMapping = getCD2RPMappingTable();

        LinkedList<ContentName> adds = new LinkedList<>();
        LinkedList<ContentName> removes = new LinkedList<>();

        adds.add(ContentName.fromNative("/sports3/football"));
        adds.add(ContentName.fromNative("/sports2/football"));
        adds.add(ContentName.fromNative("/sports1/football"));
        adds.add(ContentName.fromNative("/sports/football"));

        removes.add(ContentName.fromNative("/sports/basketball"));

        Control control = new Control(Control.ControlType.STChange, adds, removes, 0, 0);

        LinkedList<Interest> encaps = control.encapsulate(CD2RPMapping);
        System.out.println(encaps.size());
        for (Interest interest : encaps) {
            System.out.println(interest);

            Control c = new Control();
            if (c.decapsulate(interest)) {
                System.out.println(c);
            }
        }
    }

    public static void MulticastSplitTest(String[] args) throws MalformedContentNameStringException, ContentEncodingException, UnsupportedEncodingException, IOException {
        InterestTable<ContentName> CD2RPMapping = getCD2RPMappingTable();

        LinkedList<ContentName> CDs = new LinkedList<>();
        CDs.add(ContentName.fromNative("/sports3/football"));
        CDs.add(ContentName.fromNative("/sports2/football"));
        CDs.add(ContentName.fromNative("/sports1/football"));
        CDs.add(ContentName.fromNative("/sports/football"));
        CDs.add(ContentName.fromNative("/sports/basketball"));

        byte[] content = new byte[16];
        Random rand = new Random();
        rand.nextBytes(content);

        Multicast multicast = new Multicast(CDs, content);

        LinkedList<Interest> encaps = multicast.encapsulate(CD2RPMapping);

        for (Interest encap : encaps) {
            System.out.println(encap);
            Multicast m = new Multicast();
            if (m.decapsulate(encap)) {
                System.out.println(m);
            }

        }

    }
}