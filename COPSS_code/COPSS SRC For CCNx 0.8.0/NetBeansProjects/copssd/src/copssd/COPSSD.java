package copssd;

import common.NetworkClient;
import common.NetworkListener;
import copss.protocol.COPSSProtocolDTags;
import copss.protocol.Control;
import copss.protocol.Control.ControlType;
import copss.protocol.Multicast;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.impl.CCNNetworkManager.NetworkProtocol;
import org.ccnx.ccn.impl.InterestTable;
import org.ccnx.ccn.impl.InterestTable.Entry;
import org.ccnx.ccn.impl.encoding.CCNProtocolDTags;
import org.ccnx.ccn.impl.encoding.XMLCodecFactory;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.io.content.ContentEncodingException;
import org.ccnx.ccn.profiles.ccnd.CCNDaemonException;
import org.ccnx.ccn.profiles.ccnd.FaceManager;
import org.ccnx.ccn.profiles.ccnd.PrefixRegistrationManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 * COPSS router daemon process.
 *
 * The COPSS router has a thread listen to all the incoming packets.
 *
 * When a link is established using link function, the process creates a face in
 * NDN and creates a thread tunnel the packet from NDN to target node.
 *
 * If an RP is established in the COPSS router, a special module RP is created
 * and listens to the NDN FIB prefix.
 *
 * @author Jiachen Chen
 */
@SuppressWarnings("CallToThreadDumpStack")
public class COPSSD extends NetworkListener {

    /**
     * The port that NDN listens to.
     */
    private int _ccnPort;
    /**
     * CD to RP mapping table. It is instantiated as an Interest Table, so that
     * RP will be calculated using the longest prefix match according to CD. If
     * the CD to RP mapping table is prefix free, longest prefix match is equal
     * to exact match.
     */
    private InterestTable<ContentName> _cdRPMappingTable = new InterestTable<>();
    /**
     * Subscription table, also instantiated as an Interest Table.
     */
    private InterestTable<OutLinkFace> _st = new InterestTable<>();
    /**
     * Faces of the COPSS router.
     */
    private HashMap<InetSocketAddress, OutLinkFace> _faces;
    /**
     * The RP modules existed on the router.
     */
    private LinkedList<RP> _rps = new LinkedList<>();
    /**
     * NDN handle.
     */
    private CCNHandle _handle;
    /**
     * Face manager of NDN.
     */
    private FaceManager _faceManager;
    /**
     * Prefix registration manager from NDN.
     */
    private PrefixRegistrationManager _prefixRegManager;

    /**
     * Create a COPSS router daemon.
     *
     * @param listenPort the port COPSS router listens to.
     * @param ccnPort the NDN port.
     * @throws SocketException
     * @throws MalformedContentNameStringException
     * @throws CCNDaemonException
     */
    public COPSSD(int listenPort, int ccnPort) throws SocketException, MalformedContentNameStringException, CCNDaemonException {
        super(listenPort);
        _ccnPort = ccnPort;

        // get NDN objects.
        _handle = CCNHandle.getHandle();
        _faceManager = new FaceManager(_handle);
        _prefixRegManager = new PrefixRegistrationManager(_handle);

        // instantiate data structure for faces.
        _faces = new HashMap<>();

        loadCDRPMapping();
    }

    private void loadCDRPMapping() {
        //TODO: replace hard-code with reading mapping form a file
        _cdRPMappingTable.add(ContentName.ROOT, 
                new ContentName("RP")
//                ContentName.fromNative(new String[]{"RP"})
                );
    }

    /**
     * Link to a node (create face and related thread, structure).
     *
     * @param address target address.
     * @param isRouter if the target is a router (or an end-host).
     * @return face ID in NDN, or -1 if the face already exists.
     * @throws UnknownHostException
     * @throws IOException
     * @throws CCNDaemonException
     */
    public int link(InetSocketAddress address, boolean isRouter) throws UnknownHostException, IOException, CCNDaemonException {
        if (_faces.containsKey(address)) {
            return -1;
        }
        OutLinkFace face = new OutLinkFace(address, isRouter);
        new Thread(face).start();

        _faces.put(address, face);
        return face._faceID;
    }

