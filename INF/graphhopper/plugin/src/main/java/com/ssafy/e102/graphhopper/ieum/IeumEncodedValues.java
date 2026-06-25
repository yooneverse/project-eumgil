package com.ssafy.e102.graphhopper.ieum;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.ImportUnit;
import com.graphhopper.routing.ev.IntEncodedValueImpl;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.SegmentType;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.SurfaceState;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.WidthState;
import com.ssafy.e102.graphhopper.ieum.IeumEnum.YesNoUnknown;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * IEUM 접근성 값을 GraphHopper edge flag에 저장하기 위한 목록표다.
 *
 * <p>registry가 `walk_access` 같은 이름을 물어보면 이 클래스가 "enum으로 저장할지, 숫자로 저장할지"와
 * "어떤 `ieum:*` OSM tag에서 값을 읽을지"를 한 번에 돌려준다. 그 결과 exporter가 만든 OSM tag가
 * tag parser를 거쳐 encoded value로 저장되고, custom model은 그 값을 조건식에서 사용할 수 있다.
 */
public final class IeumEncodedValues {
    private static final String IEUM_TAG_PREFIX = "ieum:";

    public static final String WALK_ACCESS = "walk_access";
    public static final String DB_EDGE_ID = "db_edge_id";
    public static final String AVG_SLOPE_PERCENT = "avg_slope_percent";
    public static final String WIDTH_METER = "width_meter";
    public static final String BRAILLE_BLOCK_STATE = "braille_block_state";
    public static final String AUDIO_SIGNAL_STATE = "audio_signal_state";
    public static final String WIDTH_STATE = "width_state";
    public static final String SURFACE_STATE = "surface_state";
    public static final String STAIRS_STATE = "stairs_state";
    public static final String SIGNAL_STATE = "signal_state";
    public static final String SEGMENT_TYPE = "segment_type";

    // 이 이름이 계약의 중심이다. config/custom model의 encoded value 이름,
    // exporter가 쓰는 `ieum:*` tag 이름, 아래 parser 연결이 모두 같은 이름을 공유해야 한다.
    private static final Map<String, Function<PMap, ?>> ENCODED_VALUE_FACTORIES = Map.ofEntries(
        Map.entry(WALK_ACCESS, ignored -> new EnumEncodedValue<>(WALK_ACCESS, YesNoUnknown.class)),
        Map.entry(DB_EDGE_ID, ignored -> new IntEncodedValueImpl(DB_EDGE_ID, 31, false)),
        Map.entry(AVG_SLOPE_PERCENT, ignored -> new DecimalEncodedValueImpl(AVG_SLOPE_PERCENT, 12, 0.1, false)),
        Map.entry(WIDTH_METER, ignored -> new DecimalEncodedValueImpl(WIDTH_METER, 10, 0.1, false)),
        Map.entry(BRAILLE_BLOCK_STATE, ignored -> new EnumEncodedValue<>(BRAILLE_BLOCK_STATE, YesNoUnknown.class)),
        Map.entry(AUDIO_SIGNAL_STATE, ignored -> new EnumEncodedValue<>(AUDIO_SIGNAL_STATE, YesNoUnknown.class)),
        Map.entry(WIDTH_STATE, ignored -> new EnumEncodedValue<>(WIDTH_STATE, WidthState.class)),
        Map.entry(SURFACE_STATE, ignored -> new EnumEncodedValue<>(SURFACE_STATE, SurfaceState.class)),
        Map.entry(STAIRS_STATE, ignored -> new EnumEncodedValue<>(STAIRS_STATE, YesNoUnknown.class)),
        Map.entry(SIGNAL_STATE, ignored -> new EnumEncodedValue<>(SIGNAL_STATE, YesNoUnknown.class)),
        Map.entry(SEGMENT_TYPE, ignored -> new EnumEncodedValue<>(SEGMENT_TYPE, SegmentType.class))
    );

    private IeumEncodedValues() {
    }

    public static ImportUnit createImportUnit(String name) {
        Function<PMap, ?> encodedValueFactory = ENCODED_VALUE_FACTORIES.get(name);
        if (encodedValueFactory == null) {
            return null;
        }
        return ImportUnit.create(
            name,
            properties -> (com.graphhopper.routing.ev.EncodedValue) encodedValueFactory.apply(properties),
            createTagParser(name)
        );
    }

    private static BiFunction<EncodedValueLookup, PMap, TagParser> createTagParser(String name) {
        return (lookup, properties) -> {
            // 폭/경사처럼 계산에 쓰는 값은 decimal EV로, 상태값은 enum EV로 읽는다.
            if (DB_EDGE_ID.equals(name)) {
                return new IeumIntTagParser(lookup.getIntEncodedValue(name), tagName(name), 0);
            }
            if (AVG_SLOPE_PERCENT.equals(name)) {
                return new IeumDecimalTagParser(lookup.getDecimalEncodedValue(name), tagName(name), 0.0);
            }
            if (WIDTH_METER.equals(name)) {
                return new IeumDecimalTagParser(lookup.getDecimalEncodedValue(name), tagName(name), 0.0);
            }
            return createEnumTagParser(name, lookup);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TagParser createEnumTagParser(String name, EncodedValueLookup lookup) {
        if (WIDTH_STATE.equals(name)) {
            return new IeumEnumTagParser(
                lookup.getEnumEncodedValue(name, WidthState.class),
                tagName(name),
                WidthState.UNKNOWN
            );
        }
        if (SURFACE_STATE.equals(name)) {
            return new IeumEnumTagParser(
                lookup.getEnumEncodedValue(name, SurfaceState.class),
                tagName(name),
                SurfaceState.UNKNOWN
            );
        }
        if (SEGMENT_TYPE.equals(name)) {
            return new IeumEnumTagParser(
                lookup.getEnumEncodedValue(name, SegmentType.class),
                tagName(name),
                SegmentType.SIDE_LINE
            );
        }
        return new IeumEnumTagParser(
            lookup.getEnumEncodedValue(name, YesNoUnknown.class),
            tagName(name),
            YesNoUnknown.UNKNOWN
        );
    }

    private static String tagName(String name) {
        return IEUM_TAG_PREFIX + name;
    }
}
