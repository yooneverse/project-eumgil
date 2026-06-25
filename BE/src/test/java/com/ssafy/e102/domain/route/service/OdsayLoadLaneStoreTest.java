package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.UnexpectedRollbackException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.entity.OdsayLoadLane;
import com.ssafy.e102.domain.route.repository.OdsayLoadLaneRepository;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.external.odsay.OdsayLaneGeometry;

class OdsayLoadLaneStoreTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private OdsayLoadLaneRepository odsayLoadLaneRepository;

	private OdsayLoadLaneStore store;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		store = new OdsayLoadLaneStore(odsayLoadLaneRepository, objectMapper);
	}

	@Test
	@DisplayName("order is malformed unless it is an integral 0-based sequence")
	void ignoresMalformedOrderValues() throws Exception {
		List<String> mapObjs = List.of("map-string", "map-boolean", "map-decimal", "map-negative", "map-out-of-range");
		when(odsayLoadLaneRepository.findAllByMapObjIn(mapObjs)).thenReturn(List.of(
			row("map-string", "\"abc\""),
			row("map-boolean", "true"),
			row("map-decimal", "1.7"),
			row("map-negative", "-1"),
			row("map-out-of-range", "1")));

		Map<String, List<OdsayLaneGeometry>> result = store.findValidByMapObjIn(mapObjs);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("LINESTRING is malformed unless it has at least two lng lat numeric coordinates")
	void ignoresMalformedLineStrings() throws Exception {
		List<String> mapObjs = List.of("map-empty", "map-text", "map-one-point", "map-non-number", "map-missing-lat");
		when(odsayLoadLaneRepository.findAllByMapObjIn(mapObjs)).thenReturn(List.of(
			rowWithGeometry("map-empty", "LINESTRING()"),
			rowWithGeometry("map-text", "LINESTRING(foo)"),
			rowWithGeometry("map-one-point", "LINESTRING(129.0 35.0)"),
			rowWithGeometry("map-non-number", "LINESTRING(129.0 35.0, x 35.1)"),
			rowWithGeometry("map-missing-lat", "LINESTRING(129.0 35.0, 129.1)")));

		Map<String, List<OdsayLaneGeometry>> result = store.findValidByMapObjIn(mapObjs);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("유효한 loadLane JSON은 mapObj 기준 lane geometry로 복원한다")
	void restoresValidLaneGeometries() throws Exception {
		OdsayLoadLane row = OdsayLoadLane.create("map-1", json("""
			[
			  {"order": 1, "transportMode": "SUBWAY", "geometry": "LINESTRING(129.2 35.2, 129.3 35.3)"},
			  {"order": 0, "transportMode": "BUS", "geometry": "LINESTRING(129.0 35.0, 129.1 35.1)"}
			]
			"""));
		when(odsayLoadLaneRepository.findAllByMapObjIn(List.of("map-1"))).thenReturn(List.of(row));

		Map<String, List<OdsayLaneGeometry>> result = store.findValidByMapObjIn(List.of("map-1"));

		assertThat(result).containsOnlyKeys("map-1");
		assertThat(result.get("map-1"))
			.extracting(OdsayLaneGeometry::type)
			.containsExactly(TransportMode.BUS, TransportMode.SUBWAY);
	}

	@Test
	@DisplayName("malformed loadLane row는 정상 cache hit로 반환하지 않는다")
	void ignoresMalformedLaneGeometries() throws Exception {
		OdsayLoadLane row = OdsayLoadLane.create("map-1", json("""
			[
			  {"order": 0, "transportMode": "WALK", "geometry": "LINESTRING(129.0 35.0, 129.1 35.1)"}
			]
			"""));
		when(odsayLoadLaneRepository.findAllByMapObjIn(List.of("map-1"))).thenReturn(List.of(row));

		Map<String, List<OdsayLaneGeometry>> result = store.findValidByMapObjIn(List.of("map-1"));

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("신규 loadLane geometry는 ERD JSON 계약으로 저장한다")
	void savesNewLaneGeometries() throws Exception {
		List<OdsayLaneGeometry> laneGeometries = List.of(
			new OdsayLaneGeometry(TransportMode.BUS, "LINESTRING(129.0 35.0, 129.1 35.1)"));

		store.saveIfAbsentOrRepairMalformed("map-1", laneGeometries);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(odsayLoadLaneRepository).upsertLaneGeometries(eq("map-1"), captor.capture());
		JsonNode laneGeometriesJson = objectMapper.readTree(captor.getValue());
		assertThat(laneGeometriesJson.get(0).get("order").asInt()).isZero();
		assertThat(laneGeometriesJson.get(0).get("transportMode").asText()).isEqualTo("BUS");
		assertThat(laneGeometriesJson.get(0).get("geometry").asText())
			.isEqualTo("LINESTRING(129.0 35.0, 129.1 35.1)");
	}

	@Test
	@DisplayName("기존 malformed row는 재조회 성공 결과로 복구 저장한다")
	void repairsExistingMalformedRow() throws Exception {
		store.saveIfAbsentOrRepairMalformed("map-1", List.of(
			new OdsayLaneGeometry(TransportMode.SUBWAY, "LINESTRING(129.0 35.0, 129.1 35.1)")));

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(odsayLoadLaneRepository).upsertLaneGeometries(eq("map-1"), captor.capture());
		JsonNode laneGeometriesJson = objectMapper.readTree(captor.getValue());
		assertThat(laneGeometriesJson.isArray()).isTrue();
		assertThat(laneGeometriesJson.get(0).get("transportMode").asText()).isEqualTo("SUBWAY");
	}

	@Test
	@DisplayName("동일 mapObj cold miss insert race는 검색 흐름으로 전파하지 않는다")
	void suppressesConcurrentColdMissInsertRace() {
		List<OdsayLaneGeometry> laneGeometries = List.of(
			new OdsayLaneGeometry(TransportMode.BUS, "LINESTRING(129.0 35.0, 129.1 35.1)"));
		doThrow(new UnexpectedRollbackException("upsert race"))
			.when(odsayLoadLaneRepository)
			.upsertLaneGeometries(eq("map-1"), any());

		assertThatCode(() -> store.saveIfAbsentOrRepairMalformed("map-1", laneGeometries))
			.doesNotThrowAnyException();
	}

	private JsonNode json(String value) throws Exception {
		return objectMapper.readTree(value);
	}

	private OdsayLoadLane row(String mapObj, String orderValue) throws Exception {
		return OdsayLoadLane.create(mapObj, json("""
			[
			  {"order": %s, "transportMode": "BUS", "geometry": "LINESTRING(129.0 35.0, 129.1 35.1)"}
			]
			""".formatted(orderValue)));
	}

	private OdsayLoadLane rowWithGeometry(String mapObj, String geometry) throws Exception {
		return OdsayLoadLane.create(mapObj, json("""
			[
			  {"order": 0, "transportMode": "BUS", "geometry": "%s"}
			]
			""".formatted(geometry)));
	}
}
