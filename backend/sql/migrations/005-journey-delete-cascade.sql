-- Existing databases after 004-journey-cards.sql; apply once. Existing rows are preserved.
BEGIN;
ALTER TABLE journey_place DROP CONSTRAINT journey_place_journey_id_fkey;
ALTER TABLE journey_place ADD CONSTRAINT journey_place_journey_id_fkey FOREIGN KEY(journey_id) REFERENCES journey(id) ON DELETE CASCADE;
ALTER TABLE journey_pass_history DROP CONSTRAINT journey_pass_history_journey_id_fkey;
ALTER TABLE journey_pass_history ADD CONSTRAINT journey_pass_history_journey_id_fkey FOREIGN KEY(journey_id) REFERENCES journey(id) ON DELETE CASCADE;
COMMIT;
