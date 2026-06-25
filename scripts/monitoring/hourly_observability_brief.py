#!/usr/bin/env python3
"""dev/prod warning-error hourly observability brief generator."""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime, timedelta
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any
from urllib import error as urllib_error
from urllib import parse, request


HEALTH_TARGETS: dict[str, list[str]] = {
    "dev": ["api", "db", "redis", "ai", "graphhopper", "minio"],
    "prod": ["api", "db", "redis", "ai", "admin", "graphhopper"],
}

SERVICE_REGEX: dict[str, str] = {
    "dev": "backend|ai|graphhopper|minio|admin",
    "prod": "backend|ai|graphhopper|admin",
}

LEVEL_REGEX = "warn|warning|error|fatal|critical"
Loki_WARNING_PATTERN = (
    r"(?:^|[[:space:]])(?:level|lvl|severity)="
    r"(?:warn|warning|error|fatal|critical|WARN|WARNING|ERROR|FATAL|CRITICAL)(?:[[:space:],]|$)"
    r"|^[WEFwef][0-9]{4}[[:space:]]"
    r"|^(?:[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[T ][^[:space:]]+)?[[:space:]]+)?"
    r"(?:WARN|WARNING|ERROR|FATAL|CRITICAL|warn|warning|error|fatal|critical)(?:[[:space:],]|$)"
)
BENIGN_DOMAIN_PATTERNS: dict[str, list[str]] = {
    "dev": [
        r"Connection between locations not found",
        r"ConnectionNotFoundException",
    ],
    "prod": [
        r"Connection between locations not found",
        r"ConnectionNotFoundException",
    ],
}
KST = datetime.now().astimezone().tzinfo or UTC
MESSAGE_SECTION_BREAK = "ㅤ"
MATTERMOST_EMOJI_PATTERN = re.compile(r":[a-z0-9_+-]+:\s*", re.IGNORECASE)


@dataclass
class DeployManifest:
    environment: str
    branch: str
    commit: str
    deployed_at: str
    build_number: str = "-"
    build_url: str = "-"
    services: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class GitRefItem:
    kind: str
    ref: str
    title: str
    web_url: str
    merged_or_created_at: str


@dataclass
class LogSample:
    service: str
    level: str
    timestamp: str
    message: str


@dataclass
class EnvReport:
    environment: str
    current_count: int
    previous_count: int
    delta: int
    total_logs_current: int
    total_logs_previous: int
    total_logs_delta: int
    warning_error_ratio: float
    suppressed_count: int
    top_services: list[tuple[str, int]]
    top_total_services: list[tuple[str, int]]
    health: dict[str, str]
    down_targets: list[str]
    top_patterns: list[tuple[str, int]]
    sample_logs: list[LogSample]
    deploy_manifest: DeployManifest | None
    recent_changes: list[GitRefItem]
    notes: list[str]


@dataclass
class EnvAnalysis:
    conclusion: str
    evidence: str
    impact: str
    next_action: str
    confidence: str = "medium"
    source: str = "rule"


