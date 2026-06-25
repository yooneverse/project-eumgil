#!/usr/bin/env python3
"""GraphHopper build pipeline을 위한 빠른 DB/export 검증 smoke다.

전체 graph-cache build는 오래 걸리고 실행 환경 의존성이 크다. 이 smoke는
PostGIS에서 작은 road segment 샘플만 읽고 exporter validation 로직을 재사용해,
DB row가 OSM export 계약을 깨는 경우 build 전에 빠르게 실패시킨다.
"""
import argparse
import importlib.util
import json
import os
from datetime import datetime, timezone


EXPORTER_PATH = "/usr/local/bin/export-postgis-to-osm.py"


def load_exporter():
    """graphhopper-build 컨테이너가 사용하는 exporter 모듈을 그대로 읽는다."""
    spec = importlib.util.spec_from_file_location("export_postgis_to_osm", EXPORTER_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def strip_sql(sql):
    """설정 SQL을 subquery로 감쌀 수 있게 정규화한다."""
    return sql.strip().rstrip(";")


def fetch_dicts(conn, sql, params=None):
    with conn.cursor() as cur:
        cur.execute(sql, params or [])
        columns = [desc[0] for desc in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def fetch_scalar(conn, sql):
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchone()[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sample-size", type=int, default=int(os.getenv("GRAPHHOPPER_EXPORT_SMOKE_SAMPLE_SIZE", "100")))
    parser.add_argument("--report-json", default=os.getenv("GRAPHHOPPER_EXPORT_SMOKE_REPORT_FILE"))
    args = parser.parse_args()

    exporter = load_exporter()
    nodes_sql = strip_sql(os.getenv("GRAPHHOPPER_ROAD_NODES_SQL") or exporter.DEFAULT_NODES_SQL)
    segments_sql = strip_sql(os.getenv("GRAPHHOPPER_ROAD_SEGMENTS_SQL") or exporter.DEFAULT_SEGMENTS_SQL)

    with exporter.connect() as conn:
        node_count = fetch_scalar(conn, 'SELECT COUNT(*) FROM road_nodes')
        segment_count = fetch_scalar(conn, 'SELECT COUNT(*) FROM road_segments')
        segment_feature_table_exists = exporter.table_exists(conn, "segment_features")
        segment_feature_count = fetch_scalar(conn, 'SELECT COUNT(*) FROM segment_features') if segment_feature_table_exists else 0
        segments = fetch_dicts(conn, f"SELECT * FROM ({segments_sql}) segments LIMIT %s", [args.sample_size])
        sample_edge_ids = [int(segment["edge_id"]) for segment in segments]
        features = []
        if segment_feature_table_exists and sample_edge_ids:
            features_sql = strip_sql(exporter.build_segment_features_sql(exporter.fetch_table_columns(conn, "segment_features")))
            features = fetch_dicts(
                conn,
                f"SELECT * FROM ({features_sql}) features WHERE edge_id = ANY(%s)",
                [list(sample_edge_ids)],
            )
        # validator는 샘플 segment마다 endpoint node가 필요하다.
        # 큰 graph에서도 smoke가 가볍게 유지되도록 참조된 node만 읽는다.
        referenced_nodes = sorted(
            {
                int(segment["from_node_id"])
                for segment in segments
            }
            | {
                int(segment["to_node_id"])
                for segment in segments
            }
        )
        nodes = []
        if referenced_nodes:
            nodes = fetch_dicts(
                conn,
                f"SELECT * FROM ({nodes_sql}) nodes WHERE vertex_id = ANY(%s) ORDER BY vertex_id",
                [referenced_nodes],
            )

    loaded_node_count = len(nodes)
    loaded_segment_count = len(segments)
    nodes, segments = exporter.apply_segment_features_to_export(nodes, segments, features)
    report = exporter.validate_graph(nodes, segments, "graphhopper-db-export-smoke")
    report["smoke"] = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sampleSize": args.sample_size,
        "databaseNodeCount": node_count,
        "databaseSegmentCount": segment_count,
        "segmentFeatureTableExists": segment_feature_table_exists,
        "databaseSegmentFeatureCount": segment_feature_count,
        "sampleFeatureCount": len(features),
        "sampleReferencedNodeCount": len(referenced_nodes),
        "sampleLoadedNodeCount": loaded_node_count,
        "sampleSegmentCount": loaded_segment_count,
        "exportedNodeCount": len(nodes),
        "exportedSegmentCount": len(segments),
    }
    exporter.write_json_report(args.report_json, report)

    if report["blockers"]:
        print(json.dumps(report["smoke"], ensure_ascii=False))
        print(f'GraphHopper DB export smoke failed: blockerCount={report["summary"]["blockerCount"]}')
        return 1

    print(
        "GraphHopper DB export smoke ok: "
        f"nodes={node_count}, segments={segment_count}, sampleSegments={len(segments)}, "
        f"segmentFeatures={segment_feature_count}, sampleFeatures={len(features)}, "
        f'warnings={report["summary"]["warningCount"]}'
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
