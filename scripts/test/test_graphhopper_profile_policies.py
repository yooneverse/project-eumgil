#!/usr/bin/env python3
"""GraphHopper 접근성 profile 정책 fixture 테스트다.

테스트는 `INF/graphhopper/custom_models/*.json`을 직접 읽는다. 실행 중인
GraphHopper 서버가 없어도 되며, 이후 route smoke에서 같은 동작을 만들어야 하는
정책 규칙을 고정한다.
"""
import json
import unittest
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[2]
CUSTOM_MODEL_DIR = ROOT_DIR / "INF" / "graphhopper" / "custom_models"


def load_custom_models():
    return {
        model_path.stem: json.loads(model_path.read_text(encoding="utf-8"))
        for model_path in CUSTOM_MODEL_DIR.glob("*.json")
    }


def priority_multiplier(model, condition):
    """정확히 일치하는 custom model priority 조건의 multiplier를 찾는다."""
    for rule in model.get("priority", []):
        if rule.get("if") == condition:
            return str(rule.get("multiply_by"))
    return None


def priority_multiplier_number(model, condition):
    value = priority_multiplier(model, condition)
    return None if value is None else float(value)


class GraphhopperProfilePolicyTest(unittest.TestCase):
    """route profile이 사용하는 custom model 정책 계약 회귀 테스트다."""

    def test_walk_access_no_is_blocked_for_all_profiles(self):
        for profile_name, model in load_custom_models().items():
            with self.subTest(profile=profile_name):
                self.assertEqual(priority_multiplier(model, "walk_access == NO"), "0")

    def test_wheelchair_profiles_block_stairs(self):
        models = load_custom_models()
        wheelchair_profiles = [
            "wheelchair_auto_fast",
            "wheelchair_auto_safe",
            "wheelchair_manual_fast",
            "wheelchair_manual_safe",
        ]
        for profile_name in wheelchair_profiles:
            with self.subTest(profile=profile_name):
                self.assertEqual(priority_multiplier(models[profile_name], "stairs_state == YES"), "0.00")

    def test_manual_wheelchair_width_policy(self):
        models = load_custom_models()

        self.assertEqual(
            priority_multiplier(models["wheelchair_manual_safe"], "width_state == ADEQUATE_120"),
            "0",
        )
        self.assertEqual(
            priority_multiplier(models["wheelchair_manual_safe"], "width_state == NARROW"),
            "0",
        )
        self.assertEqual(
            priority_multiplier(models["wheelchair_manual_fast"], "width_state == ADEQUATE_120"),
            "1.0",
        )

    def test_safe_profiles_avoid_accessibility_risks_more_than_fast_profiles(self):
        # priority multiplier가 낮을수록 GraphHopper가 해당 edge를 더 강하게 회피한다.
        # 따라서 safe profile은 같은 사용자군의 fast profile보다 값이 낮아야 한다.
        models = load_custom_models()
        profile_pairs = [
            ("pedestrian_safe", "pedestrian_fast"),
            ("visual_safe", "visual_fast"),
            ("wheelchair_auto_safe", "wheelchair_auto_fast"),
            ("wheelchair_manual_safe", "wheelchair_manual_fast"),
        ]
        high_slope_conditions = {
            "pedestrian_safe": "avg_slope_percent >= 12.0",
            "visual_safe": "avg_slope_percent >= 8.33",
            "wheelchair_auto_safe": "avg_slope_percent >= 10.0",
            "wheelchair_manual_safe": "avg_slope_percent >= 8.33 && avg_slope_percent < 15.0",
        }

        for safe_profile, fast_profile in profile_pairs:
            risk_conditions = [
                "width_state == NARROW",
                "surface_state == UNPAVED",
                high_slope_conditions[safe_profile],
            ]
            for condition in risk_conditions:
                with self.subTest(safe=safe_profile, fast=fast_profile, condition=condition):
                    self.assertLess(
                        priority_multiplier_number(models[safe_profile], condition),
                        priority_multiplier_number(models[fast_profile], condition),
                    )

    def test_safe_profiles_prefer_signalized_crosswalk_over_unsignalized_crosswalk(self):
        # 신호 횡단은 대기시간이 추가될 수 있지만, safe profile의 priority model에서는
        # 무신호 횡단보다 안전성이 높은 신호 횡단을 선호해야 한다.
        models = load_custom_models()
        safe_profiles = [
            "pedestrian_safe",
            "visual_safe",
            "wheelchair_auto_safe",
            "wheelchair_manual_safe",
        ]

        for profile_name in safe_profiles:
            with self.subTest(profile=profile_name):
                signalized = priority_multiplier_number(
                    models[profile_name],
                    "segment_type == CROSS_WALK && signal_state == YES",
                )
                unsignalized = priority_multiplier_number(
                    models[profile_name],
                    "segment_type == CROSS_WALK && signal_state == NO",
                )
                self.assertGreater(signalized, unsignalized)

    def test_unknown_values_are_weak_non_blocking_penalties(self):
        models = load_custom_models()

        for profile_name, model in models.items():
            unknown_rules = [
                rule
                for rule in model.get("priority", [])
                if "UNKNOWN" in rule.get("if", "")
            ]
            self.assertTrue(unknown_rules, profile_name)

            for rule in unknown_rules:
                multiplier = float(rule["multiply_by"])
                with self.subTest(profile=profile_name, condition=rule["if"]):
                    self.assertGreater(multiplier, 0.0)
                    self.assertLessEqual(multiplier, 1.0)
                    self.assertGreaterEqual(multiplier, 0.60)

    def test_safe_profiles_penalize_shared_unknown_values_more_than_fast_profiles(self):
        # UNKNOWN은 경로를 끊지 않는 약한 penalty로 둔다. 다만 같은 사용자군에서는
        # safe profile이 fast profile보다 불확실한 접근성 정보를 더 조심스럽게 본다.
        models = load_custom_models()
        profile_pairs = [
            ("pedestrian_safe", "pedestrian_fast"),
            ("visual_safe", "visual_fast"),
            ("wheelchair_auto_safe", "wheelchair_auto_fast"),
            ("wheelchair_manual_safe", "wheelchair_manual_fast"),
        ]

        for safe_profile, fast_profile in profile_pairs:
            safe_unknown_conditions = {
                rule["if"]
                for rule in models[safe_profile].get("priority", [])
                if "UNKNOWN" in rule.get("if", "")
            }
            fast_unknown_conditions = {
                rule["if"]
                for rule in models[fast_profile].get("priority", [])
                if "UNKNOWN" in rule.get("if", "")
            }

            for condition in safe_unknown_conditions & fast_unknown_conditions:
                with self.subTest(safe=safe_profile, fast=fast_profile, condition=condition):
                    self.assertLess(
                        priority_multiplier_number(models[safe_profile], condition),
                        priority_multiplier_number(models[fast_profile], condition),
                    )

    def test_safe_profiles_allow_more_detour_than_fast_profiles(self):
        # GraphHopper distance_influence가 낮을수록 더 긴 우회를 허용한다.
        # safe profile은 접근성 위험 회피를 위해 같은 사용자군의 fast profile보다 낮아야 한다.
        models = load_custom_models()
        profile_pairs = [
            ("pedestrian_safe", "pedestrian_fast"),
            ("visual_safe", "visual_fast"),
            ("wheelchair_auto_safe", "wheelchair_auto_fast"),
            ("wheelchair_manual_safe", "wheelchair_manual_fast"),
        ]

        for safe_profile, fast_profile in profile_pairs:
            with self.subTest(safe=safe_profile, fast=fast_profile):
                self.assertLess(
                    models[safe_profile]["distance_influence"],
                    models[fast_profile]["distance_influence"],
                )

    def test_distance_influence_matches_initial_tuning_policy(self):
        models = load_custom_models()
        expected = {
            "pedestrian_safe": 40,
            "pedestrian_fast": 90,
            "visual_safe": 35,
            "visual_fast": 90,
            "wheelchair_auto_safe": 45,
            "wheelchair_auto_fast": 95,
            "wheelchair_manual_safe": 50,
            "wheelchair_manual_fast": 110,
        }

        for profile_name, distance_influence in expected.items():
            with self.subTest(profile=profile_name):
                self.assertEqual(models[profile_name]["distance_influence"], distance_influence)


if __name__ == "__main__":
    unittest.main()
