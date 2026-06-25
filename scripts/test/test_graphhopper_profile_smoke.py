#!/usr/bin/env python3
"""GraphHopper runtime profile smoke의 접근성 정책 검증 단위 테스트다."""
import importlib.util
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse


ROOT_DIR = Path(__file__).resolve().parents[2]
SMOKE_SCRIPT = ROOT_DIR / "scripts" / "graphhopper" / "smoke_graphhopper_profiles.py"


def load_smoke_module():
    spec = importlib.util.spec_from_file_location("smoke_graphhopper_profiles", SMOKE_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class GraphhopperProfileSmokeTest(unittest.TestCase):
    """runtime smoke가 단순 route 존재 이상을 확인하는지 검증한다."""

    def sample_candidate(self):
        return {
            "from_lat": 35.1,
            "from_lon": 128.1,
            "to_lat": 35.2,
            "to_lon": 128.2,
        }

    def test_route_url_requests_accessibility_path_details(self):
        module = load_smoke_module()

        url = module.route_url("http://graphhopper:8989", self.sample_candidate(), "pedestrian_safe", module.POLICY_DETAILS)
        query = parse_qs(urlparse(url).query)

        self.assertEqual(query["profile"], ["pedestrian_safe"])
        self.assertEqual(query["points_encoded"], ["false"])
        self.assertEqual(set(query["details"]), set(module.POLICY_DETAILS))

    def test_policy_details_fail_when_custom_encoded_values_are_missing(self):
        module = load_smoke_module()

        violations = module.validate_policy_details("pedestrian_safe", {"details": {}}, require_details=True)

        self.assertEqual(violations[0]["kind"], "missing_policy_detail")
        self.assertIn("walk_access", violations[0]["details"])

    def test_policy_details_fail_when_blocked_walk_access_is_used(self):
        module = load_smoke_module()
        path = {
            "details": {
                detail: [[0, 1, "UNKNOWN"]]
                for detail in module.POLICY_DETAILS
            }
        }
        path["details"]["walk_access"] = [[0, 1, "NO"]]

        violations = module.validate_policy_details("pedestrian_fast", path, require_details=True)

        self.assertEqual(violations[0]["kind"], "blocked_walk_access_used")

    def test_policy_details_fail_when_wheelchair_route_uses_stairs(self):
        module = load_smoke_module()
        path = {
            "details": {
                detail: [[0, 1, "UNKNOWN"]]
                for detail in module.POLICY_DETAILS
            }
        }
        path["details"]["stairs_state"] = [[0, 1, "YES"]]

        violations = module.validate_policy_details("wheelchair_manual_safe", path, require_details=True)

        self.assertEqual(violations[0]["kind"], "wheelchair_stairs_used")

    def test_policy_details_pass_for_canonical_safe_route(self):
        module = load_smoke_module()
        path = {
            "details": {
                "walk_access": [[0, 1, "YES"]],
                "stairs_state": [[0, 1, "NO"]],
                "avg_slope_percent": [[0, 1, "2.5"]],
                "width_state": [[0, 1, "ADEQUATE_150"]],
                "surface_state": [[0, 1, "PAVED"]],
                "signal_state": [[0, 1, "YES"]],
                "segment_type": [[0, 1, "CROSS_WALK"]],
            }
        }

        self.assertEqual(module.validate_policy_details("wheelchair_auto_safe", path, require_details=True), [])
        self.assertEqual(
            module.summarize_policy_details(path)["segment_type"],
            {"CROSS_WALK": 1},
        )


if __name__ == "__main__":
    unittest.main()
