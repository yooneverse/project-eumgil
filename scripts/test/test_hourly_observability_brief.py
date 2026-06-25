#!/usr/bin/env python3
"""hourly observability brief 회귀 테스트다."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


ROOT_DIR = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT_DIR / "scripts" / "monitoring" / "hourly_observability_brief.py"
WRITE_MANIFEST_PATH = ROOT_DIR / "scripts" / "deploy" / "write-release-manifest.py"
SPEC = importlib.util.spec_from_file_location("hourly_observability_brief", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class HourlyObservabilityBriefTest(unittest.TestCase):
    def sample_report(self, environment: str, current: int, previous: int, *, down_targets: list[str] | None = None):
        return MODULE.EnvReport(
            environment=environment,
            current_count=current,
            previous_count=previous,
            delta=current - previous,
            total_logs_current=max(current * 100, 1),
            total_logs_previous=max(previous * 100, 1),
            total_logs_delta=max(current * 100, 1) - max(previous * 100, 1),
            warning_error_ratio=(current / max(current * 100, 1)),
            suppressed_count=0,
            top_services=[("backend", current)],
            top_total_services=[("backend", max(current * 100, 1))],
            health={"api": "UP", "db": "UP", "redis": "UP"},
            down_targets=down_targets or [],
            top_patterns=[("timeout on upstream", 7)],
            sample_logs=[
                MODULE.LogSample(
                    service="backend",
                    level="error",
                    timestamp="2026-05-13T14:00:00+09:00",
                    message="timeout while calling upstream service",
                )
            ],
            deploy_manifest=MODULE.DeployManifest(
                environment=environment,
                branch="develop" if environment == "dev" else "master",
                commit="abc123def456",
                deployed_at="2026-05-13T13:20:00+09:00",
                services=["backend", "ai"],
            ),
            recent_changes=[
                MODULE.GitRefItem(
                    kind="mr",
                    ref="!206",
                    title="monitoring 로그 정제",
                    web_url="https://git.example.com/s14-final/S14P31E102/-/merge_requests/206",
                    merged_or_created_at="2026-05-13T13:15:00+09:00",
                )
            ],
            notes=[],
        )

    def test_build_rule_summary_prefers_prod_when_prod_spikes(self):
        dev_report = self.sample_report("dev", 2, 2)
        prod_report = self.sample_report("prod", 48, 8)

        summary = MODULE.build_rule_summary(dev_report, prod_report)

        self.assertIn("prod", summary)
        self.assertIn("48", summary)

    def test_build_rule_summary_prefers_dev_when_health_is_down(self):
        dev_report = self.sample_report("dev", 6, 2, down_targets=["db"])
        prod_report = self.sample_report("prod", 1, 1)

        summary = MODULE.build_rule_summary(dev_report, prod_report)

        self.assertIn("dev", summary)
        self.assertIn("health DOWN", summary)

    def test_build_rule_summary_uses_watch_language_for_non_critical_delta(self):
        dev_report = self.sample_report("dev", 8, 7)
        prod_report = self.sample_report("prod", 0, 0)

        summary = MODULE.build_rule_summary(dev_report, prod_report)

        self.assertIn("반복 경고", summary)

    def test_normalize_log_message_masks_ids_and_numbers(self):
        message = "level=error requestId=123e4567-e89b-12d3-a456-426614174000 timeout after 502 ms trace 0xdeadbeef"

        normalized = MODULE.normalize_log_message(message)

        self.assertIn("<uuid>", normalized)
        self.assertIn("<n>", normalized)
        self.assertIn("<hex>", normalized)
        self.assertNotIn("123e4567", normalized)

    def test_render_mattermost_contains_both_env_sections(self):
        dev_report = self.sample_report("dev", 4, 1)
        prod_report = self.sample_report("prod", 12, 3)

        rendered = MODULE.render_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            rule_summary="prod 반복 패턴을 먼저 확인합니다.",
            agent_summary=None,
            dev_report=dev_report,
            prod_report=prod_report,
        )

        self.assertIn("#### :mag:", rendered)
        self.assertIn("##### :memo: 전체 요약", rendered)
        self.assertIn("**[prod]**", rendered)
        self.assertIn("**[dev]**", rendered)
        self.assertIn("전체 로그", rendered)
        self.assertIn("경고 비율", rendered)
        self.assertIn("ㅤ\n##### :pushpin: 대표 패턴", rendered)
        self.assertNotIn("##### :paperclip: 참고", rendered)
        self.assertNotIn("최근 배포", rendered)

    def test_render_environment_mattermost_formats_structured_incident_analysis(self):
        dev_report = self.sample_report("dev", 4, 1)
        prod_report = self.sample_report("prod", 12, 3)
        analysis = MODULE.EnvAnalysis(
            conclusion="dev 쪽은 큰 이상이 없고 prod 쪽을 먼저 보면 됩니다.",
            evidence="warning/error 4건, 직전 1건입니다.",
            impact="사용자 영향은 제한적입니다.",
            next_action="prod 이슈 확인 후 dev backend 패턴을 추적합니다.",
            confidence="medium",
            source="test",
        )

        rendered = MODULE.render_environment_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            report=dev_report,
            peer_report=prod_report,
            analysis=analysis,
        )

        self.assertIn("#### :mag: DEV 60분 로그 브리프", rendered)
        self.assertIn("ㅤ\n##### :bar_chart: 지표", rendered)
        self.assertIn("##### :bar_chart: 지표", rendered)
        self.assertIn("##### :memo: 요약", rendered)
        self.assertIn("backend에서 `timeout on upstream` 패턴", rendered)
        self.assertIn("다음 확인: prod 이슈 확인 후 dev backend 패턴을 추적합니다.", rendered)
        self.assertNotIn(":warning:", rendered)
        self.assertNotIn(":wrench:", rendered)
        self.assertNotIn("##### :paperclip: 참고", rendered)
        self.assertNotIn("**결론:**", rendered)
        self.assertNotIn("**근거:**", rendered)
        self.assertNotIn("**영향:**", rendered)
        self.assertNotIn("**조치:**", rendered)
        self.assertNotIn("**판단:**", rendered)
        self.assertNotIn("**환경 요약:**", rendered)
        self.assertNotIn("**전체 비교 요약:**", rendered)
        self.assertNotIn("**[dev]**", rendered)
        self.assertNotIn("**[prod]**", rendered)
        self.assertIn("전체 로그 상위 서비스", rendered)

    def test_send_mattermost_accepts_plain_text_webhook_response(self):
        class PlainTextResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, traceback):
                return False

            def read(self):
                return b"ok"

        with mock.patch.object(MODULE.request, "urlopen", return_value=PlainTextResponse()) as mocked_urlopen:
            MODULE.send_mattermost("https://meeting.example/hooks/test", "hello")

        request_arg = mocked_urlopen.call_args.args[0]
        self.assertEqual("POST", request_arg.get_method())
        self.assertEqual({"text": "hello"}, json.loads(request_arg.data.decode("utf-8")))
        self.assertEqual(20, mocked_urlopen.call_args.kwargs["timeout"])

    def test_fetch_local_commits_decodes_git_log_as_utf8(self):
        with mock.patch.object(
            MODULE.subprocess,
            "check_output",
            return_value="abcdef123456\tabcdef1\t운영 브리프 ✨ 개선\t2026-05-14T11:30:00+09:00",
        ) as mocked_check_output:
            items = MODULE.fetch_local_commits(
                gitlab_base_url="https://git.example.com",
                project_path="s14-final/S14P31E102",
                branch="develop",
                since=MODULE.utcnow(),
            )

        self.assertEqual("운영 브리프 ✨ 개선", items[0].title)
        self.assertEqual("utf-8", mocked_check_output.call_args.kwargs["encoding"])
        self.assertEqual("replace", mocked_check_output.call_args.kwargs["errors"])

    def test_emit_console_text_falls_back_to_utf8_on_console_encoding_failure(self):
        class FailingStdout:
            def __init__(self):
                self.buffer = mock.Mock()

            def write(self, value):
                raise UnicodeEncodeError("cp949", value, 0, 1, "blocked")

            def flush(self):
                pass

        failing_stdout = FailingStdout()
        with mock.patch.object(MODULE.sys, "stdout", failing_stdout):
            MODULE.emit_console_text("운영 브리프 ✨")

        written = failing_stdout.buffer.write.call_args.args[0]
        self.assertEqual("운영 브리프 ✨\n".encode("utf-8"), written)

    def test_build_rule_analysis_keeps_clean_environment_useful_without_agent(self):
        dev_report = self.sample_report("dev", 8, 4)
        prod_report = self.sample_report("prod", 0, 0)
        prod_report.top_services = []
        prod_report.sample_logs = []
        prod_report.top_patterns = []
        prod_report.total_logs_current = 2400
        prod_report.total_logs_previous = 2350
        prod_report.total_logs_delta = 50
        prod_report.warning_error_ratio = 0.0

        analysis = MODULE.build_rule_analysis(prod_report, dev_report)

        self.assertIn("prod", analysis.conclusion)
        self.assertIn("warning/error 0건", analysis.evidence)
        self.assertIn("health", analysis.impact)
        self.assertIn("같은 시간대", analysis.next_action)
        self.assertTrue(analysis.conclusion.strip())
        self.assertTrue(analysis.evidence.strip())
        self.assertTrue(analysis.impact.strip())
        self.assertTrue(analysis.next_action.strip())
        self.assertIn(analysis.confidence, {"high", "medium", "low"})

        rendered = MODULE.render_environment_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            report=prod_report,
            peer_report=dev_report,
            analysis=analysis,
        )
        self.assertIn("##### :bar_chart: 지표", rendered)
        self.assertIn("##### :memo: 요약", rendered)
        self.assertIn("warning/error가 없고 health도", rendered)
        self.assertIn("다음 확인:", rendered)
        self.assertNotIn(":white_check_mark:", rendered)
        self.assertNotIn(":eyes:", rendered)
        self.assertNotIn("##### :paperclip: 참고", rendered)
        self.assertNotIn("**결론:**", rendered)
        self.assertNotIn("**근거:**", rendered)
        self.assertNotIn("**영향:**", rendered)
        self.assertNotIn("**조치:**", rendered)
        self.assertNotIn("**판단:**", rendered)
        self.assertNotIn("**환경 요약:**", rendered)
        self.assertNotIn("**전체 비교 요약:**", rendered)

    def test_render_environment_mattermost_does_not_call_unknown_health_normal(self):
        dev_report = self.sample_report("dev", 0, 0)
        prod_report = self.sample_report("prod", 0, 0)
        dev_report.health = {"api": "UNKNOWN", "db": "UNKNOWN"}
        dev_report.top_services = []
        dev_report.top_patterns = []
        dev_report.sample_logs = []
        dev_report.notes = ["Loki query failed: connection refused"]
        analysis = MODULE.build_rule_analysis(dev_report, prod_report)

        rendered = MODULE.render_environment_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            report=dev_report,
            peer_report=prod_report,
            analysis=analysis,
        )

        self.assertIn("관측 데이터가 완전하지 않습니다", rendered)
        self.assertIn("Loki/Prometheus", rendered)
        self.assertNotIn(":grey_question:", rendered)
        self.assertNotIn("정상 베이스라인", rendered)

    def test_zero_loki_total_logs_is_observability_gap_even_when_health_is_up(self):
        dev_report = self.sample_report("dev", 0, 0)
        prod_report = self.sample_report("prod", 0, 0)
        dev_report.total_logs_current = 0
        dev_report.total_logs_previous = 0
        dev_report.total_logs_delta = 0
        dev_report.top_total_services = []
        dev_report.top_services = []
        dev_report.top_patterns = []
        dev_report.sample_logs = []
        dev_report.notes = []

        severity, reasons = MODULE.describe_env(dev_report)
        analysis = MODULE.build_rule_analysis(dev_report, prod_report)
        rendered = MODULE.render_environment_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            report=dev_report,
            peer_report=prod_report,
            analysis=analysis,
        )

        self.assertTrue(MODULE.has_observability_gap(dev_report))
        self.assertEqual("issue", severity)
        self.assertIn("Loki 로그 유입 공백", reasons)
        self.assertEqual("low", analysis.confidence)
        self.assertIn("Loki 로그 유입이 0건", analysis.conclusion)
        self.assertIn("Promtail", analysis.next_action)
        self.assertIn("관측 데이터가 완전하지 않습니다", rendered)
        self.assertIn("Loki 전체 로그가 `0`건", rendered)
        self.assertNotIn("정상 베이스라인", rendered)
        self.assertNotIn("warning/error가 없고 health도", rendered)

    def test_build_rule_analysis_uses_favorite_routes_constraint_next_action(self):
        dev_report = self.sample_report("dev", 6, 0)
        prod_report = self.sample_report("prod", 0, 0)
        dev_report.top_patterns = [
            (
                "favorite_routes_route_option_check failed for POST /favorite-routes SQLState 23514",
                6,
            )
        ]
        dev_report.sample_logs = [
            MODULE.LogSample(
                service="backend",
                level="error",
                timestamp="2026-05-14T11:30:00+09:00",
                message=(
                    "POST /favorite-routes failed constraint=favorite_routes_route_option_check "
                    "SQLState 23514 route_option=FASTEST"
                ),
            )
        ]

        analysis = MODULE.build_rule_analysis(dev_report, prod_report)

        self.assertIn("/favorite-routes", analysis.next_action)
        self.assertIn("route_option", analysis.next_action)
        self.assertIn("favorite_routes", analysis.next_action)
        self.assertIn("check constraint", analysis.next_action)
        self.assertIn("enum/validation", analysis.next_action)

        rendered = MODULE.render_environment_mattermost(
            now=MODULE.utcnow(),
            lookback_minutes=60,
            report=dev_report,
            peer_report=prod_report,
            analysis=analysis,
        )
        self.assertIn("##### :memo: 요약", rendered)
        self.assertIn("favorite_routes_route_option_check", rendered)
        self.assertIn("route_option", rendered)
        self.assertIn("DB check constraint", rendered)

    def test_load_manifest_returns_none_when_path_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing.json"
            self.assertIsNone(MODULE.load_manifest(missing))

    def test_maybe_generate_agent_analysis_supports_anthropic_gms_json(self):
        fallback = MODULE.EnvAnalysis(
            conclusion="fallback conclusion",
            evidence="fallback evidence",
            impact="fallback impact",
            next_action="fallback action",
            confidence="low",
            source="rule",
        )
        with mock.patch.object(
            MODULE,
            "json_request",
            return_value={
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps(
                            {
                                "conclusion": "prod backend 경고가 집중됩니다.",
                                "evidence": "warning/error 12건 중 backend 10건입니다.",
                                "impact": "prod라 사용자 영향 가능성이 있습니다.",
                                "next_action": "backend 최근 배포와 대표 패턴을 확인합니다.",
                                "confidence": "high",
                            },
                            ensure_ascii=False,
                        ),
                    }
                ]
            },
        ) as mocked_request:
            analysis = MODULE.maybe_generate_agent_analysis(
                enabled=True,
                api_key="test-key",
                provider="anthropic-gms",
                base_url="https://internal-llm-gateway.example.com/gmsapi/api.anthropic.com",
                model="claude-opus-4-5-20251101",
                max_tokens=700,
                context={"prod": {"current_count": 12}},
                fallback_analysis=fallback,
            )

        self.assertEqual("prod backend 경고가 집중됩니다.", analysis.conclusion)
        self.assertEqual("warning/error 12건 중 backend 10건입니다.", analysis.evidence)
        self.assertEqual("high", analysis.confidence)
        called_url = mocked_request.call_args.args[0]
        called_headers = mocked_request.call_args.kwargs["headers"]
        called_payload = mocked_request.call_args.kwargs["payload"]
        self.assertEqual("https://internal-llm-gateway.example.com/gmsapi/api.anthropic.com/v1/messages", called_url)
        self.assertEqual("test-key", called_headers["x-api-key"])
        self.assertEqual("claude-opus-4-5-20251101", called_payload["model"])
        self.assertIsInstance(called_payload["messages"][0]["content"], str)
        self.assertIn('"current_count": 12', called_payload["messages"][0]["content"])
        self.assertIn("JSON", called_payload["messages"][0]["content"])
        self.assertIn("conclusion", called_payload["messages"][0]["content"])

    def test_maybe_generate_agent_analysis_uses_fallback_on_gms_request_failure(self):
        fallback = MODULE.EnvAnalysis(
            conclusion="rule conclusion",
            evidence="rule evidence",
            impact="rule impact",
            next_action="rule action",
            confidence="medium",
            source="rule",
        )
        with mock.patch.object(
            MODULE,
            "json_request",
            side_effect=ValueError("bad gateway"),
        ):
            analysis = MODULE.maybe_generate_agent_analysis(
                enabled=True,
                api_key="test-key",
                provider="anthropic-gms",
                base_url="https://internal-llm-gateway.example.com/gmsapi/api.anthropic.com",
                model="claude-opus-4-5-20251101",
                max_tokens=700,
                context={"target_environment": "prod"},
                fallback_analysis=fallback,
            )

        self.assertEqual("rule conclusion", analysis.conclusion)
        self.assertEqual("rule evidence", analysis.evidence)
        self.assertEqual("agent-error", analysis.source)
        self.assertEqual("low", analysis.confidence)

    def test_maybe_generate_agent_analysis_uses_fallback_on_malformed_provider_envelope(self):
        fallback = MODULE.EnvAnalysis(
            conclusion="rule conclusion",
            evidence="rule evidence",
            impact="rule impact",
            next_action="rule action",
            confidence="medium",
            source="rule",
        )
        with mock.patch.object(MODULE, "json_request", return_value={"content": "not-a-list"}):
            analysis = MODULE.maybe_generate_agent_analysis(
                enabled=True,
                api_key="test-key",
                provider="anthropic-gms",
                base_url="https://internal-llm-gateway.example.com/gmsapi/api.anthropic.com",
                model="claude-opus-4-5-20251101",
                max_tokens=700,
                context={"target_environment": "prod"},
                fallback_analysis=fallback,
            )

        self.assertEqual("rule conclusion", analysis.conclusion)
        self.assertEqual("rule impact", analysis.impact)
        self.assertEqual("agent-error", analysis.source)
        self.assertEqual("low", analysis.confidence)

    def test_build_agent_context_omits_full_duplicated_env_reports(self):
        prod_report = self.sample_report("prod", 12, 3)
        dev_report = self.sample_report("dev", 4, 1)
        analysis = MODULE.build_rule_analysis(prod_report, dev_report)

        context = MODULE.build_agent_context(
            generated_at="2026-05-14T11:00:00+09:00",
            lookback_minutes=60,
            target_report=prod_report,
            peer_report=dev_report,
            rule_analysis=analysis,
            project_context={"service": "busan-eumgil"},
        )

        self.assertEqual({"target", "peer", "rule_analysis", "project_context"}, set(context))
        self.assertNotIn("prod", context)
        self.assertNotIn("dev", context)
        self.assertEqual("prod", context["target"]["environment"])
        self.assertEqual(60, context["target"]["lookback_minutes"])
        self.assertEqual("dev", context["peer"]["environment"])
        self.assertNotIn("sample_logs", context["peer"])
        self.assertIn("current_count", context["peer"])

    def test_parse_agent_analysis_falls_back_when_agent_returns_prose(self):
        fallback = MODULE.EnvAnalysis(
            conclusion="rule conclusion",
            evidence="rule evidence",
            impact="rule impact",
            next_action="rule action",
            confidence="medium",
            source="rule",
        )

        analysis = MODULE.parse_agent_analysis("prod 쪽이 더 의심됩니다.", fallback)

        self.assertEqual("prod 쪽이 더 의심됩니다.", analysis.conclusion)
        self.assertEqual("rule evidence", analysis.evidence)
        self.assertEqual("rule impact", analysis.impact)
        self.assertEqual("rule action", analysis.next_action)
        self.assertEqual("low", analysis.confidence)

    def test_is_benign_domain_warning_identifies_graphhopper_no_route(self):
        sample = MODULE.LogSample(
            service="backend",
            level="warn",
            timestamp="2026-05-14T01:12:00+09:00",
            message="external route call failed provider=graphhopper operation=route status=400 BAD_REQUEST body={\"message\":\"Connection between locations not found\"}",
        )

        self.assertTrue(MODULE.is_benign_domain_warning("dev", sample))

    def test_build_environment_summary_can_use_total_log_context_without_flagging_issue(self):
        dev_report = self.sample_report("dev", 3, 3)
        dev_report.total_logs_current = 12000
        dev_report.total_logs_previous = 11800
        dev_report.total_logs_delta = 200
        dev_report.warning_error_ratio = dev_report.current_count / dev_report.total_logs_current
        prod_report = self.sample_report("prod", 0, 0)
        prod_report.total_logs_current = 15000
        prod_report.total_logs_previous = 14900
        prod_report.total_logs_delta = 100

        summary = MODULE.build_environment_summary(dev_report, prod_report)

        self.assertIn("큰 이상 징후", summary)

    def test_should_generate_agent_summary_for_skips_clean_environment(self):
        prod_report = self.sample_report("prod", 0, 0)
        prod_report.top_services = []
        prod_report.down_targets = []

        self.assertFalse(MODULE.should_generate_agent_summary_for(prod_report))

    def test_should_generate_agent_summary_for_keeps_warning_environment(self):
        dev_report = self.sample_report("dev", 2, 0)

        self.assertTrue(MODULE.should_generate_agent_summary_for(dev_report))

    def test_write_release_manifest_script_outputs_expected_json(self):
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "prod-release.json"
            subprocess.run(
                [
                    sys.executable,
                    str(WRITE_MANIFEST_PATH),
                    "--output",
                    str(output_path),
                    "--environment",
                    "prod",
                    "--branch",
                    "master",
                    "--commit",
                    "abc123def456",
                    "--deployed-at",
                    "2026-05-13T04:00:00Z",
                    "--build-number",
                    "15",
                    "--build-url",
                    "https://jenkins.example/job/15",
                    "--services",
                    "backend",
                    "ai",
                    "graphhopper-blue",
                    "graphhopper-green",
                    "--metadata",
                    "pipeline=e102-prod-deploy",
                    "rollback=false",
                ],
                check=True,
                capture_output=True,
                text=True,
            )

            payload = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual("prod", payload["environment"])
            self.assertEqual("master", payload["branch"])
            self.assertEqual("abc123def456", payload["commit"])
            self.assertEqual(["backend", "ai", "graphhopper-blue", "graphhopper-green"], payload["services"])
            self.assertEqual("e102-prod-deploy", payload["metadata"]["pipeline"])


if __name__ == "__main__":
    unittest.main()
