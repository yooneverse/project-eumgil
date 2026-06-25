package com.ssafy.e102.domain.admin.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.type.SegmentType;

class AdminRoadNetworkEditApplyRequestTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("보행 네트워크 편집 요청은 FE Point geometry payload를 역직렬화한다")
	void deserializesPointNodeRefGeometryFromFrontendPayload() throws Exception {
		String json = """
			{
			  "gu": "강서구",
			  "dong": "명지동",
			  "edits": [
			    {
			      "action": "add_segment",
			      "segmentType": "SIDE_LINE",
			      "geom": {
			        "type": "LineString",
			        "coordinates": [
			          [128.9201188, 35.12493112],
			          [128.9201238, 35.12493112]
			        ]
			      },
			      "fromNode": {
			        "mode": "existing",
			        "vertexId": 950,
			        "geom": {
			          "type": "Point",
			          "coordinates": [128.9201188, 35.12493112]
			        },
			        "snapDistanceMeter": 0.0
			      },
			      "toNode": {
			        "mode": "existing",
			        "vertexId": 951,
			        "geom": {
			          "type": "Point",
			          "coordinates": [128.9201188, 35.12493112]
			        },
			        "snapDistanceMeter": 0.0
			      }
			    }
			  ]
			}
			""";

		AdminRoadNetworkEditApplyRequest request = objectMapper.readValue(json, AdminRoadNetworkEditApplyRequest.class);

		assertThat(request.gu()).isEqualTo("강서구");
		assertThat(request.dong()).isEqualTo("명지동");
		assertThat(request.edits()).hasSize(1);
		AdminRoadNetworkEditApplyRequest.Edit edit = request.edits().get(0);
		assertThat(edit.action()).isEqualTo("add_segment");
		assertThat(edit.segmentType()).isEqualTo(SegmentType.SIDE_LINE);
		assertThat(edit.geom().coordinates()).containsExactly(
			java.util.List.of(128.9201188, 35.12493112),
			java.util.List.of(128.9201238, 35.12493112));
		assertThat(edit.fromNode().geom().type()).isEqualTo("Point");
		assertThat(edit.fromNode().geom().coordinates()).containsExactly(128.9201188, 35.12493112);
		assertThat(edit.fromNode().snapDistanceMeter()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(edit.toNode().geom().coordinates()).containsExactly(128.9201188, 35.12493112);
	}
}
