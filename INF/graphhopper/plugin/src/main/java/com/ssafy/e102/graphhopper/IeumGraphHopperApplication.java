package com.ssafy.e102.graphhopper;

import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.resources.RootResource;
import com.graphhopper.http.CORSFilter;
import com.graphhopper.navigation.NavigateResource;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;

/**
 * IEUM GraphHopper 서버를 실제로 실행하는 가장 바깥쪽 진입점이다.
 *
 * <p>흐름은 application -> bundle -> managed -> registry -> encoded value/tag parser 순서로 이어진다.
 * 이 클래스는 서버를 켜고 {@link IeumGraphHopperBundle}을 붙이는 역할만 맡는다. 실제 접근성 tag를
 * GraphHopper에 읽히게 만드는 일은 bundle이 등록한 managed instance와 import registry가 처리한다.
 */
public final class IeumGraphHopperApplication extends Application<GraphHopperServerConfiguration> {

    public static void main(String[] args) throws Exception {
        new IeumGraphHopperApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<GraphHopperServerConfiguration> bootstrap) {
        bootstrap.addBundle(new IeumGraphHopperBundle());
        bootstrap.addBundle(new AssetsBundle("/com/graphhopper/maps/", "/maps/", "index.html"));
        bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars", "/webjars/", null, "webjars"));
    }

    @Override
    public void run(GraphHopperServerConfiguration configuration, Environment environment) {
        environment.jersey().register(new RootResource());
        environment.jersey().register(NavigateResource.class);
        environment.servlets()
            .addFilter("cors", CORSFilter.class)
            .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
    }
}