def parse_bool(value: str | None, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def parse_iso8601(value: str | None) -> datetime | None:
    if not value:
        return None
    normalized = value.strip().replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError:
        return None


def utcnow() -> datetime:
    return datetime.now(tz=UTC)


def read_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def load_project_context(path: Path) -> dict[str, Any] | None:
    return read_json(path)


def json_request(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    payload: dict[str, Any] | None = None,
    timeout: int = 20,
) -> dict[str, Any]:
    encoded = None
    req_headers = dict(headers or {})
    if payload is not None:
        encoded = json.dumps(payload).encode("utf-8")
        req_headers["Content-Type"] = "application/json"
    req = request.Request(url, data=encoded, headers=req_headers, method=method)
    with request.urlopen(req, timeout=timeout) as response:
        body = response.read().decode("utf-8")
        if not body.strip():
            raise ValueError(f"empty JSON response from {url}")
        return json.loads(body)


def post_json_request(
    url: str,
    *,
    payload: dict[str, Any],
    headers: dict[str, str] | None = None,
    timeout: int = 20,
) -> str:
    req_headers = dict(headers or {})
    req_headers["Content-Type"] = "application/json"
    encoded = json.dumps(payload).encode("utf-8")
    req = request.Request(url, data=encoded, headers=req_headers, method="POST")
    with request.urlopen(req, timeout=timeout) as response:
        return response.read().decode("utf-8")


def query_prometheus_value(prometheus_url: str, query: str) -> float:
    params = parse.urlencode({"query": query})
    data = json_request(f"{prometheus_url.rstrip('/')}/api/v1/query?{params}")
    results = data.get("data", {}).get("result", [])
    if not results:
        return 0.0
    value = results[0].get("value", [None, "0"])[1]
    return float(value)


def query_loki_instant(loki_url: str, query: str, now: datetime) -> list[dict[str, Any]]:
    params = parse.urlencode(
        {
            "query": query,
            "time": str(int(now.timestamp() * 1_000_000_000)),
        }
    )
    data = json_request(f"{loki_url.rstrip('/')}/loki/api/v1/query?{params}")
    return data.get("data", {}).get("result", [])


def query_loki_logs(
    loki_url: str,
    selector: str,
    *,
    start: datetime,
    end: datetime,
    limit: int = 30,
) -> list[dict[str, Any]]:
    params = parse.urlencode(
        {
            "query": selector,
            "start": str(int(start.timestamp() * 1_000_000_000)),
            "end": str(int(end.timestamp() * 1_000_000_000)),
            "limit": str(limit),
            "direction": "BACKWARD",
        }
    )
    data = json_request(f"{loki_url.rstrip('/')}/loki/api/v1/query_range?{params}")
    return data.get("data", {}).get("result", [])


def selector_for(environment: str) -> str:
    return "{" f'environment="{environment}",' f'service_name=~"{SERVICE_REGEX[environment]}"' "}"


def warning_selector_for(environment: str, *, include_benign: bool) -> str:
    selector = f'{selector_for(environment)} |~ "{Loki_WARNING_PATTERN}"'
    if include_benign:
        return selector
    for pattern in BENIGN_DOMAIN_PATTERNS.get(environment, []):
        selector += f' !~ "{pattern}"'
    return selector


def count_query(selector: str, window: str, *, group_by: str | None = None, offset: str | None = None) -> str:
    offset_expr = f" offset {offset}" if offset else ""
    base = f"count_over_time({selector}[{window}]{offset_expr})"
    if group_by:
        return f"sum by ({group_by}) ({base})"
    return f"sum({base})"


def flatten_log_streams(streams: list[dict[str, Any]]) -> list[LogSample]:
    samples: list[LogSample] = []
    for stream in streams:
        labels = stream.get("stream", {})
        service = labels.get("service_name") or labels.get("compose_service") or "unknown"
        level = (labels.get("level") or "unknown").lower()
        for raw_ts, message in stream.get("values", []):
            timestamp = datetime.fromtimestamp(int(raw_ts) / 1_000_000_000, tz=UTC).astimezone(KST).isoformat()
            samples.append(
                LogSample(
                    service=service,
                    level=level,
                    timestamp=timestamp,
                    message=message.strip(),
                )
            )
    samples.sort(key=lambda item: item.timestamp, reverse=True)
    return samples


def is_benign_domain_warning(environment: str, sample: LogSample) -> bool:
    patterns = BENIGN_DOMAIN_PATTERNS.get(environment, [])
    return any(re.search(pattern, sample.message, flags=re.I) for pattern in patterns)


def normalize_log_message(message: str) -> str:
    normalized = message
    normalized = re.sub(r"\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\b", "<ts>", normalized)
    normalized = re.sub(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", "<uuid>", normalized, flags=re.I)
    normalized = re.sub(r"\b0x[0-9a-f]+\b", "<hex>", normalized, flags=re.I)
    normalized = re.sub(r"\b[0-9a-f]{12,64}\b", "<hex>", normalized, flags=re.I)
    normalized = re.sub(r"\b\d+\b", "<n>", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    return normalized


def top_patterns(samples: list[LogSample], limit: int = 3) -> list[tuple[str, int]]:
    counter: Counter[str] = Counter()
    for sample in samples:
        counter[normalize_log_message(sample.message)] += 1
    return counter.most_common(limit)


def load_manifest(path: Path) -> DeployManifest | None:
    data = read_json(path)
    if not data:
        return None
    return DeployManifest(
        environment=data.get("environment", "unknown"),
        branch=data.get("branch", "-"),
        commit=data.get("commit", "-"),
        deployed_at=data.get("deployed_at", "-"),
        build_number=data.get("build_number", "-"),
        build_url=data.get("build_url", "-"),
        services=list(data.get("services", [])),
        metadata=dict(data.get("metadata", {})),
    )


def gitlab_headers(token: str | None) -> dict[str, str]:
    if not token:
        return {}
    return {"PRIVATE-TOKEN": token}


def fetch_gitlab_commits(
    *,
    gitlab_base_url: str,
    project_path: str,
    token: str | None,
    branch: str,
    since: datetime,
    limit: int = 3,
) -> list[GitRefItem]:
    if not token:
        return []
    encoded_project = parse.quote(project_path, safe="")
    params = parse.urlencode(
        {
            "ref_name": branch,
            "since": since.astimezone(UTC).isoformat().replace("+00:00", "Z"),
            "per_page": str(limit),
        }
    )
    data = json_request(
        f"{gitlab_base_url.rstrip('/')}/api/v4/projects/{encoded_project}/repository/commits?{params}",
        headers=gitlab_headers(token),
    )
    return [
        GitRefItem(
            kind="commit",
            ref=item.get("short_id", "-"),
            title=item.get("title", "").strip(),
            web_url=item.get("web_url", ""),
            merged_or_created_at=item.get("created_at", ""),
        )
        for item in data[:limit]
    ]


def fetch_local_commits(
    *,
    gitlab_base_url: str,
    project_path: str,
    branch: str,
    since: datetime,
    limit: int = 3,
) -> list[GitRefItem]:
    repo_root = Path(__file__).resolve().parents[2]
    since_arg = since.astimezone(UTC).isoformat().replace("+00:00", "Z")
    refs = [f"origin/{branch}", branch]
    last_error: str | None = None

    for ref in refs:
        try:
            output = subprocess.check_output(
                [
                    "git",
                    "-C",
                    str(repo_root),
                    "log",
                    ref,
                    f"--since={since_arg}",
                    f"--max-count={limit}",
                    "--pretty=format:%H%x09%h%x09%s%x09%cI",
                ],
                text=True,
                encoding="utf-8",
                errors="replace",
                stderr=subprocess.STDOUT,
            ).strip()
        except (OSError, subprocess.CalledProcessError) as exc:
            last_error = str(exc)
            continue

        if not output:
            continue

        commits: list[GitRefItem] = []
        for line in output.splitlines():
            full_sha, short_sha, subject, committed_at = line.split("\t", 3)
            commits.append(
                GitRefItem(
                    kind="commit",
                    ref=short_sha,
                    title=subject.strip(),
                    web_url=f"{gitlab_base_url.rstrip('/')}/{project_path}/-/commit/{full_sha}",
                    merged_or_created_at=committed_at,
                )
            )
        return commits[:limit]

    if last_error:
        raise RuntimeError(f"local git history lookup failed: {last_error}")
    return []


def fetch_gitlab_merge_requests(
    *,
    gitlab_base_url: str,
    project_path: str,
    token: str | None,
    branch: str,
    since: datetime,
    limit: int = 3,
) -> list[GitRefItem]:
    if not token:
        return []
    encoded_project = parse.quote(project_path, safe="")
    params = parse.urlencode(
        {
            "state": "merged",
            "target_branch": branch,
            "updated_after": since.astimezone(UTC).isoformat().replace("+00:00", "Z"),
            "order_by": "updated_at",
            "sort": "desc",
            "per_page": str(limit),
        }
    )
    data = json_request(
        f"{gitlab_base_url.rstrip('/')}/api/v4/projects/{encoded_project}/merge_requests?{params}",
        headers=gitlab_headers(token),
    )
    items: list[GitRefItem] = []
    for item in data:
        merged_at = parse_iso8601(item.get("merged_at"))
        if merged_at and merged_at >= since:
            items.append(
                GitRefItem(
                    kind="mr",
                    ref=f"!{item.get('iid', '-')}",
                    title=item.get("title", "").strip(),
                    web_url=item.get("web_url", ""),
                    merged_or_created_at=item.get("merged_at", ""),
                )
            )
    return items[:limit]


def format_status(value: float) -> str:
    return "UP" if value >= 1.0 else "DOWN"


def collect_health(prometheus_url: str, environment: str) -> tuple[dict[str, str], list[str]]:
    job = "blackbox-dev-http" if environment == "dev" else "blackbox-prod-http"
    statuses: dict[str, str] = {}
    down_targets: list[str] = []
    for target_name in HEALTH_TARGETS[environment]:
        query = f'max(probe_success{{job="{job}", target_name="{target_name}"}}) or vector(0)'
        value = query_prometheus_value(prometheus_url, query)
        status = format_status(value)
        statuses[target_name] = status
        if status != "UP":
            down_targets.append(target_name)
    return statuses, down_targets


def parse_loki_scalar(results: list[dict[str, Any]]) -> int:
    if not results:
        return 0
    raw_value = results[0].get("value", [None, "0"])[1]
    return int(float(raw_value))


def parse_loki_vector(results: list[dict[str, Any]], label: str) -> list[tuple[str, int]]:
    parsed: list[tuple[str, int]] = []
    for row in results:
        label_value = row.get("metric", {}).get(label, "unknown")
        raw_value = row.get("value", [None, "0"])[1]
        parsed.append((label_value, int(float(raw_value))))
    parsed.sort(key=lambda item: item[1], reverse=True)
    return parsed


def build_env_report(
    *,
    environment: str,
    loki_url: str,
    prometheus_url: str,
    now: datetime,
    lookback_minutes: int,
    gitlab_base_url: str,
    gitlab_project_path: str,
    gitlab_token: str | None,
    manifest_path: Path,
) -> EnvReport:
    total_selector = selector_for(environment)
    warning_selector = warning_selector_for(environment, include_benign=False)
    all_warning_selector = warning_selector_for(environment, include_benign=True)
    current_window = f"{lookback_minutes}m"
    previous_offset = current_window
    notes: list[str] = []

    try:
        total_current_results = query_loki_instant(loki_url, count_query(total_selector, current_window), now)
        total_previous_results = query_loki_instant(loki_url, count_query(total_selector, current_window, offset=previous_offset), now)
        total_service_results = query_loki_instant(loki_url, count_query(total_selector, current_window, group_by="service_name"), now)
        current_results = query_loki_instant(loki_url, count_query(warning_selector, current_window), now)
        previous_results = query_loki_instant(loki_url, count_query(warning_selector, current_window, offset=previous_offset), now)
        service_results = query_loki_instant(loki_url, count_query(warning_selector, current_window, group_by="service_name"), now)
        suppressed_results = query_loki_instant(
            loki_url,
            f"sum(count_over_time(({all_warning_selector})[{current_window}])) - sum(count_over_time(({warning_selector})[{current_window}]))",
            now,
        )
        logs = flatten_log_streams(
            query_loki_logs(
                loki_url,
                warning_selector,
                start=now - timedelta(minutes=lookback_minutes),
                end=now,
                limit=40,
            )
        )
    except (urllib_error.URLError, TimeoutError, KeyError, ValueError) as exc:
        total_current_results = []
        total_previous_results = []
        total_service_results = []
        current_results = []
        previous_results = []
        service_results = []
        suppressed_results = []
        logs = []
        notes.append(f"Loki query failed: {exc}")

    try:
        health, down_targets = collect_health(prometheus_url, environment)
    except (urllib_error.URLError, TimeoutError, KeyError, ValueError) as exc:
        health = {target: "UNKNOWN" for target in HEALTH_TARGETS[environment]}
        down_targets = []
        notes.append(f"Prometheus query failed: {exc}")

    try:
        manifest = load_manifest(manifest_path)
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        manifest = None
        notes.append(f"Release manifest parse failed: {exc}")

    compare_branch = "develop" if environment == "dev" else "master"
    recent_since = now - timedelta(hours=12)
    if manifest:
        deployed_at = parse_iso8601(manifest.deployed_at)
        if deployed_at:
            recent_since = max(now - timedelta(days=3), deployed_at.astimezone(UTC) - timedelta(minutes=30))

    recent_changes: list[GitRefItem] = []
    try:
        recent_changes = fetch_gitlab_merge_requests(
            gitlab_base_url=gitlab_base_url,
            project_path=gitlab_project_path,
            token=gitlab_token,
            branch=compare_branch,
            since=recent_since,
        )
        if len(recent_changes) < 3:
            recent_changes.extend(
                fetch_gitlab_commits(
                    gitlab_base_url=gitlab_base_url,
                    project_path=gitlab_project_path,
                    token=gitlab_token,
                    branch=compare_branch,
                    since=recent_since,
                    limit=3 - len(recent_changes),
                )
            )
    except (urllib_error.URLError, TimeoutError, KeyError, ValueError, RuntimeError) as exc:
        notes.append(f"GitLab API query failed: {exc}")

    if len(recent_changes) < 3:
        try:
            local_commits = fetch_local_commits(
                gitlab_base_url=gitlab_base_url,
                project_path=gitlab_project_path,
                branch=compare_branch,
                since=recent_since,
                limit=3,
            )
            existing_refs = {item.ref for item in recent_changes}
            for item in local_commits:
                if item.ref not in existing_refs:
                    recent_changes.append(item)
        except RuntimeError as exc:
            notes.append(str(exc))

    total_logs_current = parse_loki_scalar(total_current_results)
    total_logs_previous = parse_loki_scalar(total_previous_results)
    current_count = parse_loki_scalar(current_results)
    previous_count = parse_loki_scalar(previous_results)
    suppressed_count = max(0, parse_loki_scalar(suppressed_results))
    top_service_counts = parse_loki_vector(service_results, "service_name")
    top_total_service_counts = parse_loki_vector(total_service_results, "service_name")
    warning_error_ratio = (current_count / total_logs_current) if total_logs_current > 0 else 0.0
    if total_logs_current <= 0 or not top_total_service_counts:
        notes.append(
            "Loki 전체 로그 유입이 0건입니다. Promtail 라벨/수집 중단 또는 실제 무트래픽 여부를 확인해야 합니다."
        )
    if suppressed_count > 0:
        notes.append(
            f"운영성 낮은 도메인 경고 {suppressed_count}건은 메인 경고 집계에서 제외했습니다. "
            "예: GraphHopper no-route"
        )
    if manifest is None:
        notes.append("배포 manifest가 없어 최근 배포 기반 상관관계는 약합니다.")
    if not gitlab_token:
        notes.append("GitLab API token이 없어 MR 정보는 생략하고 로컬 git commit 기준으로 보강했습니다.")

    return EnvReport(
        environment=environment,
        current_count=current_count,
        previous_count=previous_count,
        delta=current_count - previous_count,
        total_logs_current=total_logs_current,
        total_logs_previous=total_logs_previous,
        total_logs_delta=total_logs_current - total_logs_previous,
        warning_error_ratio=warning_error_ratio,
        suppressed_count=suppressed_count,
        top_services=top_service_counts[:3],
        top_total_services=top_total_service_counts[:3],
        health=health,
        down_targets=down_targets,
        top_patterns=top_patterns(logs),
        sample_logs=logs[:3],
        deploy_manifest=manifest,
        recent_changes=recent_changes[:3],
        notes=notes,
    )


def describe_env(report: EnvReport) -> tuple[str, list[str]]:
    severity = "normal"
    reasons: list[str] = []
    observability_failures = [note for note in report.notes if note.startswith("Loki query failed") or note.startswith("Prometheus query failed")]
    if observability_failures:
        severity = "issue"
        reasons.append("관측 스택 데이터 조회 실패")
    elif has_log_ingestion_gap(report):
        severity = "issue"
        reasons.append("Loki 로그 유입 공백")
    if report.down_targets:
        severity = "issue"
        reasons.append(f"health DOWN: {', '.join(report.down_targets)}")
    if report.current_count >= 20 and report.delta >= 10:
        severity = "issue"
        reasons.append(f"warning/error {report.current_count}건, 직전 대비 {report.delta:+d}")
    elif report.current_count >= 5 or report.delta > 0:
        severity = "watch"
        reasons.append(f"warning/error {report.current_count}건")

    if report.top_services and report.current_count > 0:
        top_service, top_count = report.top_services[0]
        share = top_count / max(report.current_count, 1)
        if top_count >= 5 and share >= 0.6:
            reasons.append(f"{top_service} 비중 {share:.0%}")

    deployed_at = parse_iso8601(report.deploy_manifest.deployed_at) if report.deploy_manifest else None
    if deployed_at and deployed_at >= utcnow() - timedelta(hours=2) and report.current_count > max(5, report.previous_count):
        reasons.append("최근 배포 이후 증가 가능성")

    return severity, reasons


def build_rule_summary(dev_report: EnvReport, prod_report: EnvReport) -> str:
    dev_severity, dev_reasons = describe_env(dev_report)
    prod_severity, prod_reasons = describe_env(prod_report)
    severity_rank = {"normal": 0, "watch": 1, "issue": 2}

    if severity_rank[prod_severity] > severity_rank[dev_severity]:
        prefix = "prod에서 반복 경고가 감지됩니다." if prod_severity == "watch" else "prod 쪽에 문제가 있어 보입니다."
        return f"{prefix} 근거: {', '.join(prod_reasons[:3]) or '로그/health 이상 징후'}."
    if severity_rank[dev_severity] > severity_rank[prod_severity]:
        prefix = "dev에서 반복 경고가 감지됩니다." if dev_severity == "watch" else "dev 쪽에 문제가 있어 보입니다."
        return f"{prefix} 근거: {', '.join(dev_reasons[:3]) or '로그/health 이상 징후'}."

    if prod_report.current_count == 0 and dev_report.current_count == 0 and not prod_report.down_targets and not dev_report.down_targets:
        return "지난 1시간 동안 dev/prod 모두 뚜렷한 warning/error 급증이나 health 이상은 적습니다."

    if prod_report.current_count >= dev_report.current_count:
        return f"prod 로그가 더 많이 잡힙니다. prod {prod_report.current_count}건, dev {dev_report.current_count}건입니다."
    return f"dev 로그가 더 많이 잡힙니다. dev {dev_report.current_count}건, prod {prod_report.current_count}건입니다."


def build_environment_summary(target_report: EnvReport, peer_report: EnvReport) -> str:
    severity, reasons = describe_env(target_report)
    severity_rank = {"normal": 0, "watch": 1, "issue": 2}
    peer_severity, _ = describe_env(peer_report)

    if severity == "issue":
        return (
            f"{target_report.environment} 쪽에 문제가 있어 보입니다. "
            f"근거: {', '.join(reasons[:3]) or 'warning/error 또는 health 이상'}."
        )
    if severity == "watch":
        return (
            f"{target_report.environment} 쪽에 반복 경고가 감지됩니다. "
            f"근거: {', '.join(reasons[:3]) or '로그 증가 징후'}."
        )
    if severity_rank[peer_severity] > severity_rank[severity]:
        return (
            f"{target_report.environment} 쪽은 비교적 안정적이고, "
            f"{peer_report.environment} 쪽 반복 패턴을 먼저 확인해야 합니다."
        )
    return f"{target_report.environment} 쪽은 지난 1시간 기준으로 큰 이상 징후가 두드러지지 않습니다."


def format_ratio(value: float) -> str:
    return f"{value * 100:.2f}%"


def format_delta(value: int) -> str:
    return f"{value:+d}"


def top_service_detail(report: EnvReport) -> str | None:
    if not report.top_services or report.current_count <= 0:
        return None
    service, count = report.top_services[0]
    share = count / max(report.current_count, 1)
    return f"{service} {count}건({share:.0%})"


def health_impact_summary(report: EnvReport) -> str:
    if report.down_targets:
        return f"health DOWN({', '.join(report.down_targets)})"
    if report.health and all(status == "UP" for status in report.health.values()):
        return "health 모두 UP"
    if report.health:
        unknown_targets = [target for target, status in report.health.items() if status != "UP"]
        return f"health 확인 제한({', '.join(unknown_targets)})"
    return "health 데이터 없음"


def confidence_from_report(report: EnvReport, severity: str) -> str:
    if has_observability_gap(report):
        return "low"
    if severity == "normal" and report.health and all(status == "UP" for status in report.health.values()):
        return "high"
    return "medium"


def actionable_signal_text(report: EnvReport) -> str:
    pattern_text = " ".join(pattern for pattern, _ in report.top_patterns)
    sample_text = " ".join(sample.message for sample in report.sample_logs)
    return f"{pattern_text} {sample_text}"


def known_backend_next_action(report: EnvReport) -> str | None:
    signal_text = actionable_signal_text(report)
    signal_text_lower = signal_text.lower()
    has_favorite_route_constraint_signal = (
        "favorite_routes_route_option_check" in signal_text_lower
        or "/favorite-routes" in signal_text_lower
        or re.search(r"sqlstate\D*23514\b", signal_text_lower) is not None
    )
    if has_favorite_route_constraint_signal:
        return (
            "POST /favorite-routes 요청 payload의 route_option 값을 favorite_routes DB check constraint "
            "favorite_routes_route_option_check와 관련 enum/validation mapping에 맞춰 검증합니다."
        )
    return None


def build_rule_analysis(target_report: EnvReport, peer_report: EnvReport) -> EnvAnalysis:
    severity, reasons = describe_env(target_report)
    peer_severity, _ = describe_env(peer_report)
    severity_rank = {"normal": 0, "watch": 1, "issue": 2}
    service_detail = top_service_detail(target_report)
    pattern_detail = target_report.top_patterns[0][0] if target_report.top_patterns else None
    evidence_parts = [
        (
            f"warning/error {target_report.current_count}건"
            f"(직전 {target_report.previous_count}건, {format_delta(target_report.delta)})"
        ),
        (
            f"전체 로그 {target_report.total_logs_current}건"
            f"(직전 {target_report.total_logs_previous}건, {format_delta(target_report.total_logs_delta)})"
        ),
        f"경고 비율 {format_ratio(target_report.warning_error_ratio)}",
        f"{peer_report.environment} warning/error {peer_report.current_count}건",
    ]
    if service_detail:
        evidence_parts.append(f"주요 서비스 {service_detail}")
    if pattern_detail:
        evidence_parts.append(f"대표 패턴 `{pattern_detail}`")
    if target_report.suppressed_count > 0:
        evidence_parts.append(f"운영성 낮은 도메인 경고 {target_report.suppressed_count}건 제외")
    if reasons:
        evidence_parts.append(f"판정 근거 {', '.join(reasons[:2])}")

    health_summary = health_impact_summary(target_report)
    confidence = confidence_from_report(target_report, severity)

    if has_observability_gap(target_report):
        if has_query_failure(target_report):
            conclusion = f"{target_report.environment} 관측 데이터 조회 실패로 서비스 상태 확인이 제한됩니다."
            next_action = "Loki/Prometheus 쿼리 경로와 인증 상태를 먼저 복구한 뒤 동일 시간대를 재조회합니다."
        else:
            conclusion = (
                f"{target_report.environment} Loki 로그 유입이 0건이라 정상으로 단정할 수 없습니다."
            )
            next_action = "Promtail target/label 설정과 Loki 수집 상태, 실제 서비스 트래픽 유입 여부를 먼저 확인합니다."
        impact = f"{health_summary}이나 로그 수집 공백 때문에 실제 영향 범위는 낮은 확신으로만 추정할 수 있습니다."
    elif target_report.down_targets:
        conclusion = (
            f"{target_report.environment} {', '.join(target_report.down_targets)} health DOWN이 확인되어 "
            "가용성 문제를 우선 확인해야 합니다."
        )
        impact = f"{health_summary} 상태라 해당 타깃 의존 API의 사용자 영향 가능성이 높습니다."
        next_action = f"{', '.join(target_report.down_targets)} 컨테이너/타깃 health check와 최근 배포 변경을 먼저 대조합니다."
    elif severity == "issue":
        owner = service_detail or "서비스"
        conclusion = f"{target_report.environment} {owner} warning/error 급증을 우선 확인해야 합니다."
        impact = (
            f"{health_summary}이나 경고 비율 {format_ratio(target_report.warning_error_ratio)}와 "
            f"{format_delta(target_report.delta)}건 변화 때문에 장애 전조 가능성이 있습니다."
        )
        next_action = "대표 패턴과 최근 배포 commit/MR을 대조하고 같은 서비스의 ERROR 원문을 시간순으로 확인합니다."
    elif severity == "watch":
        owner = service_detail or "서비스"
        conclusion = f"{target_report.environment} {owner} warning/error가 반복되어 같은 도메인 패턴을 확인해야 합니다."
        impact = (
            f"{health_summary} 기준 즉시 가용성 영향은 제한적으로 보이나, "
            f"{peer_report.environment} 대비 같은 서비스에 경고가 몰리는지 함께 봐야 합니다."
        )
        next_action = "대표 패턴을 서비스/도메인 단위로 묶고, 같은 API 호출에서 반복되는 원문 로그를 먼저 확인합니다."
    elif severity_rank[peer_severity] > severity_rank[severity]:
        conclusion = (
            f"{target_report.environment}는 큰 이상이 두드러지지 않으며 "
            f"{peer_report.environment} 반복 패턴을 먼저 확인해야 합니다."
        )
        impact = f"{health_summary}이고 warning/error가 낮아 현재 {target_report.environment} 사용자 영향 가능성은 낮습니다."
        next_action = f"{peer_report.environment} 조치 후 같은 시간대의 {target_report.environment} 로그/health 변화만 비교합니다."
    else:
        conclusion = f"{target_report.environment}는 지난 1시간 기준 뚜렷한 warning/error 급증이나 health 이상이 없습니다."
        impact = f"{health_summary}이고 경고 비율 {format_ratio(target_report.warning_error_ratio)}라 즉시 사용자 영향 가능성은 낮습니다."
        next_action = "추가 조치 없이 모니터링을 유지하되, 다음 브리프에서 warning/error 증가나 health 변화를 확인합니다."

    next_action = known_backend_next_action(target_report) or next_action

    return EnvAnalysis(
        conclusion=conclusion,
        evidence=", ".join(evidence_parts) + ".",
        impact=impact,
        next_action=next_action,
        confidence=confidence,
        source="rule",
    )


def compact_peer_context(report: EnvReport) -> dict[str, Any]:
    return {
        "environment": report.environment,
        "current_count": report.current_count,
        "previous_count": report.previous_count,
        "delta": report.delta,
        "total_logs_current": report.total_logs_current,
        "total_logs_previous": report.total_logs_previous,
        "total_logs_delta": report.total_logs_delta,
        "warning_error_ratio": report.warning_error_ratio,
        "suppressed_count": report.suppressed_count,
        "top_services": report.top_services,
        "top_total_services": report.top_total_services,
        "health": report.health,
        "down_targets": report.down_targets,
        "top_patterns": report.top_patterns,
        "notes": report.notes,
    }


def build_agent_context(
    *,
    generated_at: str,
    lookback_minutes: int,
    target_report: EnvReport,
    peer_report: EnvReport,
    rule_analysis: EnvAnalysis,
    project_context: dict[str, Any] | None,
) -> dict[str, Any]:
    target = asdict(target_report)
    target["generated_at"] = generated_at
    target["lookback_minutes"] = lookback_minutes
    return {
        "target": target,
        "peer": compact_peer_context(peer_report),
        "rule_analysis": asdict(rule_analysis),
        "project_context": project_context or {},
    }


def should_generate_agent_summary_for(report: EnvReport) -> bool:
    return bool(report.down_targets) or report.current_count > 0


def agent_error_fallback(fallback: EnvAnalysis) -> EnvAnalysis:
    return EnvAnalysis(
        conclusion=fallback.conclusion,
        evidence=fallback.evidence,
        impact=fallback.impact,
        next_action=fallback.next_action,
        confidence="low",
        source="agent-error",
    )


def sanitize_analysis_field(value: Any, fallback: str, *, limit: int = 360) -> str:
    if not isinstance(value, str):
        return fallback
    text = re.sub(r"\s+", " ", value).strip()
    if not text:
        return fallback
    if len(text) > limit:
        return text[: limit - 3].rstrip() + "..."
    return text


def normalize_confidence(value: Any, fallback: str = "medium") -> str:
    if not isinstance(value, str):
        return fallback if fallback in {"high", "medium", "low"} else "medium"
    normalized = value.strip().lower()
    return normalized if normalized in {"high", "medium", "low"} else fallback


def parse_json_object_from_text(text: str) -> dict[str, Any] | None:
    stripped = text.strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)```", stripped, flags=re.I | re.S)
    if fenced:
        stripped = fenced.group(1).strip()

    candidates = [stripped]
    start = stripped.find("{")
    end = stripped.rfind("}")
    if 0 <= start < end:
        candidates.append(stripped[start : end + 1])

    for candidate in candidates:
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            return parsed
    return None


def parse_agent_analysis(text: str, fallback: EnvAnalysis) -> EnvAnalysis:
    parsed = parse_json_object_from_text(text)
    if parsed is not None:
        return EnvAnalysis(
            conclusion=sanitize_analysis_field(parsed.get("conclusion"), fallback.conclusion),
            evidence=sanitize_analysis_field(parsed.get("evidence"), fallback.evidence),
            impact=sanitize_analysis_field(parsed.get("impact"), fallback.impact),
            next_action=sanitize_analysis_field(parsed.get("next_action"), fallback.next_action),
            confidence=normalize_confidence(parsed.get("confidence"), fallback.confidence),
            source="agent-json",
        )

    prose = sanitize_analysis_field(text, fallback.conclusion)
    return EnvAnalysis(
        conclusion=prose,
        evidence=fallback.evidence,
        impact=fallback.impact,
        next_action=fallback.next_action,
        confidence="low",
        source="agent-prose-fallback",
    )


def extract_anthropic_text(data: dict[str, Any]) -> str:
    content = data.get("content")
    if not isinstance(content, list):
        raise ValueError("malformed Anthropic response: content must be a list")
    text_parts: list[str] = []
    for item in content:
        if not isinstance(item, dict):
            raise ValueError("malformed Anthropic response: content item must be an object")
        if item.get("type") != "text":
            continue
        text = item.get("text")
        if not isinstance(text, str):
            raise ValueError("malformed Anthropic response: text must be a string")
        if text.strip():
            text_parts.append(text.strip())
    if not text_parts:
        raise ValueError("malformed Anthropic response: missing text content")
    return "\n".join(text_parts)


def extract_openai_text(data: dict[str, Any]) -> str:
    choices = data.get("choices")
    if not isinstance(choices, list) or not choices:
        raise ValueError("malformed chat response: choices must be a non-empty list")
    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        raise ValueError("malformed chat response: choice must be an object")
    message = first_choice.get("message")
    if not isinstance(message, dict):
        raise ValueError("malformed chat response: message must be an object")
    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ValueError("malformed chat response: missing message content")
    return content.strip()


def maybe_generate_agent_analysis(
    *,
    enabled: bool,
    api_key: str | None,
    provider: str,
    base_url: str,
    model: str,
    max_tokens: int,
    context: dict[str, Any],
    fallback_analysis: EnvAnalysis,
) -> EnvAnalysis | None:
    if not enabled or not api_key:
        return None
    prompt = (
        "당신은 운영 모니터링 분석 리포터입니다. "
        "아래 JSON을 보고 target.environment에 대한 사고 대응용 분석을 한국어로 작성하세요. "
        "warning/error 건수만 보지 말고 total_logs_current, total_logs_previous, warning_error_ratio, "
        "suppressed_count, down_targets, deploy_manifest, recent_changes를 함께 고려하세요. "
        "suppressed_count는 메인 경고에서 제외된 도메인 경고 수이며, notes에 benign/noise 설명이 있으면 운영 장애로 과장하지 마세요. "
        "top_patterns나 sample_logs에 favorite_routes_route_option_check, /favorite-routes, SQLState 23514가 있으면 "
        "/favorite-routes 요청 payload의 route_option 값을 favorite_routes DB check constraint와 enum/validation mapping에 대조하는 조치를 제안하세요. "
        "막연한 '추세 감시', '확인 권장' 대신 어떤 서비스/도메인에서 어떤 패턴이 반복되는지와 "
        "왜 그 도메인 문제가 의심되는지를 한 문장 안에 드러내세요. "
        "반드시 서비스/문제 중심 결론, 구체 수치 근거, 영향도, 다음 진단 조치를 분리하세요. "
        "응답은 다른 텍스트 없이 JSON 객체만 반환하세요. "
        '필드는 "conclusion", "evidence", "impact", "next_action", "confidence"만 사용하세요. '
        '"confidence"는 "high", "medium", "low" 중 하나입니다.'
    )
    provider_name = provider.strip().lower()
    user_context = json.dumps(context, ensure_ascii=False)

    try:
        if provider_name in {"anthropic", "anthropic-gms"}:
            endpoint = base_url.rstrip("/")
            if not endpoint.endswith("/v1/messages"):
                endpoint = f"{endpoint}/v1/messages"
            anthropic_user_prompt = f"{prompt}\n\n아래 JSON을 분석하세요.\n{user_context}"
            payload = {
                "model": model,
                "max_tokens": max_tokens,
                "temperature": 0.1,
                "messages": [
                    {
                        "role": "user",
                        "content": anthropic_user_prompt,
                    }
                ],
            }
            headers = {
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
            }
            data = json_request(
                endpoint,
                method="POST",
                headers=headers,
                payload=payload,
                timeout=25,
            )
            return parse_agent_analysis(extract_anthropic_text(data), fallback_analysis)

        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": prompt},
                {"role": "user", "content": user_context},
            ],
            "temperature": 0.1,
            "max_tokens": max_tokens,
        }
        headers = {"Authorization": f"Bearer {api_key}"}
        data = json_request(
            f"{base_url.rstrip('/')}/chat/completions",
            method="POST",
            headers=headers,
            payload=payload,
            timeout=25,
        )
        return parse_agent_analysis(extract_openai_text(data), fallback_analysis)
    except (
        urllib_error.URLError,
        TimeoutError,
        KeyError,
        ValueError,
        subprocess.CalledProcessError,
        AttributeError,
        IndexError,
        TypeError,
    ):
        return agent_error_fallback(fallback_analysis)


def maybe_generate_agent_summary(
    *,
    enabled: bool,
    api_key: str | None,
    provider: str,
    base_url: str,
    model: str,
    max_tokens: int,
    context: dict[str, Any],
) -> str | None:
    fallback = EnvAnalysis(
        conclusion="",
        evidence="",
        impact="",
        next_action="",
        confidence="low",
        source="compat",
    )
    analysis = maybe_generate_agent_analysis(
        enabled=enabled,
        api_key=api_key,
        provider=provider,
        base_url=base_url,
        model=model,
        max_tokens=max_tokens,
        context=context,
        fallback_analysis=fallback,
    )
    if not analysis:
        return None
    if analysis.source == "agent-json":
        return (
            f"{analysis.conclusion} "
            f"근거: {analysis.evidence} "
            f"영향: {analysis.impact} "
            f"조치: {analysis.next_action}"
        ).strip()
    return analysis.conclusion or None


def render_changes(items: list[GitRefItem]) -> list[str]:
    lines: list[str] = []
    for item in items:
        stamp = parse_iso8601(item.merged_or_created_at)
        stamp_text = stamp.astimezone(KST).strftime("%m-%d %H:%M") if stamp else item.merged_or_created_at
        lines.append(f"- `{item.ref}` {item.title} ({stamp_text})")
    return lines


def compact_text(value: str, limit: int = 180) -> str:
    normalized = re.sub(r"\s+", " ", value).strip()
    if len(normalized) <= limit:
        return normalized
    return normalized[: max(limit - 3, 0)].rstrip() + "..."


def health_text(report: EnvReport) -> str:
    return " / ".join(f"{target.upper()} {status}" for target, status in report.health.items()) or "-"


def service_counts_text(items: list[tuple[str, int]]) -> str:
    return ", ".join(f"{service} {count}" for service, count in items) or "-"


def has_favorite_routes_constraint_signal(report: EnvReport) -> bool:
    haystack = " ".join(
        [pattern for pattern, _ in report.top_patterns]
        + [sample.message for sample in report.sample_logs]
    ).lower()
    return (
        "favorite_routes_route_option_check" in haystack
        or "/favorite-routes" in haystack
        or "sqlstate 23514" in haystack
    ) and ("favorite" in haystack or "route_option" in haystack)


def favorite_routes_pattern_count(report: EnvReport) -> int | None:
    for pattern, count in report.top_patterns:
        lower_pattern = pattern.lower()
        if (
            "favorite_routes_route_option_check" in lower_pattern
            or "/favorite-routes" in lower_pattern
            or "sqlstate 23514" in lower_pattern
        ):
            return count
    return None


def has_observability_gap(report: EnvReport) -> bool:
    if any(status.upper() == "UNKNOWN" for status in report.health.values()):
        return True
    return has_query_failure(report) or has_log_ingestion_gap(report)


def has_query_failure(report: EnvReport) -> bool:
    return any(
        note.startswith("Loki query failed") or note.startswith("Prometheus query failed")
        for note in report.notes
    )


def has_log_ingestion_gap(report: EnvReport) -> bool:
    if any(note.startswith("Loki 전체 로그 유입이 0건") for note in report.notes):
        return True
    return report.total_logs_current <= 0 or not report.top_total_services


def render_metric_section(report: EnvReport) -> str:
    delta_sign = f"{report.delta:+d}"
    total_delta_sign = f"{report.total_logs_delta:+d}"
    warning_ratio_percent = f"{report.warning_error_ratio * 100:.2f}%"
    return (
        "##### :bar_chart: 지표\n"
        f"- warning/error: `{report.current_count}`건 (직전 1시간 `{report.previous_count}`건, `{delta_sign}`)\n"
        f"- 전체 로그: `{report.total_logs_current}`건 (직전 1시간 `{report.total_logs_previous}`건, `{total_delta_sign}`)\n"
        f"- 경고 비율: `{warning_ratio_percent}`\n"
        f"- 제외된 도메인 경고: `{report.suppressed_count}`건\n"
        f"- 상태: {health_text(report)}\n"
        f"- 경고 상위 서비스: {service_counts_text(report.top_services)}\n"
        f"- 전체 로그 상위 서비스: {service_counts_text(report.top_total_services)}"
    )


def clean_summary_text(text: str) -> str:
    return MATTERMOST_EMOJI_PATTERN.sub("", text).strip()


def render_summary_bullets(report: EnvReport, peer_report: EnvReport, analysis: EnvAnalysis) -> list[str]:
    bullets: list[str] = []
    top_service = report.top_services[0][0] if report.top_services else "서비스"
    top_count = report.top_services[0][1] if report.top_services else report.current_count
    top_pattern = report.top_patterns[0][0] if report.top_patterns else ""
    peer_title = peer_report.environment.upper()

    observability_gap = has_observability_gap(report)

    if observability_gap:
        bullets.append(
            f"{report.environment} 관측 데이터가 완전하지 않습니다. "
            f"health가 {health_text(report)}로 잡혀 실제 정상/장애 여부를 이 브리프만으로 단정하면 안 됩니다."
        )
    elif has_favorite_routes_constraint_signal(report):
        pattern_count = favorite_routes_pattern_count(report) or top_count or report.current_count
        bullets.append(
            "backend에서 `/favorite-routes` 저장 중 "
            f"`favorite_routes_route_option_check` 제약조건 오류가 약 `{pattern_count}`건 반복되고 있습니다. "
            "`route_option` 값과 DB check constraint, enum/validation mapping이 어긋났을 가능성이 큽니다."
        )
    elif report.down_targets:
        bullets.append(
            f"{', '.join(target.upper() for target in report.down_targets)} health가 DOWN입니다. "
            "로그 증가보다 먼저 실제 의존성/컨테이너 상태를 확인해야 합니다."
        )
    elif report.current_count > 0 and top_pattern:
        bullets.append(
            f"{top_service}에서 `{compact_text(top_pattern, 90)}` 패턴이 `{top_count}`건 중심으로 반복됩니다. "
            f"{report.environment} 쪽 특정 도메인/서비스 로직을 먼저 의심하는 편이 맞습니다."
        )
    elif report.current_count > 0:
        bullets.append(
            f"{report.environment} warning/error가 `{report.current_count}`건 감지됐고, "
            f"{top_service} 비중이 가장 큽니다. 같은 시간대 원문 로그를 서비스 기준으로 묶어 봐야 합니다."
        )
    else:
        bullets.append(
            f"{report.environment}는 지난 1시간 warning/error가 없고 health도 {health_text(report)} 상태입니다. "
            "지금은 장애 징후보다 정상 베이스라인에 가깝습니다."
        )

    if observability_gap:
        if has_query_failure(report):
            bullets.append(
                "warning/error와 전체 로그가 낮게 보여도 Loki/Prometheus 조회 실패가 섞여 있어 "
                "실제 로그 유입량이 과소 집계됐을 수 있습니다."
            )
        else:
            bullets.append(
                "Loki 전체 로그가 `0`건이라 Promtail 라벨/수집 중단 또는 실제 무트래픽 여부를 확인해야 합니다."
            )
    elif report.current_count > 0:
        bullets.append(
            f"전체 로그 대비 경고 비율은 `{format_ratio(report.warning_error_ratio)}`이고, "
            f"{peer_title} warning/error는 `{peer_report.current_count}`건입니다. "
            f"health가 유지된다면 전체 장애보다는 `{top_service}` 쪽 반복 오류 가능성이 더 큽니다."
        )
    elif report.total_logs_current > 0:
        bullets.append(
            f"전체 로그는 `{report.total_logs_current}`건으로 직전 대비 `{report.total_logs_delta:+d}`건입니다. "
            "경고 비율이 낮으므로 로그 유입 자체보다 health 변화 여부만 계속 비교하면 됩니다."
        )

    if report.suppressed_count > 0:
        bullets.append(
            f"GraphHopper no-route 계열처럼 운영성 낮은 도메인 경고 `{report.suppressed_count}`건은 "
            "메인 warning/error 집계에서 제외했습니다."
        )

    if observability_gap:
        bullets.append(f"다음 확인: {analysis.next_action}")
    elif report.current_count > 0 or report.down_targets:
        bullets.append(f"다음 확인: {analysis.next_action}")
    else:
        bullets.append(
            "다음 확인: 배포 직후 같은 지표가 튀는지만 보고, "
            f"이상 징후가 생기면 {peer_title}와 같은 시간대로 비교하면 됩니다."
        )

    return [clean_summary_text(bullet) for bullet in bullets]


def render_summary_section(report: EnvReport, peer_report: EnvReport, analysis: EnvAnalysis) -> str:
    lines = ["##### :memo: 요약"]
    lines.extend(f"- {bullet}" for bullet in render_summary_bullets(report, peer_report, analysis))
    return "\n".join(lines)


def render_detail_section(report: EnvReport, analysis: EnvAnalysis | None = None, peer_title: str | None = None) -> str:
    pattern_text = "\n".join(f"- `{pattern}` x{count}" for pattern, count in report.top_patterns) or "- 없음"
    sample_text = "\n".join(
        f"- [{sample.service}/{sample.level}] {compact_text(sample.message)}" for sample in report.sample_logs
    ) or "- 없음"
    return (
        "##### :pushpin: 대표 패턴\n"
        f"{pattern_text}\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        "##### :page_facing_up: 대표 로그\n"
        f"{sample_text}"
    )


def render_env_section(report: EnvReport) -> str:
    return (
        f"**[{report.environment}]**\n"
        f"{render_metric_section(report)}\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        f"{render_detail_section(report)}"
    )


def render_mattermost(
    *,
    now: datetime,
    lookback_minutes: int,
    rule_summary: str,
    agent_summary: str | None,
    dev_report: EnvReport,
    prod_report: EnvReport,
) -> str:
    end_text = now.astimezone(KST).strftime("%Y-%m-%d %H:%M")
    start_text = (now - timedelta(minutes=lookback_minutes)).astimezone(KST).strftime("%H:%M")
    summary_title = agent_summary or rule_summary
    return (
        f"#### :mag: {lookback_minutes}분 운영 로그 브리프 ({start_text} ~ {end_text} KST)\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        "##### :memo: 전체 요약\n"
        f"- {summary_title}\n"
        f"- 규칙 기반: {rule_summary}\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        f"{render_env_section(prod_report)}\n\n"
        f"{render_env_section(dev_report)}"
    )


def render_environment_mattermost(
    *,
    now: datetime,
    lookback_minutes: int,
    report: EnvReport,
    peer_report: EnvReport,
    analysis: EnvAnalysis,
) -> str:
    end_text = now.astimezone(KST).strftime("%Y-%m-%d %H:%M")
    start_text = (now - timedelta(minutes=lookback_minutes)).astimezone(KST).strftime("%H:%M")
    env_title = report.environment.upper()
    peer_title = peer_report.environment.upper()
    return (
        f"#### :mag: {env_title} {lookback_minutes}분 로그 브리프 ({start_text} ~ {end_text} KST)\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        f"{render_metric_section(report)}\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        f"{render_summary_section(report, peer_report, analysis)}\n"
        f"{MESSAGE_SECTION_BREAK}\n"
        f"{render_detail_section(report, analysis=analysis, peer_title=peer_title)}"
    )


def send_mattermost(webhook_url: str, text: str) -> None:
    post_json_request(webhook_url, payload={"text": text}, timeout=20)


def emit_console_text(text: str = "") -> None:
    try:
        print(text)
    except UnicodeEncodeError:
        sys.stdout.buffer.write(text.encode("utf-8", errors="replace") + b"\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate hourly dev/prod observability brief.")
    parser.add_argument("--lookback-minutes", type=int, default=int(os.getenv("OBS_BRIEF_LOOKBACK_MINUTES", "60")))
    parser.add_argument("--loki-url", default=os.getenv("OBS_BRIEF_LOKI_URL", "http://loki:3100"))
    parser.add_argument("--prometheus-url", default=os.getenv("OBS_BRIEF_PROMETHEUS_URL", "http://prometheus:9090"))
    parser.add_argument("--gitlab-base-url", default=os.getenv("OBS_BRIEF_GITLAB_BASE_URL", "https://git.example.com"))
    parser.add_argument("--gitlab-project-path", default=os.getenv("OBS_BRIEF_GITLAB_PROJECT_PATH", "s14-final/S14P31E102"))
    parser.add_argument("--gitlab-token", default=os.getenv("OBS_BRIEF_GITLAB_TOKEN") or os.getenv("GITLAB_TOKEN"))
    parser.add_argument("--report-json", default=os.getenv("OBS_BRIEF_REPORT_JSON", "reports/observability/hourly-brief.json"))
    parser.add_argument(
        "--dev-mattermost-webhook-url",
        default=(
            os.getenv("DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL")
            or os.getenv("LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL")
            or os.getenv("MATTERMOST_WEBHOOK_URL")
        ),
    )
    parser.add_argument(
        "--prod-mattermost-webhook-url",
        default=(
            os.getenv("PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL")
            or os.getenv("LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL")
            or os.getenv("MATTERMOST_WEBHOOK_URL")
        ),
    )
    parser.add_argument("--dev-manifest-path", default=os.getenv("OBS_BRIEF_DEV_MANIFEST_PATH", "/opt/e102-server/runtime-state/dev-release.json"))
    parser.add_argument("--prod-manifest-path", default=os.getenv("OBS_BRIEF_PROD_MANIFEST_PATH", "/opt/e102-server/runtime-state/prod-release.json"))
    parser.add_argument("--enable-agent-summary", action="store_true", default=parse_bool(os.getenv("OBS_BRIEF_AGENT_ENABLED"), False))
    parser.add_argument("--agent-provider", default=os.getenv("OBS_BRIEF_AGENT_PROVIDER", "openai"))
    parser.add_argument("--agent-base-url", default=os.getenv("OBS_BRIEF_AGENT_BASE_URL", "https://api.openai.com/v1"))
    parser.add_argument("--agent-model", default=os.getenv("OBS_BRIEF_AGENT_MODEL", "claude-opus-4-5-20251101"))
    parser.add_argument("--agent-max-tokens", type=int, default=int(os.getenv("OBS_BRIEF_AGENT_MAX_TOKENS", "700")))
    parser.add_argument("--agent-api-key", default=os.getenv("OBS_BRIEF_AGENT_API_KEY") or os.getenv("OPENAI_API_KEY"))
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    now = utcnow()
    dev_report = build_env_report(
        environment="dev",
        loki_url=args.loki_url,
        prometheus_url=args.prometheus_url,
        now=now,
        lookback_minutes=args.lookback_minutes,
        gitlab_base_url=args.gitlab_base_url,
        gitlab_project_path=args.gitlab_project_path,
        gitlab_token=args.gitlab_token,
        manifest_path=Path(args.dev_manifest_path),
    )
    prod_report = build_env_report(
        environment="prod",
        loki_url=args.loki_url,
        prometheus_url=args.prometheus_url,
        now=now,
        lookback_minutes=args.lookback_minutes,
        gitlab_base_url=args.gitlab_base_url,
        gitlab_project_path=args.gitlab_project_path,
        gitlab_token=args.gitlab_token,
        manifest_path=Path(args.prod_manifest_path),
    )
    rule_summary = build_rule_summary(dev_report, prod_report)
    dev_rule_summary = build_environment_summary(dev_report, prod_report)
    prod_rule_summary = build_environment_summary(prod_report, dev_report)
    dev_rule_analysis = build_rule_analysis(dev_report, prod_report)
    prod_rule_analysis = build_rule_analysis(prod_report, dev_report)
    generated_at = now.astimezone(KST).isoformat()
    context = {
        "generated_at": generated_at,
        "lookback_minutes": args.lookback_minutes,
        "rule_summary": rule_summary,
        "prod": asdict(prod_report),
        "dev": asdict(dev_report),
    }
    project_context_path = Path(__file__).resolve().parents[2] / "INF" / "monitoring" / "observability-brief-context.json"
    project_context = load_project_context(project_context_path)
    if project_context:
        context["project_context"] = project_context
    agent_analyses: dict[str, EnvAnalysis] = {}
    try:
        prod_agent_analysis = maybe_generate_agent_analysis(
            enabled=args.enable_agent_summary and should_generate_agent_summary_for(prod_report),
            api_key=args.agent_api_key,
            provider=args.agent_provider,
            base_url=args.agent_base_url,
            model=args.agent_model,
            max_tokens=args.agent_max_tokens,
            context=build_agent_context(
                generated_at=generated_at,
                lookback_minutes=args.lookback_minutes,
                target_report=prod_report,
                peer_report=dev_report,
                rule_analysis=prod_rule_analysis,
                project_context=project_context,
            ),
            fallback_analysis=prod_rule_analysis,
        )
        if prod_agent_analysis:
            agent_analyses["prod"] = prod_agent_analysis
        dev_agent_analysis = maybe_generate_agent_analysis(
            enabled=args.enable_agent_summary and should_generate_agent_summary_for(dev_report),
            api_key=args.agent_api_key,
            provider=args.agent_provider,
            base_url=args.agent_base_url,
            model=args.agent_model,
            max_tokens=args.agent_max_tokens,
            context=build_agent_context(
                generated_at=generated_at,
                lookback_minutes=args.lookback_minutes,
                target_report=dev_report,
                peer_report=prod_report,
                rule_analysis=dev_rule_analysis,
                project_context=project_context,
            ),
            fallback_analysis=dev_rule_analysis,
        )
        if dev_agent_analysis:
            agent_analyses["dev"] = dev_agent_analysis
    except (
        urllib_error.URLError,
        TimeoutError,
        KeyError,
        ValueError,
        subprocess.CalledProcessError,
        AttributeError,
        IndexError,
        TypeError,
    ) as exc:
        context["agent_error"] = str(exc)

    selected_prod_analysis = agent_analyses.get("prod") or prod_rule_analysis
    selected_dev_analysis = agent_analyses.get("dev") or dev_rule_analysis

    combined_mattermost_text = render_mattermost(
        now=now,
        lookback_minutes=args.lookback_minutes,
        rule_summary=rule_summary,
        agent_summary=(agent_analyses.get("prod") or agent_analyses.get("dev") or None).conclusion
        if agent_analyses
        else None,
        dev_report=dev_report,
        prod_report=prod_report,
    )
    dev_mattermost_text = render_environment_mattermost(
        now=now,
        lookback_minutes=args.lookback_minutes,
        report=dev_report,
        peer_report=prod_report,
        analysis=selected_dev_analysis,
    )
    prod_mattermost_text = render_environment_mattermost(
        now=now,
        lookback_minutes=args.lookback_minutes,
        report=prod_report,
        peer_report=dev_report,
        analysis=selected_prod_analysis,
    )
    context["mattermost_text"] = combined_mattermost_text
    context["dev_mattermost_text"] = dev_mattermost_text
    context["prod_mattermost_text"] = prod_mattermost_text
    context["dev_rule_summary"] = dev_rule_summary
    context["prod_rule_summary"] = prod_rule_summary
    context["dev_rule_analysis"] = asdict(dev_rule_analysis)
    context["prod_rule_analysis"] = asdict(prod_rule_analysis)
    context["dev_analysis"] = asdict(selected_dev_analysis)
    context["prod_analysis"] = asdict(selected_prod_analysis)
    context["agent_summaries"] = {environment: analysis.conclusion for environment, analysis in agent_analyses.items()}
    context["agent_analyses"] = {environment: asdict(analysis) for environment, analysis in agent_analyses.items()}
    report_path = Path(args.report_json)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
    emit_console_text(prod_mattermost_text)
    emit_console_text()
    emit_console_text(dev_mattermost_text)

    if not args.dry_run and args.prod_mattermost_webhook_url:
        send_mattermost(args.prod_mattermost_webhook_url, prod_mattermost_text)
    if not args.dry_run and args.dev_mattermost_webhook_url:
        send_mattermost(args.dev_mattermost_webhook_url, dev_mattermost_text)

    return 0


if __name__ == "__main__":
    sys.exit(main())
