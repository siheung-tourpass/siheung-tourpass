-- Apply once to an existing database; included in schema.sql for new databases.
BEGIN;
CREATE TABLE place_feedback (
  visitor_id BIGINT NOT NULL REFERENCES app_user(id),
  place_id BIGINT NOT NULL REFERENCES place(id),
  rating SMALLINT NOT NULL CHECK(rating BETWEEN 1 AND 5),
  comment VARCHAR(500),
  created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(visitor_id,place_id)
);
CREATE INDEX place_feedback_place_id_idx ON place_feedback(place_id);
CREATE TRIGGER place_feedback_updated_at BEFORE UPDATE ON place_feedback FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
COMMIT;
