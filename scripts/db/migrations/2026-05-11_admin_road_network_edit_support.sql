-- 관리자 보행 네트워크 편집 반영용 sequence/default 및 source feature 매칭 threshold 함수.
-- Run with: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/migrations/2026-05-11_admin_road_network_edit_support.sql

CREATE SEQUENCE IF NOT EXISTS road_nodes_vertex_id_seq;
SELECT setval(
    'road_nodes_vertex_id_seq',
    COALESCE((SELECT MAX(vertex_id) FROM road_nodes), 0) + 1,
    false
);
ALTER TABLE road_nodes
    ALTER COLUMN vertex_id SET DEFAULT nextval('road_nodes_vertex_id_seq');

CREATE SEQUENCE IF NOT EXISTS road_segments_edge_id_seq;
SELECT setval(
    'road_segments_edge_id_seq',
    COALESCE((SELECT MAX(edge_id) FROM road_segments), 0) + 1,
    false
);
ALTER TABLE road_segments
    ALTER COLUMN edge_id SET DEFAULT nextval('road_segments_edge_id_seq');

CREATE SEQUENCE IF NOT EXISTS segment_features_feature_id_seq;
SELECT setval(
    'segment_features_feature_id_seq',
    COALESCE((SELECT MAX(feature_id) FROM segment_features), 0) + 1,
    false
);
ALTER TABLE segment_features
    ALTER COLUMN feature_id SET DEFAULT nextval('segment_features_feature_id_seq');

CREATE OR REPLACE FUNCTION source_match_threshold_meter(source_file text)
RETURNS double precision
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN source_file = '점자블록.csv' THEN 0.0
        WHEN source_file = '계단.csv' THEN 2.0
        WHEN source_file = '횡단보도_음향신호기.csv' THEN 30.0
        WHEN source_file = '횡단보도_신호등.csv' THEN 20.0
        WHEN source_file = '경사도&표면타입.csv' THEN 20.0
        ELSE 10.0
    END
$$;
