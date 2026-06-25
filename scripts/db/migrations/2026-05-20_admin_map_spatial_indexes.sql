-- Admin map performance indexes for road-network and route-review map queries.
-- Run with: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/migrations/2026-05-20_admin_map_spatial_indexes.sql

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_road_segments_geom_gist
    ON road_segments
    USING GIST (geom);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_admin_areas_geom_gist
    ON admin_areas
    USING GIST (geom);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_segment_features_edge_id
    ON segment_features (edge_id);
