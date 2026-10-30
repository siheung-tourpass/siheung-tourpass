BEGIN;
CREATE TABLE pass_lookup (
  token VARCHAR(22) PRIMARY KEY,
  staff_user_id BIGINT NOT NULL REFERENCES app_user(id),
  merchant_id BIGINT NOT NULL REFERENCES merchant(id),
  pass_id BIGINT NOT NULL REFERENCES issued_pass(id),
  expires_at TIMESTAMPTZ(6) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE
);
COMMIT;
