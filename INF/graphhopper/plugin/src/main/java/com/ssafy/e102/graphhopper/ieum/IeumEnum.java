package com.ssafy.e102.graphhopper.ieum;

/**
 * IEUM encoded value에 저장할 수 있는 표준 상태값 모음이다.
 *
 * <p>이 enum 이름은 exporter, GraphHopper custom model, BE 문서가 함께 쓰는 계약값이다.
 * 예를 들어 custom model은 `width_state == NARROW`처럼 문자열 이름으로 비교한다. 따라서 값을 바꿀 때는
 * OSM export, custom model, API/ERD 문서를 같이 확인해야 한다.
 */
public final class IeumEnum {
    private IeumEnum() {
    }

    public enum YesNoUnknown {
        YES,
        NO,
        UNKNOWN
    }

    public enum WidthState {
        ADEQUATE_150,
        ADEQUATE_120,
        NARROW,
        UNKNOWN
    }

    public enum SurfaceState {
        PAVED,
        UNPAVED,
        UNKNOWN
    }

    public enum SegmentType {
        CROSS_WALK,
        SIDE_LINE
    }
}
