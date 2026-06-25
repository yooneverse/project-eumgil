package com.ssafy.e102.global.security.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ssafy.e102.global.security.filter.JwtAuthenticationFilter;
import com.ssafy.e102.global.security.handler.RestAccessDeniedHandler;
import com.ssafy.e102.global.security.handler.RestAuthenticationEntryPoint;
import com.ssafy.e102.global.security.jwt.JwtProperties;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

	static final String HAZARD_REPORT_REROUTE_PATTERN = "/hazard/{reportId}/reroute";

	private final RestAuthenticationEntryPoint authenticationEntryPoint;
	private final RestAccessDeniedHandler accessDeniedHandler;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CorsProperties corsProperties;

	public SecurityConfig(
		RestAuthenticationEntryPoint authenticationEntryPoint,
		RestAccessDeniedHandler accessDeniedHandler,
		JwtAuthenticationFilter jwtAuthenticationFilter,
		CorsProperties corsProperties) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.corsProperties = corsProperties;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.anonymous(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/auth/social-login", "/auth/signup", "/auth/reissue")
				.permitAll()
				.requestMatchers("/health", "/health/**")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus")
				.permitAll()
				.requestMatchers("/actuator/**")
				.denyAll()
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/logout")
				.authenticated()
				.requestMatchers("/users/**")
				.authenticated()
				.requestMatchers("/bookmarks/**")
				.authenticated()
				.requestMatchers("/favorite-routes/**")
				.authenticated()
				.requestMatchers("/places", "/places/**")
				.authenticated()
				.requestMatchers("/voice/**")
				.authenticated()
				.requestMatchers("/routes/**", "/route-ratings/**")
				.authenticated()
				.requestMatchers("/hazard-reports", "/hazard-reports/**")
				.authenticated()
				.requestMatchers("/hazard/markers", "/hazard/markers/**", HAZARD_REPORT_REROUTE_PATTERN)
				.authenticated()
				.requestMatchers("/admin", "/admin/**")
				.hasRole("ADMIN")
				.anyRequest()
				.permitAll())
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
