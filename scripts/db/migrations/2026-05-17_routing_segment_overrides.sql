CREATE TABLE IF NOT EXISTS routing_segment_overrides (
    edge_id BIGINT PRIMARY KEY,
    walk_access VARCHAR(30),
    stairs_state VARCHAR(30),
    width_state VARCHAR(30),
    braille_block_state VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_routing_segment_overrides_edge_id
        FOREIGN KEY (edge_id)
        REFERENCES road_segments (edge_id)
        ON DELETE CASCADE
);

ALTER TABLE routing_segment_overrides
    ALTER COLUMN walk_access DROP NOT NULL;

ALTER TABLE routing_segment_overrides
    ADD COLUMN IF NOT EXISTS stairs_state VARCHAR(30);

ALTER TABLE routing_segment_overrides
    ADD COLUMN IF NOT EXISTS width_state VARCHAR(30);

ALTER TABLE routing_segment_overrides
    ADD COLUMN IF NOT EXISTS braille_block_state VARCHAR(30);

ALTER TABLE routing_segment_overrides
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE road_segments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS routing_apply_states (
    state_key VARCHAR(100) PRIMARY KEY,
    dirty BOOLEAN NOT NULL DEFAULT FALSE,
    applying BOOLEAN NOT NULL DEFAULT FALSE,
    applying_started_at TIMESTAMP NULL,
    dirty_marked_at TIMESTAMP NULL,
    last_applied_at TIMESTAMP NULL,
    last_result_status VARCHAR(30) NOT NULL DEFAULT 'SKIPPED',
    last_result_message TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE routing_apply_states
    ADD COLUMN IF NOT EXISTS applying_started_at TIMESTAMP NULL;

INSERT INTO routing_apply_states (
    state_key,
    dirty,
    applying,
    applying_started_at,
    dirty_marked_at,
    last_applied_at,
    last_result_status,
    last_result_message,
    updated_at
)
VALUES (
    'ROUTING_OVERRIDES',
    FALSE,
    FALSE,
    NULL,
    NULL,
    NULL,
    'SKIPPED',
    NULL,
    CURRENT_TIMESTAMP
)
ON CONFLICT (state_key) DO NOTHING;
