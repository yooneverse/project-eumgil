package com.ssafy.e102.graphhopper.ieum;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

/**
 * OSM way??정수형 `ieum:*` tag 하나를 int encoded value로 옮기는 parser다.
 *
 * <p>현재는 DB edge id를 graph-cache 안에 심어 런타임 overlay weighting에서 내부 edge를 찾는 용도로 사용한다.
 */
public class IeumIntTagParser implements TagParser {
    private final IntEncodedValue encodedValue;
    private final String tagName;
    private final int fallback;

    public IeumIntTagParser(IntEncodedValue encodedValue, String tagName, int fallback) {
        this.encodedValue = encodedValue;
        this.tagName = tagName;
        this.fallback = fallback;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        encodedValue.setInt(false, edgeId, edgeIntAccess, parseValue(way.getTag(tagName)));
    }

    private int parseValue(String value) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
