-- Existing databases only; existing draft/archive states remain unchanged.
BEGIN;
ALTER TABLE journey DROP CONSTRAINT journey_status_check;
ALTER TABLE journey ADD CONSTRAINT journey_status_check CHECK(status IN ('PLANNING','CONFIRMED','ARCHIVED'));
COMMIT;