    /**
     * Add an FIB entry in NDN.
     *
     * @param prefix the prefix of the FIB entry.
     * @param outgoingAddress outgoing address of the FIB entry.
     * @return true if add succeed, false if the face does not exist.
     * @throws CCNDaemonException
     */
    public boolean addFIB(ContentName prefix, InetSocketAddress outgoingAddress) throws CCNDaemonException {
        OutLinkFace f = _faces.get(outgoingAddress);
        if (f == null) {
            return false;
        }
        f.addCCNFIB(prefix);
        return true;
    }

    /**
     * Create a new RP module using rpName.
     *
     * @param rpName the name of the new RP module.
     * @throws IOException
     */
    public void setRP(ContentName rpName) throws IOException {
        _rps.add(new RP(rpName));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("COPSSD: listen=");
        builder.append(_listenPort);
        builder.append(", ccn=");
        builder.append(_ccnPort);
        builder.append(", running=");
        builder.append(_running);
        builder.append(", RPS=");
        builder.append(_rps);
        for (Entry<OutLinkFace> e : _st.values()) {
            builder.append("\n\t\t");
            builder.append(e.name());
            builder.append("->");
            builder.append(e.value()._remoteAddress);
        }
        builder.append("\n");
        for (OutLinkFace f : _faces.values()) {
            builder.append("\n\t\t");
            builder.append(f);
        }
        return builder.toString();
    }

    /**
     * Stop the COPSS daemon, RP modules and all the faces it listens to.
     */
    @Override
    public void stop() {
        super.stop();
        for (OutLinkFace f : _faces.values()) {
            f.stop();
        }
        for (RP rp : _rps) {
            rp.stop();
        }
        _handle.close();
    }

