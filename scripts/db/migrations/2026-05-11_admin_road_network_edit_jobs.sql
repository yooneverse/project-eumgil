-- Admin road network edit async job support.
-- Run with: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/migrations/2026-05-11_admin_road_network_edit_jobs.sql

CREATE TABLE IF NOT EXISTS admin_road_network_edit_jobs (
    job_id bigserial PRIMARY KEY,
    status varchar(30) NOT NULL,
    request_json jsonb NOT NULL,
    total_edits integer NOT NULL,
    processed_edits integer NOT NULL DEFAULT 0,
    result_json jsonb,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_admin_road_network_edit_jobs_status
    ON admin_road_network_edit_jobs (status, created_at DESC);
