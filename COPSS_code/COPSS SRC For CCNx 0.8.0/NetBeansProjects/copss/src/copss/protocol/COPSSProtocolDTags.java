package copss.protocol;

/**
 * Constants for COPSS packet serialization
 *
 * @author Jiachen Chen
 */
public final class COPSSProtocolDTags {

    /**
     * Packet type Control
     */
    public static final int Control = 200;
    /**
     * Packet type Multicast
     */
    public static final int Multicast = 201;
    /**
     * Control packet sub type FIB change
     */
    public static final int FIBChange = 211;
    /**
     * Control packet sub type ST change
     */
    public static final int STChange = 212;
    /**
     * Control packet field ContentName add count
     */
    public static final int ContentNameAddCount = 220;
    /**
     * Control packet field ContentName remove count
     */
    public static final int ContentNameRemoveCount = 221;
    /**
     * Control packet field Control type
     */
    public static final int ControlType = 230;
    /**
     * Control packet field version
     */
    public static final int Version = 231;
    /**
     * Control packet field TTL
     */
    public static final int TTL = 232;
    /**
     * Multicast packet field ContentName add count
     */
    public static final int Content = 233;
}