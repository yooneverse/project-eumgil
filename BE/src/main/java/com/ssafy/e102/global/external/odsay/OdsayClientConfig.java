package com.ssafy.e102.global.external.odsay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OdsayProperties.class)
public class OdsayClientConfig {}
