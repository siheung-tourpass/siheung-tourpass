-- Preserve existing place items and their records; apply once to an existing DB.
BEGIN;
ALTER TABLE journey_place ALTER COLUMN place_id DROP NOT NULL;
ALTER TABLE journey_place ADD COLUMN card_kind VARCHAR(8);
ALTER TABLE journey_place ADD COLUMN card_key VARCHAR(150);
UPDATE journey_place SET card_kind='PLACE',card_key='place:' || place_id;
ALTER TABLE journey_place ALTER COLUMN card_kind SET NOT NULL;
ALTER TABLE journey_place ALTER COLUMN card_key SET NOT NULL;
ALTER TABLE journey_place ADD CONSTRAINT journey_card_kind_check CHECK(card_kind IN ('PLACE','EVENT','MERCHANT'));
ALTER TABLE journey_place ADD CONSTRAINT journey_card_place_check CHECK((card_kind='PLACE')=(place_id IS NOT NULL));
ALTER TABLE journey_place ADD CONSTRAINT journey_card_key_unique UNIQUE(journey_id,card_key);
COMMIT;
