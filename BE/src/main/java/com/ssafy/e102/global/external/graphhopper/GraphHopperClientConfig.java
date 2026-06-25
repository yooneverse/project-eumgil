package com.ssafy.e102.global.external.graphhopper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * GraphHopper 외부 client 설정을 Spring configuration에 등록한다.
 *
 * <p>application 설정값은 {@link GraphHopperProperties}로 바인딩되고, 실제 HTTP 호출은
 * {@link GraphHopperRouteClient}가 맡는다.
 */
@Configuration
@EnableConfigurationProperties(GraphHopperProperties.class)
public class GraphHopperClientConfig {}
