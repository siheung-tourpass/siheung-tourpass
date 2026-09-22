-- Preserve existing card snapshots and records; apply once to an existing DB.
BEGIN;
ALTER TABLE journey_place DROP CONSTRAINT IF EXISTS journey_place_card_kind_check;
ALTER TABLE journey_place DROP CONSTRAINT IF EXISTS journey_card_kind_check;
ALTER TABLE journey_place ADD CONSTRAINT journey_card_kind_check CHECK(card_kind IN ('PLACE','EVENT','MERCHANT','DINING'));
COMMIT;
