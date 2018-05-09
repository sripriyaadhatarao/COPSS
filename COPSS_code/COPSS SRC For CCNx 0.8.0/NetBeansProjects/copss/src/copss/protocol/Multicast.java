package copss.protocol;

import copss.util.Utility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.ccnx.ccn.impl.InterestTable;
import org.ccnx.ccn.impl.encoding.GenericXMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.impl.encoding.XMLEncoder;
import org.ccnx.ccn.impl.support.DataUtils;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.io.content.ContentDecodingException;
import org.ccnx.ccn.io.content.ContentEncodingException;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;

/**
 * Multicast (Publish) packet.
 * 
 * @author Jiachen Chen
 */
public class Multicast extends GenericXMLEncodable {

    /**
     * Encapsulates the multicast packet using prefix /RPName/multicast/
     */
    public static final String MULTICAST_ENCAPSULATE_NAME = "multicast";
    
    private LinkedList<ContentName> _contentNames;
    private byte[] _content;

    public Multicast() {
    }

    public Multicast(LinkedList<ContentName> contentNames, byte[] content) {
        _contentNames = contentNames;
        _content = content;
    }

    /**
     * Dangerous: original list returned. Do NOT change content!
     *
     * @return original ContentNameAdd list
     */
    public LinkedList<ContentName> contentNames() {
        return _contentNames;
    }

    /**
     * Dangerous: original byte array returned. Do NOT change content!
     *
     * @return original content
     */
    public byte[] content() {
        return _content;
    }

    @Override
    public void decode(XMLDecoder decoder) throws ContentDecodingException {
        decoder.readStartElement(getElementLabel());


        int count;
        _contentNames = new LinkedList<>();

        if (decoder.peekStartElement(COPSSProtocolDTags.ContentNameAddCount)) {
            count = decoder.readIntegerElement(COPSSProtocolDTags.ContentNameAddCount);

            for (int i = 0; i < count; i++) {
                ContentName name = new ContentName();
                name.decode(decoder);
                _contentNames.addLast(name);
            }
        }

        _content = decoder.readBinaryElement(COPSSProtocolDTags.Content);

        try {
            decoder.readEndElement();
        } catch (ContentDecodingException e) {
            Log.info(Log.FAC_ENCODING, "Catching exception reading Interest end element, and moving on. Waiting for schema updates...");
        }

    }

    @Override
    public void encode(XMLEncoder encoder) throws ContentEncodingException {
        if (!validate()) {
            throw new ContentEncodingException("Cannot encode " + this.getClass().getName() + ": field values missing.");
        }
        encoder.writeStartElement(getElementLabel());

        encoder.writeElement(COPSSProtocolDTags.ContentNameAddCount, _contentNames.size());

        for (ContentName name : _contentNames) {
            name.encode(encoder);
        }

        encoder.writeElement(COPSSProtocolDTags.Content, _content);

        encoder.writeEndElement();
    }

    @Override
    public long getElementLabel() {
        return COPSSProtocolDTags.Multicast;
    }

    @Override
    public boolean validate() {
        return _contentNames != null && _contentNames.size() > 0 && _content != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Multicast:\n\tCDs: ");
        sb.append(_contentNames);
        sb.append("\n\tContent=");
        sb.append(DataUtils.printHexBytes(_content));
        return sb.toString();
    }

    /**
     * Encapsulates the multicast packet into Interest packet(s) according to the CD2RPMappingTable
     * 
     * @param CD2RPMappingTable CD to RP mapping.
     * @return encapsulated Interest packets.
     * @throws ContentEncodingException 
     */
    public LinkedList<Interest> encapsulate(InterestTable<ContentName> CD2RPMappingTable) throws ContentEncodingException {
        LinkedList<Interest> ret = new LinkedList<>();
        HashMap<ContentName, LinkedList<ContentName>> tmp;
        tmp = Utility.splitContentNames(CD2RPMappingTable, _contentNames);

        for (Map.Entry<ContentName, LinkedList<ContentName>> entry : tmp.entrySet()) {
            Multicast multicast = new Multicast(entry.getValue(), _content);
            String content = DataUtils.base64Encode(multicast.encode(), Integer.MAX_VALUE).replaceAll("\n|\r", "");
            ret.add(new Interest(
                    new ContentName(entry.getKey(), MULTICAST_ENCAPSULATE_NAME, content)
//                    ContentName.fromNative(entry.getKey(), new String[]{MULTICAST_ENCAPSULATE_NAME, content})
                    ));
        }
        return ret;
    }

    /**
     * Decapsulates the multicast from the Interst packet
     * 
     * @param interest the Interest packet
     * @return if the decapsulation is successful
     * @throws UnsupportedEncodingException
     * @throws IOException 
     */
    public boolean decapsulate(Interest interest) throws UnsupportedEncodingException, IOException {
        ContentName name = interest.name();
        if (!name.stringComponent(1).equals(MULTICAST_ENCAPSULATE_NAME)) {
            return false;
        }
        String base64String = URLDecoder.decode(name.stringComponent(2), "UTF-8");
//        System.out.println(base64String);
        byte[] buf = DataUtils.base64Decode(DataUtils.getBytesFromUTF8String(base64String));
        decode(buf);
        return true;
    }
}