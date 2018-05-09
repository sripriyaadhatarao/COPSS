package copss.protocol;

import copss.util.Utility;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
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
 * Control packet.
 * 
 * @author Jiachen Chen
 */
public class Control extends GenericXMLEncodable {

    /**
     * Encapsulates the control packet using prefix /RPName/control/
     */
    private static final String CONTROL_ENCAPSULATE_NAME = "control";

    /**
     * Type of control packets: FIB change and ST change.
     */
    public enum ControlType {

        FIBChange(COPSSProtocolDTags.FIBChange),
        STChange(COPSSProtocolDTags.STChange);
        private final int _value;

        ControlType(int value) {
            _value = value;
        }

        public int value() {
            return _value;
        }

        public static ControlType fromInt(int value) {
            switch (value) {
                case COPSSProtocolDTags.FIBChange:
                    return FIBChange;
                case COPSSProtocolDTags.STChange:
                    return STChange;
                default:
                    return null;
            }
        }
    }
    private ControlType _type;
    private LinkedList<ContentName> _contentNameAdd, _contentNameRemove;
    private Integer _version, _ttl;

    public Control() {
    }

    public Control(ControlType type, LinkedList<ContentName> contentNameAdd, LinkedList<ContentName> contentNameRemove, int version, int ttl) {
        _type = type;
        _contentNameAdd = contentNameAdd;
        _contentNameRemove = contentNameRemove;
        _version = version;
        _ttl = ttl;
    }

    @Override
    public void decode(XMLDecoder decoder) throws ContentDecodingException {
        decoder.readStartElement(getElementLabel());

        _type = ControlType.fromInt(decoder.readIntegerElement(COPSSProtocolDTags.ControlType));
        if (_type == null) {
            throw new ContentDecodingException("Cannot decode " + this.getClass().getName() + ": type mismatch.");
        }

        int count;
        _contentNameAdd = new LinkedList<>();
        _contentNameRemove = new LinkedList<>();
        _version = null;

        if (decoder.peekStartElement(COPSSProtocolDTags.ContentNameAddCount)) {
            count = decoder.readIntegerElement(COPSSProtocolDTags.ContentNameAddCount);

            for (int i = 0; i < count; i++) {
                ContentName name = new ContentName();
                name.decode(decoder);
                _contentNameAdd.add(name);
            }
        }

        if (decoder.peekStartElement(COPSSProtocolDTags.ContentNameRemoveCount)) {
            count = decoder.readIntegerElement(COPSSProtocolDTags.ContentNameRemoveCount);

            for (int i = 0; i < count; i++) {
                ContentName name = new ContentName();
                name.decode(decoder);
                _contentNameRemove.add(name);
            }
        }

        _version = decoder.readIntegerElement(COPSSProtocolDTags.Version);
        _ttl = decoder.readIntegerElement(COPSSProtocolDTags.TTL);

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

        encoder.writeElement(COPSSProtocolDTags.ControlType, _type.value());

        if (_contentNameAdd.size() > 0) {
            encoder.writeElement(COPSSProtocolDTags.ContentNameAddCount, _contentNameAdd.size());
            for (ContentName name : _contentNameAdd) {
                name.encode(encoder);
            }
        }

        if (_contentNameRemove.size() > 0) {
            encoder.writeElement(COPSSProtocolDTags.ContentNameRemoveCount, _contentNameRemove.size());
            for (ContentName name : _contentNameRemove) {
                name.encode(encoder);
            }
        }

        encoder.writeElement(COPSSProtocolDTags.Version, _version);
        encoder.writeElement(COPSSProtocolDTags.TTL, _ttl);

        encoder.writeEndElement();
    }

    @Override
    public long getElementLabel() {
        return COPSSProtocolDTags.Control;
    }

    @Override
    public boolean validate() {
        return _contentNameAdd != null && _contentNameRemove != null && _version != null && _ttl != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(_type);
        sb.append(": V=");
        sb.append(_version);
        sb.append(", ttl=");
        sb.append(_ttl);
        sb.append('\n');
        sb.append("\tAdd: ");
        sb.append(_contentNameAdd);
        sb.append('\n');
        sb.append("\tDel: ");
        sb.append(_contentNameRemove);
        return sb.toString();

    }

