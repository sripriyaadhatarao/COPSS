package copss.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import org.ccnx.ccn.impl.InterestTable;
import org.ccnx.ccn.protocol.ContentName;

/**
 * Basic utilities provided to communicate with COPSS router
 *
 * @author Jiachen Chen
 */
public class Utility {

    /**
     * Default CCN port: 9695.
     */
    public static final int DEFAULT_CCN_PORT = 9695;
    /**
     * Default COPSS port: 9696.
     */
    public static final int DEFAULT_COPSS_PORT = 9696;

    /**
     * Split the content names according to the CD2RPTable.
     * @param CD2RPTable CD to RP mapping table.
     * @param names the names to be split.
     * @return dictionary of RPName:List<CD>.
     */
    public static HashMap<ContentName, LinkedList<ContentName>> splitContentNames(InterestTable<ContentName> CD2RPTable, Collection<ContentName> names) {
        HashMap<ContentName, LinkedList<ContentName>> ret = new HashMap<>();

        for (ContentName name : names) {
            ContentName rp = CD2RPTable.getValue(name);
            if (rp == null) {
                System.out.printf("Cannot find RP for CD: %s%n", name);
                continue;
            }
            LinkedList<ContentName> tmp = ret.get(rp);
            if (tmp == null) {
                ret.put(rp, tmp = new LinkedList<>());
            }
            tmp.add(name);
        }
        return ret;
    }
}