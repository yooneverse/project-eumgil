package com.ssafy.e102.domain.report.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class HazardReportRerouteCustomModelFactory {

	private final ObjectMapper objectMapper;

	public HazardReportRerouteCustomModelFactory(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public JsonNode create(Polygon avoidArea) {
		ObjectNode customModel = objectMapper.createObjectNode();
		ObjectNode areas = customModel.putObject("areas");
		ObjectNode hazardArea = areas.putObject("hazard_area");
		hazardArea.put("type", "Feature");
		ObjectNode geometry = hazardArea.putObject("geometry");
		geometry.put("type", "Polygon");
		ArrayNode coordinates = geometry.putArray("coordinates");
		ArrayNode ring = coordinates.addArray();
		for (Coordinate coordinate : avoidArea.getCoordinates()) {
			ArrayNode point = ring.addArray();
			point.add(coordinate.x);
			point.add(coordinate.y);
		}

		ArrayNode priority = customModel.putArray("priority");
		ObjectNode avoidRule = priority.addObject();
		avoidRule.put("if", "in_hazard_area");
		avoidRule.put("multiply_by", "0");
		return customModel;
	}
}
