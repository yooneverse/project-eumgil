package com.ssafy.e102.graphhopper.ieum;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

/**
 * OSM way의 `ieum:*` 문자열 tag 하나를 enum encoded value로 옮기는 parser다.
 *
 * <p>예를 들어 exporter가 `ieum:width_state=NARROW`를 쓰면 이 parser가 `NARROW` enum으로 바꿔
 * edge flag에 저장한다. tag가 없거나 잘못된 값이면 import를 실패시키지 않고 fallback으로 저장해
 * custom model이 항상 예측 가능한 조건 판단을 하게 한다.
 */
public class IeumEnumTagParser<E extends Enum<E>> implements TagParser {
    private final EnumEncodedValue<E> encodedValue;
    private final String tagName;
    private final E fallback;

    public IeumEnumTagParser(EnumEncodedValue<E> encodedValue, String tagName, E fallback) {
        this.encodedValue = encodedValue;
        this.tagName = tagName;
        this.fallback = fallback;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        encodedValue.setEnum(false, edgeId, edgeIntAccess, parseValue(way.getTag(tagName)));
    }

    private E parseValue(String value) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            // custom model은 enum 이름을 그대로 비교하므로 별도 치환 없이 앞뒤 공백만 제거한다.
            return Enum.valueOf(fallback.getDeclaringClass(), value.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
