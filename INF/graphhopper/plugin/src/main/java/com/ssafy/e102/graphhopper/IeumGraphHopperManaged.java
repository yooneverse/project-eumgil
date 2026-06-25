package com.ssafy.e102.graphhopper;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.ssafy.e102.graphhopper.ieum.IeumImportRegistry;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IEUM 설정이 들어간 GraphHopper instance를 만들고 시작/종료하는 lifecycle 담당자다.
 *
 * <p>핵심은 `setImportRegistry(new IeumImportRegistry())`다. 이 한 줄이 GraphHopper import 과정에
 * IEUM registry를 끼워 넣는다. 그래서 config/custom model에 적힌 `walk_access`, `width_state` 같은
 * 이름을 registry가 encoded value와 tag parser로 바꿔 줄 수 있다.
 */
public class IeumGraphHopperManaged implements Managed {
    private static final Logger log = LoggerFactory.getLogger(IeumGraphHopperManaged.class);

    private final GraphHopper graphHopper;
    private final RoutingSegmentOverrideStore routingSegmentOverrideStore;

    public IeumGraphHopperManaged(GraphHopperConfig configuration) {
        this.routingSegmentOverrideStore = new RoutingSegmentOverrideStore();
        this.graphHopper = new IeumGraphHopper(routingSegmentOverrideStore)
            .setImportRegistry(new IeumImportRegistry())
            .init(configuration);
    }

    @Override
    public void start() {
        // 첫 실행이면 OSM/PBF를 읽어 graph-cache를 만들고, 이미 cache가 있으면 바로 로드한다.
        // 이 시점에 registry와 tag parser가 동작해 `ieum:*` tag가 edge flag에 저장된다.
        graphHopper.importOrLoad();
        routingSegmentOverrideStore.reload();
        log.info(
            "loaded ieum graph at:{}, data_reader_file:{}, encoded values:{}, {} bytes for edge flags, {}",
            graphHopper.getGraphHopperLocation(),
            graphHopper.getOSMFile(),
            graphHopper.getEncodingManager().toEncodedValuesAsString(),
            graphHopper.getEncodingManager().getBytesForFlags(),
            graphHopper.getBaseGraph().toDetailsString()
        );
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    public RoutingSegmentOverrideStore getRoutingSegmentOverrideStore() {
        return routingSegmentOverrideStore;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }
}
