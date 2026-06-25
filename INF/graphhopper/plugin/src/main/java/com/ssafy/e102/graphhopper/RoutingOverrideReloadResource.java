package com.ssafy.e102.graphhopper;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/ieum/admin/overrides")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoutingOverrideReloadResource {

	private final RoutingSegmentOverrideStore routingSegmentOverrideStore;

	@Inject
	public RoutingOverrideReloadResource(RoutingSegmentOverrideStore routingSegmentOverrideStore) {
		this.routingSegmentOverrideStore = routingSegmentOverrideStore;
	}

	@POST
	@Path("/reload")
	public Map<String, Object> reload() {
		routingSegmentOverrideStore.reloadStrict();
		return Map.of(
			"status", "RELOADED",
			"overrideCount", routingSegmentOverrideStore.size());
	}
}