    /**
     * Dangerous: original list returned. Do NOT change content!
     *
     * @return original ContentNameAdd list
     */
    public LinkedList<ContentName> contentNameAdd() {
        return _contentNameAdd;
    }

    /**
     * Dangerous: original list returned. Do NOT change content!
     *
     * @return original ContentNameRemove list
     */
    public LinkedList<ContentName> contentNameRemove() {
        return _contentNameRemove;
    }

    public ControlType type() {
        return _type;
    }

    public int version() {
        return _version;
    }

    public int ttl() {
        return _ttl;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Control)) {
            return false;
        }
        Control c = (Control) o;
        if (_type != c._type && _version != c._version) {
            return false;
        }
        if (_contentNameAdd == null) {
            if (c._contentNameAdd != null) {
                return false;
            }
        } else {
            if (!_contentNameAdd.equals(c._contentNameAdd)) {
                return false;
            }
        }
        if (_contentNameRemove == null) {
            if (c._contentNameRemove != null) {
                return false;
            }
        } else {
            if (!_contentNameRemove.equals(c._contentNameRemove)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + this._type.hashCode();
        if (this._contentNameAdd != null) {
            for (ContentName cn : this._contentNameAdd) {
                hash = 53 * hash + cn.hashCode();
            }
        }
        if (this._contentNameRemove != null) {
            for (ContentName cn : this._contentNameRemove) {
                hash = 53 * hash + cn.hashCode();
            }
        }
        hash = 53 * hash + (this._version != null ? this._version.hashCode() : 0);
        return hash;
    }

    /**
     * Encapsulates a control packet into Interest packet(s) according to the CD2RPMappingTable.
     * 
     * @param CD2RPMappingTable The CD to RP mapping.
     * @return the encapsulated interest packets
     * @throws ContentEncodingException 
     */
    public LinkedList<Interest> encapsulate(InterestTable<ContentName> CD2RPMappingTable) throws ContentEncodingException {

        HashMap<ContentName, Control> ret = new HashMap<>();

        HashMap<ContentName, LinkedList<ContentName>> tmp;

        tmp = Utility.splitContentNames(CD2RPMappingTable, _contentNameAdd);

        for (Entry<ContentName, LinkedList<ContentName>> entry : tmp.entrySet()) {
            ret.put(entry.getKey(), new Control(_type, entry.getValue(), new LinkedList<ContentName>(), _version, _ttl));
        }

        tmp = Utility.splitContentNames(CD2RPMappingTable, _contentNameRemove);
        for (Entry<ContentName, LinkedList<ContentName>> entry : tmp.entrySet()) {
            Control c = ret.get(entry.getKey());
            if (c == null) {
                ret.put(entry.getKey(), new Control(_type, new LinkedList<ContentName>(), entry.getValue(), _version, _ttl));
            } else {
                c._contentNameRemove = entry.getValue();
            }
        }

        LinkedList<Interest> ret2 = new LinkedList<>();

        for (Entry<ContentName, Control> entry : ret.entrySet()) {
            String content = DataUtils.base64Encode(entry.getValue().encode(), Integer.MAX_VALUE).replaceAll("\n|\r", "");
            ret2.add(new Interest(
                    new ContentName(entry.getKey(), CONTROL_ENCAPSULATE_NAME, content)
//                    ContentName.fromNative(entry.getKey(), new String[]{CONTROL_ENCAPSULATE_NAME, content})
                    ));

        }
        return ret2;
    }

    /**
     * Decapsulates a control packet from an Interst packet
     * 
     * @param interest the interest packet
     * @return if the decapsulation is successful
     * @throws ContentDecodingException
     * @throws IOException 
     */
    public boolean decapsulate(Interest interest) throws ContentDecodingException, IOException {
        ContentName name = interest.name();

        if (name.count() <= 1 || !name.stringComponent(1).equals(CONTROL_ENCAPSULATE_NAME)) {
            return false;
        }
        String base64String = URLDecoder.decode(name.stringComponent(2), "UTF-8");
//        System.out.println(base64String);
        byte[] buf = DataUtils.base64Decode(DataUtils.getBytesFromUTF8String(base64String));
        decode(buf);
        return true;
    }
}