package com.ssafy.e102.graphhopper.ieum;

import com.graphhopper.routing.ev.DefaultImportRegistry;
import com.graphhopper.routing.ev.ImportUnit;

/**
 * GraphHopper가 모르는 IEUM encoded value 이름을 알아듣게 해 주는 등록소다.
 *
 * <p>GraphHopper는 import를 시작할 때 config/custom model에 적힌 이름마다 "이 값을 어떻게 저장하고
 * 어떤 tag에서 읽을지"를 registry에 물어본다. IEUM 이름이면 {@link IeumEncodedValues}에서 import unit을
 * 만들고, 기본 GraphHopper 이름이면 원래 registry로 넘긴다.
 */
public class IeumImportRegistry extends DefaultImportRegistry {

    @Override
    public ImportUnit createImportUnit(String name) {
        ImportUnit ieumUnit = IeumEncodedValues.createImportUnit(name);
        if (ieumUnit != null) {
            return ieumUnit;
        }
        return super.createImportUnit(name);
    }
}
