import csv
import inspect
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

import sys

ROOT_DIR = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT_DIR / "scripts" / "db"))

import load_accessibility_features as loader  # noqa: E402


class AccessibilityFeatureLoaderTest(unittest.TestCase):
    def test_normalizes_policy_values(self):
        self.assertEqual(loader.derive_width_state(Decimal("1.50")), "ADEQUATE_150")
        self.assertEqual(loader.derive_width_state(Decimal("1.20")), "ADEQUATE_120")
        self.assertEqual(loader.derive_width_state(Decimal("1.19")), "NARROW")

        self.assertEqual(loader.normalize_surface_state("비포장"), "UNPAVED")
        self.assertEqual(loader.normalize_surface_state("아스팔트"), "PAVED")
        self.assertEqual(loader.normalize_ewkt("POINT(129.1 35.1)"), "SRID=4326;POINT(129.1 35.1)")

    def test_parses_source_csvs_into_feature_rows(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = Path(temp_dir)
            self.write_csv(
                source_dir / "인도&인도폭.csv",
                ["sourceId", "wkt", "widthMeter", "surfaceLabel", "surfaceCode"],
                [["side-1", "LINESTRING(129.1 35.1,129.2 35.2)", "1.4", "아스팔트", ""]],
            )
            self.write_csv(
                source_dir / "이면도로.csv",
                ["sourceId", "geometryWkt", "slopePercent", "surfaceLabel", "widthMeter"],
                [["local-1", "LINESTRING(129.1 35.1,129.2 35.2)", "6.0", "비포장", "1.1"]],
            )
            self.write_csv(
                source_dir / "경사도&표면타입.csv",
                ["sourceId", "geometryWkt", "slopeMean", "surfaceType", "widthMeter"],
                [["slope-1", "LINESTRING(129.1 35.1,129.2 35.2)", "10.0", "PAVED", "1.6"]],
            )
            self.write_csv(
                source_dir / "횡단보도_신호등.csv",
                ["sourceId", "point", "crossingState"],
                [["cross-1", "POINT(129.1 35.1)", "TRAFFIC_SIGNALS"]],
            )
            self.write_csv(
                source_dir / "횡단보도_음향신호기.csv",
                ["sourceId", "lat", "lng", "audioSignalState", "stat"],
                [["audio-1", "35.1", "129.1", "YES", "정상동작"]],
            )
            self.write_csv(
                source_dir / "계단.csv",
                ["sourceId", "geometryWkt", "stairsState"],
                [["stairs-1", "POINT(129.1 35.1)", "YES"]],
            )
            self.write_csv(
                source_dir / "점자블록.csv",
                ["sourceId", "geom", "brailleBlockState", "signalState"],
                [["braille-1", "LINESTRING(129.1 35.1,129.2 35.2)", "no", "UNKNOWN"]],
            )
            self.write_csv(
                source_dir / "지하철_엘리베이터.csv",
                ["sourceId", "point"],
                [["elevator-1", "POINT(129.1 35.1)"]],
            )

            rows, issues, report = loader.parse_source_features(source_dir)

        self.assertFalse(issues)
        feature_counts = report["parsedFeatureTypeCounts"]
        self.assertEqual(feature_counts["WALK_ACCESS"], 2)
        self.assertEqual(feature_counts["WIDTH"], 3)
        self.assertEqual(feature_counts["SURFACE"], 3)
        self.assertEqual(feature_counts["SLOPE"], 2)
        self.assertEqual(feature_counts["CROSSWALK"], 1)
        self.assertEqual(feature_counts["SIGNAL"], 1)
        self.assertEqual(feature_counts["AUDIO_SIGNAL"], 1)
        self.assertEqual(feature_counts["STAIRS"], 1)
        self.assertEqual(feature_counts["BRAILLE_BLOCK"], 1)
        self.assertEqual(len(rows), 15)
        self.assertNotIn("지하철_엘리베이터.csv", {row.source_file for row in rows})

        walk_access = [row for row in rows if row.feature_type == "WALK_ACCESS"]
        self.assertEqual(len(walk_access), 2)
        self.assertTrue(all(row.state == "YES" for row in walk_access))
        self.assertTrue(all(row.update_walk_access for row in walk_access))
        braille = next(row for row in rows if row.feature_type == "BRAILLE_BLOCK")
        self.assertEqual(braille.state, "NO")
        self.assertEqual(braille.geometry_kind, "LINESTRING")
        self.assertEqual(braille.threshold_meter, Decimal("0"))
        audio = next(row for row in rows if row.feature_type == "AUDIO_SIGNAL")
        self.assertEqual(audio.geom_ewkt, "SRID=4326;POINT(129.1 35.1)")
        crosswalk = next(row for row in rows if row.feature_type == "CROSSWALK")
        self.assertTrue(crosswalk.prefer_crosswalk)
        signal = next(row for row in rows if row.feature_type == "SIGNAL")
        self.assertEqual(signal.state, "YES")
        slope = next(row for row in rows if row.feature_type == "SLOPE")
        self.assertIsNone(slope.state)
        self.assertIsNotNone(slope.value_number)

    def test_position_event_feature_types_exclude_segment_attributes(self):
        self.assertEqual(
            loader.POSITION_EVENT_FEATURE_TYPES,
            {"CROSSWALK", "AUDIO_SIGNAL", "BRAILLE_BLOCK", "STAIRS"},
        )
        self.assertTrue({"SLOPE", "SURFACE", "WIDTH"}.isdisjoint(loader.POSITION_EVENT_FEATURE_TYPES))
        self.assertTrue({"SIGNAL", "WALK_ACCESS"}.isdisjoint(loader.POSITION_EVENT_FEATURE_TYPES))

    def test_reports_missing_required_csv_header_before_row_parsing(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = Path(temp_dir)
            self.write_csv(
                source_dir / "횡단보도_신호등.csv",
                ["sourceId", "wrongGeometry"],
                [["cross-1", "POINT(129.1 35.1)"]],
            )

            rows, issues, report = loader.parse_source_features(source_dir, require_files=False)

        self.assertFalse(rows)
        self.assertIn(
            {
                "source_file": "횡단보도_신호등.csv",
                "csv_line_no": 1,
                "source_id": "",
                "reason": "required CSV header missing: one of point, geom, geometryWkt, wkt",
            },
            report["parseIssueSamples"],
        )
        self.assertTrue(any(issue.csv_line_no == 1 for issue in issues))

    def test_position_event_segment_feature_sql_filter_is_centralized(self):
        self.assertEqual(
            loader.position_event_feature_type_sql(),
            "'AUDIO_SIGNAL', 'BRAILLE_BLOCK', 'CROSSWALK', 'STAIRS'",
        )

    def test_braille_block_lines_match_only_same_crosswalk_geometry(self):
        source = inspect.getsource(loader.build_matching_tables)

        self.assertIn("feature_type = 'BRAILLE_BLOCK' AS has_braille_block", source)
        self.assertIn("f.has_braille_block", source)
        self.assertIn("s.segment_type = 'CROSS_WALK'", source)
        self.assertIn('ST_Equals(f.geom, s."geom")', source)
        self.assertIn("ST_DWithin(f.geom_5179, s.geom_5179, f.threshold_meter)", source)

    def test_segment_feature_insert_dedupes_by_edge_type_and_state(self):
        source = inspect.getsource(loader.insert_and_update)

        self.assertIn("DISTINCT ON (edge_id, feature_type, COALESCE(state, ''))", source)
        self.assertIn("deduped_segment_features", source)

    def test_source_features_schema_and_replacement_are_persistent(self):
        schema_source = inspect.getsource(loader.ensure_schema)
        replace_source = inspect.getsource(loader.replace_source_features)

        self.assertIn("CREATE TABLE IF NOT EXISTS source_features", schema_source)
        self.assertIn("idx_source_features_geom", schema_source)
        self.assertIn("USING GIST", schema_source)
        self.assertIn("TRUNCATE TABLE source_features RESTART IDENTITY", replace_source)
        self.assertIn("INSERT INTO source_features", replace_source)

    @staticmethod
    def write_csv(path, headers, rows):
        with path.open("w", newline="", encoding="utf-8") as file:
            writer = csv.writer(file)
            writer.writerow(headers)
            writer.writerows(rows)


if __name__ == "__main__":
    unittest.main()
