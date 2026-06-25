#!/usr/bin/env python3
"""PostGIS-to-OSM GraphHopper exporter 단위 테스트다.

이 테스트는 PostGIS나 GraphHopper 없이 exporter 계약을 실행 가능하게 유지한다.
OSM tag 출력, validation, enum 계약, `segment_features` 기반 분할/상태 반영을
검증한다.
"""
import importlib.util
import json
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[2]
EXPORT_SCRIPT = ROOT_DIR / "scripts" / "graphhopper" / "export_postgis_to_osm.py"
CUSTOM_MODEL_DIR = ROOT_DIR / "INF" / "graphhopper" / "custom_models"


def load_export_module():
    spec = importlib.util.spec_from_file_location("export_postgis_to_osm", EXPORT_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class GraphhopperExportTest(unittest.TestCase):
    """export row, validation report, feature 분할 회귀 테스트다."""

    def sample_nodes(self):
        return [
            {"vertex_id": 10, "lon": 128.1, "lat": 35.1},
            {"vertex_id": 20, "lon": 128.2, "lat": 35.2},
            {"vertex_id": 30, "lon": 128.3, "lat": 35.3},
        ]

    def sample_segments(self):
        return [
            {
                "edge_id": 1,
                "from_node_id": 10,
                "to_node_id": 20,
                "geom_wkt": "LINESTRING(128.1 35.1, 128.15 35.15, 128.2 35.2)",
                "walk_access": "NO",
                "avg_slope_percent": None,
                "width_meter": "",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "UNKNOWN",
                "surface_state": "UNKNOWN",
                "stairs_state": "UNKNOWN",
                "signal_state": "UNKNOWN",
                "segment_type": "SIDE_LINE",
            },
            {
                "edge_id": 2,
                "from_node_id": 20,
                "to_node_id": 30,
                "geom_wkt": "LINESTRING(128.2 35.2, 128.3 35.3)",
                "walk_access": "YES",
                "avg_slope_percent": "1.2",
                "width_meter": "3.0",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "ADEQUATE_120",
                "surface_state": "PAVED",
                "stairs_state": "NO",
                "signal_state": "YES",
                "segment_type": "CROSS_WALK",
            },
        ]

    def test_write_osm_exports_ieum_tags_for_all_segments(self):
        module = load_export_module()
        nodes = self.sample_nodes()
        segments = self.sample_segments()

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "road-network.osm"
            module.write_osm(nodes, segments, output)
            root = ET.parse(output).getroot()

        ways = root.findall("way")
        self.assertEqual(len(ways), 2)
        first_way_tags = {tag.attrib["k"]: tag.attrib["v"] for tag in ways[0].findall("tag")}
        second_way_tags = {tag.attrib["k"]: tag.attrib["v"] for tag in ways[1].findall("tag")}

        self.assertEqual(first_way_tags["highway"], "footway")
        self.assertEqual(first_way_tags["foot"], "yes")
        self.assertEqual(first_way_tags["oneway"], "no")
        self.assertEqual(first_way_tags["ieum:walk_access"], "NO")
        self.assertEqual(first_way_tags["ieum:avg_slope_percent"], "0.0")
        self.assertEqual(first_way_tags["ieum:width_meter"], "0.0")
        self.assertEqual(first_way_tags["ieum:segment_type"], "SIDE_LINE")
        self.assertEqual(second_way_tags["ieum:segment_type"], "CROSS_WALK")
        self.assertNotIn("ieum:slope_state", second_way_tags)
        self.assertEqual(second_way_tags["ieum:width_state"], "ADEQUATE_120")
        self.assertEqual(second_way_tags["ieum:surface_state"], "PAVED")
        self.assertNotIn("e102:edge_id", first_way_tags)
        self.assertNotIn("ieum:crossing_state", first_way_tags)

    def test_default_export_sql_uses_snake_case_columns_when_available(self):
        module = load_export_module()

        nodes_sql = module.build_road_nodes_sql({"vertex_id", "point"})
        segments_sql = module.build_road_segments_sql({
            "edge_id",
            "from_node_id",
            "to_node_id",
            "geom",
            "length_meter",
            "walk_access",
            "avg_slope_percent",
            "width_meter",
            "braille_block_state",
            "audio_signal_state",
            "width_state",
            "surface_state",
            "stairs_state",
            "signal_state",
            "segment_type",
        })

        self.assertIn('"vertex_id" AS vertex_id', nodes_sql)
        self.assertIn('ORDER BY "vertex_id"', nodes_sql)
        self.assertIn('"edge_id" AS edge_id', segments_sql)
        self.assertIn('COALESCE("walk_access"::text', segments_sql)
        self.assertIn('ORDER BY "edge_id"', segments_sql)

    def test_default_export_sql_falls_back_to_legacy_camel_case_columns(self):
        module = load_export_module()

        nodes_sql = module.build_road_nodes_sql({"vertexId", "point"})
        segments_sql = module.build_road_segments_sql({
            "edgeId",
            "fromNodeId",
            "toNodeId",
            "geom",
            "lengthMeter",
            "walkAccess",
            "avgSlopePercent",
            "widthMeter",
            "brailleBlockState",
            "audioSignalState",
            "widthState",
            "surfaceState",
            "stairsState",
            "signalState",
            "segmentType",
        })

        self.assertIn('"vertexId" AS vertex_id', nodes_sql)
        self.assertIn('ORDER BY "vertexId"', nodes_sql)
        self.assertIn('"edgeId" AS edge_id', segments_sql)
        self.assertIn('"fromNodeId" AS from_node_id', segments_sql)
        self.assertIn('COALESCE("walkAccess"::text', segments_sql)
        self.assertIn('ORDER BY "edgeId"', segments_sql)

    def test_default_feature_sql_reads_snake_case_state_columns(self):
        module = load_export_module()

        features_sql = module.build_segment_features_sql({
            "feature_id",
            "edge_id",
            "feature_type",
            "geom",
            "state",
            "value_number",
        })

        self.assertIn('"feature_id" AS feature_id', features_sql)
        self.assertIn('"edge_id" AS edge_id', features_sql)
        self.assertIn('"feature_type"::text AS feature_type', features_sql)
        self.assertIn('"state"::text AS state', features_sql)
        self.assertIn('"value_number" AS value_number', features_sql)
        self.assertIn("WHERE \"feature_type\"::text IN ('CROSSWALK', 'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'STAIRS')", features_sql)
        self.assertIn('ORDER BY "edge_id", "feature_id"', features_sql)

    def test_default_feature_sql_falls_back_to_legacy_camel_case_columns(self):
        module = load_export_module()

        features_sql = module.build_segment_features_sql({
            "featureId",
            "edgeId",
            "featureType",
            "geom",
            "state",
            "valueNumber",
        })

        self.assertIn('"featureId" AS feature_id', features_sql)
        self.assertIn('"edgeId" AS edge_id', features_sql)
        self.assertIn('"featureType"::text AS feature_type', features_sql)
        self.assertIn('"state"::text AS state', features_sql)
        self.assertIn('"valueNumber" AS value_number', features_sql)
        self.assertIn("WHERE \"featureType\"::text IN ('CROSSWALK', 'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'STAIRS')", features_sql)
        self.assertIn('ORDER BY "edgeId", "featureId"', features_sql)

    def test_default_feature_sql_allows_old_minimal_feature_table_without_state_columns(self):
        module = load_export_module()

        features_sql = module.build_segment_features_sql({"feature_id", "edge_id", "feature_type", "geom"})

        self.assertIn("NULL::text AS state", features_sql)
        self.assertIn("NULL::numeric AS value_number", features_sql)

    def test_validate_graph_reports_pass_with_unknown_warnings(self):
        module = load_export_module()

        report = module.validate_graph(self.sample_nodes(), self.sample_segments(), "road-network.osm")

        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["summary"]["nodeCount"], 3)
        self.assertEqual(report["summary"]["segmentCount"], 2)
        self.assertEqual(report["summary"]["routeableEdgeCount"], 1)
        self.assertEqual(report["summary"]["blockerCount"], 0)
        self.assertEqual(report["enumCounts"]["segment_type"]["CROSS_WALK"], 1)
        self.assertTrue(any(warning["kind"] == "high_unknown_ratio" for warning in report["warnings"]))

    def test_validate_graph_blocks_bad_topology_and_enum(self):
        module = load_export_module()
        bad_segments = self.sample_segments()
        bad_segments[1] = {
            **bad_segments[1],
            "to_node_id": 999,
            "geom_wkt": "LINESTRING(128.2 35.2, 128.21 35.21)",
            "surface_state": "YES",
        }

        report = module.validate_graph(self.sample_nodes(), bad_segments, "road-network.osm")

        blocker_kinds = {blocker["kind"] for blocker in report["blockers"]}
        self.assertEqual(report["status"], "FAIL")
        self.assertIn("missing_node_reference", blocker_kinds)
        self.assertIn("enum_violation", blocker_kinds)

    def test_write_json_report_creates_parent_directory(self):
        module = load_export_module()
        report = module.validate_graph(self.sample_nodes(), self.sample_segments(), "road-network.osm")

        with tempfile.TemporaryDirectory() as directory:
            report_path = Path(directory) / "validation" / "report.json"
            module.write_json_report(report_path, report)
            parsed = json.loads(report_path.read_text(encoding="utf-8"))

        self.assertEqual(parsed["status"], "PASS")

    def test_segment_features_split_source_segment_only_for_position_events(self):
        # 계단 같은 위치 이벤트는 분할 기준이 되지만, 경사 같은 구간 속성은
        # road_segments 집계 컬럼에서만 읽고 segment_features에서는 무시한다.
        module = load_export_module()
        nodes = [
            {"vertex_id": 1, "lon": 0.0, "lat": 0.0},
            {"vertex_id": 2, "lon": 10.0, "lat": 0.0},
        ]
        segments = [
            {
                "edge_id": 100,
                "from_node_id": 1,
                "to_node_id": 2,
                "geom_wkt": "LINESTRING(0 0, 10 0)",
                "walk_access": "YES",
                "avg_slope_percent": "0.0",
                "width_meter": "2.0",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "ADEQUATE_150",
                "surface_state": "PAVED",
                "stairs_state": "NO",
                "signal_state": "UNKNOWN",
                "segment_type": "SIDE_LINE",
            }
        ]
        features = [
            {
                "feature_id": 1,
                "edge_id": 100,
                "feature_type": "STAIRS",
                "geom_wkt": "LINESTRING(2 0, 4 0)",
                "state": "YES",
                "value_number": None,
            },
            {
                "feature_id": 2,
                "edge_id": 100,
                "feature_type": "SLOPE",
                "geom_wkt": "LINESTRING(6 0, 10 0)",
                "state": None,
                "value_number": "13.0",
            },
        ]

        output_nodes, output_segments = module.apply_segment_features_to_export(nodes, segments, features)

        self.assertEqual(len(output_nodes), 4)
        self.assertEqual(len(output_segments), 3)
        self.assertEqual([segment["from_node_id"] for segment in output_segments], [1, 3, 4])
        self.assertEqual([segment["to_node_id"] for segment in output_segments], [3, 4, 2])
        self.assertEqual(output_segments[1]["stairs_state"], "YES")
        self.assertTrue(all(segment["avg_slope_percent"] == "0.0" for segment in output_segments))

        report = module.validate_graph(output_nodes, output_segments, "road-network.osm")
        self.assertEqual(report["status"], "PASS")

    def test_segment_features_can_set_crosswalk_and_audio_states_without_width_override(self):
        # segment 전체를 덮는 feature는 상태값만 덮어쓴다.
        # point feature는 뒤쪽 child에만 영향을 주므로 분할 경계를 만든다.
        module = load_export_module()
        nodes = [
            {"vertex_id": 1, "lon": 0.0, "lat": 0.0},
            {"vertex_id": 2, "lon": 10.0, "lat": 0.0},
        ]
        segments = [
            {
                "edge_id": 200,
                "from_node_id": 1,
                "to_node_id": 2,
                "geom_wkt": "LINESTRING(0 0, 10 0)",
                "walk_access": "YES",
                "avg_slope_percent": "0.0",
                "width_meter": "0.0",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "UNKNOWN",
                "surface_state": "PAVED",
                "stairs_state": "NO",
                "signal_state": "UNKNOWN",
                "segment_type": "SIDE_LINE",
            }
        ]
        features = [
            {
                "feature_id": 1,
                "edge_id": 200,
                "feature_type": "CROSSWALK",
                "geom_wkt": "LINESTRING(0 0, 10 0)",
                "state": "YES",
                "value_number": None,
            },
            {
                "feature_id": 2,
                "edge_id": 200,
                "feature_type": "WIDTH",
                "geom_wkt": "LINESTRING(0 0, 10 0)",
                "state": None,
                "value_number": "1.25",
            },
            {
                "feature_id": 3,
                "edge_id": 200,
                "feature_type": "AUDIO_SIGNAL",
                "geom_wkt": "POINT(5 0)",
                "state": "YES",
                "value_number": None,
            },
        ]

        output_nodes, output_segments = module.apply_segment_features_to_export(nodes, segments, features)

        self.assertEqual(len(output_nodes), 3)
        self.assertEqual(len(output_segments), 2)
        for segment in output_segments:
            self.assertEqual(segment["segment_type"], "CROSS_WALK")
            self.assertEqual(segment["signal_state"], "YES")
            self.assertEqual(segment["width_meter"], "0.0")
            self.assertEqual(segment["width_state"], "UNKNOWN")
        self.assertEqual(output_segments[0]["audio_signal_state"], "UNKNOWN")
        self.assertEqual(output_segments[1]["audio_signal_state"], "YES")

    def test_near_duplicate_feature_points_do_not_create_zero_length_child_segment(self):
        module = load_export_module()
        nodes = [
            {"vertex_id": 1, "lon": 0.0, "lat": 0.0},
            {"vertex_id": 2, "lon": 10.0, "lat": 0.0},
        ]
        segments = [
            {
                "edge_id": 300,
                "from_node_id": 1,
                "to_node_id": 2,
                "geom_wkt": "LINESTRING(0 0, 10 0)",
                "walk_access": "YES",
                "avg_slope_percent": "0.0",
                "width_meter": "2.0",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "ADEQUATE_150",
                "surface_state": "PAVED",
                "stairs_state": "NO",
                "signal_state": "UNKNOWN",
                "segment_type": "SIDE_LINE",
            }
        ]
        features = [
            {
                "feature_id": 1,
                "edge_id": 300,
                "feature_type": "CROSSWALK",
                "geom_wkt": "POINT(5.0000000000001 0)",
                "state": "YES",
                "value_number": None,
            },
            {
                "feature_id": 2,
                "edge_id": 300,
                "feature_type": "BRAILLE_BLOCK",
                "geom_wkt": "POINT(5.0000000000002 0)",
                "state": "YES",
                "value_number": None,
            },
        ]

        output_nodes, output_segments = module.apply_segment_features_to_export(nodes, segments, features)

        self.assertEqual(len(output_nodes), 3)
        self.assertEqual(len(output_segments), 2)
        self.assertEqual([segment["from_node_id"] for segment in output_segments], [1, 3])
        self.assertEqual([segment["to_node_id"] for segment in output_segments], [3, 2])
        report = module.validate_graph(output_nodes, output_segments, "road-network.osm")
        self.assertEqual(report["status"], "PASS")

    def test_close_point_features_do_not_collapse_child_segment_after_rounding(self):
        # 아주 가까운 두 point feature가 같은 source edge를 나눌 때
        # WKT/OSM 좌표 반올림 때문에 zero-length child가 생기면 안 된다.
        module = load_export_module()
        nodes = [
            {"vertex_id": 1, "lon": 128.82709585, "lat": 35.09417414},
            {"vertex_id": 2, "lon": 128.82678768, "lat": 35.09394820},
        ]
        segments = [
            {
                "edge_id": 4900,
                "from_node_id": 1,
                "to_node_id": 2,
                "geom_wkt": "LINESTRING(128.82709585 35.09417414, 128.82678768 35.09394820)",
                "walk_access": "YES",
                "avg_slope_percent": "0.0",
                "width_meter": "0.0",
                "braille_block_state": "UNKNOWN",
                "audio_signal_state": "UNKNOWN",
                "width_state": "UNKNOWN",
                "surface_state": "PAVED",
                "stairs_state": "NO",
                "signal_state": "UNKNOWN",
                "segment_type": "SIDE_LINE",
            }
        ]
        features = [
            {
                "feature_id": 939,
                "edge_id": 4900,
                "feature_type": "CROSSWALK",
                "geom_wkt": "POINT(128.8269182 35.0940894)",
                "state": "YES",
                "value_number": None,
            },
            {
                "feature_id": 3263,
                "edge_id": 4900,
                "feature_type": "BRAILLE_BLOCK",
                "geom_wkt": "POINT(128.8269182 35.09408939)",
                "state": "YES",
                "value_number": None,
            },
        ]

        output_nodes, output_segments = module.apply_segment_features_to_export(nodes, segments, features)
        report = module.validate_graph(output_nodes, output_segments, "road-network.osm")

        self.assertEqual(report["status"], "PASS")
        middle_segment = output_segments[1]
        self.assertNotEqual(
            module.parse_linestring_wkt(middle_segment["geom_wkt"])[0],
            module.parse_linestring_wkt(middle_segment["geom_wkt"])[-1],
        )

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "road-network.osm"
            module.write_osm(output_nodes, output_segments, output)
            xml = output.read_text(encoding="utf-8")

        self.assertIn("128.826939899868", xml)
        self.assertIn("128.826939895100", xml)

    def test_export_precision_merge_prevents_collapsed_synthetic_child(self):
        module = load_export_module()
        original_decimal_places = module.GEOMETRY_DECIMAL_PLACES
        module.GEOMETRY_DECIMAL_PLACES = 8
        try:
            nodes = [
                {"vertex_id": 1, "lon": 128.82709585, "lat": 35.09417414},
                {"vertex_id": 2, "lon": 128.82678768, "lat": 35.09394820},
            ]
            segments = [
                {
                    "edge_id": 4900,
                    "from_node_id": 1,
                    "to_node_id": 2,
                    "geom_wkt": "LINESTRING(128.82709585 35.09417414, 128.82678768 35.09394820)",
                    "walk_access": "YES",
                    "avg_slope_percent": "0.0",
                    "width_meter": "0.0",
                    "braille_block_state": "UNKNOWN",
                    "audio_signal_state": "UNKNOWN",
                    "width_state": "UNKNOWN",
                    "surface_state": "PAVED",
                    "stairs_state": "NO",
                    "signal_state": "UNKNOWN",
                    "segment_type": "SIDE_LINE",
                }
            ]
            features = [
                {
                    "feature_id": 939,
                    "edge_id": 4900,
                    "feature_type": "CROSSWALK",
                    "geom_wkt": "POINT(128.8269182 35.0940894)",
                    "state": "YES",
                    "value_number": None,
                },
                {
                    "feature_id": 3263,
                    "edge_id": 4900,
                    "feature_type": "BRAILLE_BLOCK",
                    "geom_wkt": "POINT(128.8269182 35.09408939)",
                    "state": "YES",
                    "value_number": None,
                },
            ]

            output_nodes, output_segments = module.apply_segment_features_to_export(nodes, segments, features)
            report = module.validate_graph(output_nodes, output_segments, "road-network.osm")

            self.assertEqual(report["status"], "PASS")
            for segment in output_segments:
                coords = module.parse_linestring_wkt(segment["geom_wkt"])
                self.assertNotEqual(coords[0], coords[-1])
        finally:
            module.GEOMETRY_DECIMAL_PLACES = original_decimal_places

    def test_custom_models_use_canonical_accessibility_enums(self):
        allowed_width_conditions = {
            "width_state == ADEQUATE_120",
            "width_state == NARROW",
            "width_state == UNKNOWN",
        }
        for model_path in CUSTOM_MODEL_DIR.glob("*.json"):
            model = json.loads(model_path.read_text(encoding="utf-8"))
            conditions = [
                priority_rule.get("if", "")
                for priority_rule in model.get("priority", [])
            ]
            joined_conditions = "\n".join(conditions)

            self.assertIn("avg_slope_percent", joined_conditions, model_path.name)
            width_conditions = {
                condition for condition in conditions
                if condition.startswith("width_state == ")
            }
            self.assertTrue(width_conditions <= allowed_width_conditions, model_path.name)

            if "wheelchair" in model_path.name or "visual_safe" in model_path.name:
                self.assertIn("width_state == ADEQUATE_120", joined_conditions, model_path.name)
            self.assertIn("width_state == NARROW", joined_conditions, model_path.name)


if __name__ == "__main__":
    unittest.main()
