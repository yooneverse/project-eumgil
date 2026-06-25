package com.ssafy.e102.graphhopper.ieum;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

/**
 * OSM way의 숫자형 `ieum:*` tag 하나를 decimal encoded value로 옮기는 parser다.
 *
 * <p>예를 들어 exporter가 `ieum:avg_slope_percent=8.5`를 쓰면 이 parser가 문자열을 double로 바꿔
 * edge flag에 저장한다. 숫자가 비어 있거나 파싱할 수 없으면 fallback을 저장해 import가 중단되지 않게 한다.
 */
public class IeumDecimalTagParser implements TagParser {
    private final DecimalEncodedValue encodedValue;
    private final String tagName;
    private final double fallback;

    public IeumDecimalTagParser(DecimalEncodedValue encodedValue, String tagName, double fallback) {
        this.encodedValue = encodedValue;
        this.tagName = tagName;
        this.fallback = fallback;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        encodedValue.setDecimal(false, edgeId, edgeIntAccess, parseValue(way.getTag(tagName)));
    }

    private double parseValue(String value) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            // 숫자는 치환하지 않고 그대로 파싱한다. 잘못된 원천값은 설정된 fallback으로 통일된다.
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
