CREATE TABLE IF NOT EXISTS admin_audit_logs (
  log_id BIGSERIAL PRIMARY KEY,
  actor_user_id UUID NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id VARCHAR(100),
  gu VARCHAR(50),
  dong VARCHAR(50),
  summary VARCHAR(500) NOT NULL,
  before_json JSONB,
  after_json JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE admin_audit_logs
  DROP CONSTRAINT IF EXISTS admin_audit_logs_actor_user_id_fkey;

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at
  ON admin_audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_actor
  ON admin_audit_logs(actor_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_action
  ON admin_audit_logs(action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_area
  ON admin_audit_logs(gu, dong, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target
  ON admin_audit_logs(target_type, target_id);