    /**
     * Handles packets.
     *
     * If the packet comes from an unknown face, discard the packet. Otherwise,
     * Process multicast using "handleMulticastPacket" Process control using
     * "handleControlPacket" Process Interest if it is an encapsulated Control
     * packet (need to find a better solution). Otherwise, send to NDN.
     *
     * @param packet the incoming packet.
     */
    @Override
    protected void handlePacket(DatagramPacket packet) {
        InetSocketAddress remoteAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
//        System.out.printf("Packet from: %s%n", remoteAddress);
        OutLinkFace f = _faces.get(remoteAddress);
        //discard packets from unknown source
        if (f == null) {
//            for (OutLinkFace fx : _faces.values()) {
//                System.out.println(fx._remoteAddress);
//            }
            System.out.printf("Cannot find face %s.%n", remoteAddress);
            return;
        }
        byte[] content = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, content, 0, content.length);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(content)) {
            XMLDecoder decoder = XMLCodecFactory.getDecoder();
            decoder.beginDecoding(bais);
            int type = decoder.peekStartElementAsLong().intValue();
            switch (type) {
                case COPSSProtocolDTags.Multicast: {
                    Multicast multicast = new Multicast();
                    multicast.decode(decoder);
                    handleMulticastPacket(multicast, f, content);
                    break;
                }
                case COPSSProtocolDTags.Control: {
                    Control control = new Control();
                    control.decode(decoder);
                    handleControlPacket(control, f);
                    break;
                }
                case CCNProtocolDTags.Interest: {
                    // Check if it is an encapsulated Control
                    // Bad implementation!
                    Interest interest = new Interest();
                    interest.decode(decoder);
                    Control c = new Control();
                    if (c.decapsulate(interest)) {
                        handleControlPacket(c, f);
                        break;
                    }
                    // If not, write to CCN
                }
                default: {
//                    System.out.println("Writing to CCN...");
                    f.writeToCCN(content);
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Processes the control packet. Only process the ST change here.
     *
     * 1) Change the local ST first and calculate the delta (newly added
     * subscription and the unsubscribe to the last subscription). 2)
     * Encapsulate the delta into multiple Interest packets and sends them to
     * the NDN.
     *
     * Actually, the 2nd stage should be implemented as split delta according to
     * CD2RP mapping, and forward the changes to the target faces according to
     * FIB. However, NDN doesn't allow us to look up FIB, so we have to
     * encapsulate them into Interest packets and ask NDN to forward the packet.
     *
     * Need a better solution for this.
     *
     * @param control the incoming control packet.
     * @param face the incoming face.
     * @throws ContentEncodingException
     * @throws IOException
     */
    private void handleControlPacket(Control control, OutLinkFace face) throws ContentEncodingException, IOException {
//        System.out.printf("Receive control [%s]: %s%n", face, control);
        if (control.type() == ControlType.STChange) {
            LinkedList<ContentName> resultAdds = new LinkedList<>(),
                    resultRemoves = new LinkedList<>();

            // modify ST
            for (ContentName CD : control.contentNameAdd()) {
                boolean hasSameCD = false, alreadySubscribed = false;
                for (Entry<OutLinkFace> entry : _st.getMatches(CD)) {
                    if (entry.name().equals(CD)) {
                        hasSameCD = true;
                        if (entry.value().equals(face)) {
                            alreadySubscribed = true;
                        }
                    }
                }
                if (!alreadySubscribed) {
                    _st.add(CD, face);
                    if (!hasSameCD) {
                        resultAdds.add(CD);
                    }
                }
            }

            for (ContentName CD : control.contentNameRemove()) {
                Entry<OutLinkFace> entry = _st.remove(CD, face);
                // if the subscriber really subscribed to the CD
                if (entry != null) {
                    entry = _st.getMatch(CD);
                    // if nobody subscribed to the same CD, continue unsubscription
                    if (entry == null || !entry.name().equals(CD)) {
                        resultRemoves.add(CD);
                    }
                }
            }
//            System.out.printf("ResultAdds:%s%nResultRemoves%s%n", resultAdds, resultRemoves);
            control = new Control(ControlType.STChange, resultAdds, resultRemoves, 0, 0);
//            System.out.println(control);
            // Split, encapsulate and forward
            for (Interest encap : control.encapsulate(_cdRPMappingTable)) {
                face.writeToCCN(encap.encode());
            }

        } else {
            System.out.printf("Invalid Control Type: %s%n", control.type());
        }
    }

    /**
     * Handles the multicast packet.
     *
     * If it comes from an end host (I'm the 1st hop router): encapsulate the
     * multicast into (multiple) Interest(s). write to NDN else (I'm the
     * internal router) forward using ST.
     *
     * @param multicast the incoming multicast packet.
     * @param face the incoming face.
     * @param originalContent the original byte array of the packet. If it is
     * forward using ST, the we can send the original content out directly.
     * @throws ContentEncodingException
     * @throws IOException
     */
    private void handleMulticastPacket(Multicast multicast, OutLinkFace face, byte[] originalContent) throws ContentEncodingException, IOException {
        // If from a router or from RP, do multicast
        if (face == null || face._isRouter) {
            LinkedList<OutLinkFace> faces = new LinkedList<>();
            for (ContentName CD : multicast.contentNames()) {
                for (OutLinkFace f : _st.getValues(CD)) {
                    if (f != face && !faces.contains(f)) {
                        faces.add(f);
                    }
                }
            }
            for (OutLinkFace f : faces) {
                f.writeToRemote(originalContent);
            }
        } else {
            LinkedList<Interest> encaps = multicast.encapsulate(_cdRPMappingTable);
            for (Interest encap : encaps) {
                face.writeToCCN(encap.encode());
            }
        }
    }

    /**
     * RP module
     */
    class RP {

        /**
         * The name of the RP.
         */
        public ContentName _prefix;
        /**
         * Register a prefix in NDN and listens to the packet. If it is an
         * encapsulated multicast packet, decapsulate it. Otherwise, return
         * false.
         */
        private CCNInterestHandler _handler = new CCNInterestHandler() {

            @Override
            public boolean handleInterest(Interest interest) {
                Multicast multicast = new Multicast();
                try {
                    if (multicast.decapsulate(interest)) {
//                            System.out.println(multicast);
                        handleMulticastPacket(multicast, null, multicast.encode());
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        };
//        private CCNFilterListener _listener = new CCNFilterListener() {
//
//            @Override
//            public boolean handleInterest(Interest interest) {
//                Multicast multicast = new Multicast();
//                try {
//                    if (multicast.decapsulate(interest)) {
////                            System.out.println(multicast);
//                        handleMulticastPacket(multicast, null, multicast.encode());
//                        return true;
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                return false;
//            }
//        };

        public RP(ContentName prefix) throws IOException {
            _prefix = prefix;
            _handle.registerFilter(prefix, _handler);
//            _handle.registerFilter(prefix, _listener);
        }

        public void stop() {
            _handle.unregisterFilter(_prefix, _handler);
//            _handle.unregisterFilter(_prefix, _listener);
        }

        @Override
        public String toString() {
            return _prefix.toString();
        }
    }

    /**
     * The tunnel for a face. On receiving a packet from NDN, forward it to
     * remote using COPSS listen port. Also provide functions like add/remove
     * FIB, write to NDN or remote.
     */
    class OutLinkFace extends NetworkClient {

        /**
         * The face ID in NDN.
         */
        public Integer _faceID;
        /**
         * Remote face address.
         */
        public InetSocketAddress _remoteAddress;
        /**
         * If remote is a router.
         */
        public boolean _isRouter;

        public OutLinkFace(InetSocketAddress remoteAddress, boolean isRouter) throws SocketException, UnknownHostException, CCNDaemonException {

            super(new InetSocketAddress(GetLocalhostAddress(), _ccnPort));

            _remoteAddress = remoteAddress;
            _isRouter = isRouter;

            // Register a face in NDN.
            _faceID = _faceManager.createFace(NetworkProtocol.UDP, GetLocalhostAddress().getHostAddress(), getLocalPort(), Integer.MAX_VALUE);
        }

        /**
         * Add FIB in NDN, prefix->local listen port.
         *
         * @param prefix the prefix to add.
         * @throws CCNDaemonException
         */
        public void addCCNFIB(ContentName prefix) throws CCNDaemonException {
            _prefixRegManager.registerPrefix(prefix, _faceID, PrefixRegistrationManager.CCN_FORW_ACTIVE | PrefixRegistrationManager.CCN_FORW_CHILD_INHERIT);
        }

        /**
         * Remove an FIB entry.
         *
         * @param prefix the prefix to be removed.
         * @throws CCNDaemonException
         */
        public void removeCCNFIB(ContentName prefix) throws CCNDaemonException {
            _prefixRegManager.unRegisterPrefix(prefix, _faceID);
        }

        /**
         * Write a packet to NDN using local listen port.
         *
         * @param buf the packet content.
         * @throws IOException
         */
        public void writeToCCN(byte[] buf) throws IOException {
            send(buf);
        }

        /**
         * Write a packet to remote address using COPSS listen port.
         *
         * @param buf the packet content.
         * @throws IOException
         */
        public void writeToRemote(byte[] buf) throws IOException {
//            System.out.println(_remoteAddress);
            COPSSD.this.send(_remoteAddress, buf);
        }

        @Override
        public void stop() {
            super.stop();
            try {
                _faceManager.deleteFace(_faceID);
            } catch (CCNDaemonException ex) {
                ex.printStackTrace();
            }
        }

        /**
         * On receive a packet from NDN, forward it to the remote address using
         * COPSS port.
         *
         * @param packet the packet received.
         */
        @Override
        protected void handlePacket(DatagramPacket packet) {
            byte[] buf = new byte[packet.getLength()];
//            System.out.printf("WriteToRemote: %s%n", _remoteAddress);
            System.arraycopy(packet.getData(), 0, buf, 0, buf.length);
            try {
                writeToRemote(buf);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return String.format("Face %d, %s, %b", _faceID, _remoteAddress, _isRouter);
        }
    }
}