CREATE TABLE IF NOT EXISTS admin_area_assignments (
  assignment_id BIGSERIAL PRIMARY KEY,
  gu VARCHAR(50) NOT NULL,
  dong VARCHAR(50) NOT NULL,
  assignment_type VARCHAR(30) NOT NULL DEFAULT 'ROAD_NETWORK',
  assignee_user_id UUID REFERENCES users(user_id),
  status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uk_admin_area_assignments_area_type UNIQUE (gu, dong, assignment_type)
);

ALTER TABLE admin_area_assignments
  ADD COLUMN IF NOT EXISTS assignment_type VARCHAR(30) NOT NULL DEFAULT 'ROAD_NETWORK';

ALTER TABLE admin_area_assignments
  DROP CONSTRAINT IF EXISTS uk_admin_area_assignments_area;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'uk_admin_area_assignments_area_type'
  ) THEN
    ALTER TABLE admin_area_assignments
      ADD CONSTRAINT uk_admin_area_assignments_area_type UNIQUE (gu, dong, assignment_type);
  END IF;
END
$$;

INSERT INTO admin_area_assignments (
  gu,
  dong,
  assignment_type,
  assignee_user_id,
  status,
  created_at,
  updated_at
)
SELECT
  gu,
  dong,
  'FACILITY',
  assignee_user_id,
  status,
  created_at,
  updated_at
FROM admin_area_assignments
WHERE assignment_type = 'ROAD_NETWORK'
ON CONFLICT (gu, dong, assignment_type) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_admin_area_assignments_assignee
  ON admin_area_assignments(assignee_user_id);

CREATE INDEX IF NOT EXISTS idx_admin_area_assignments_status
  ON admin_area_assignments(status);

CREATE INDEX IF NOT EXISTS idx_admin_area_assignments_type_status
  ON admin_area_assignments(assignment_type, status);
