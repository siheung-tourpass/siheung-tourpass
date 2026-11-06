-- Deletes the fictional demo places, demo region, demo product and merchants A/B with every record that uses them.
-- Run once after tourism-sql.py and tourpass-sql.py; deleted history cannot be restored without a backup.
BEGIN;
CREATE TEMP TABLE demo_place ON COMMIT DROP AS SELECT id FROM place WHERE material_kind='DEMO';
CREATE TEMP TABLE demo_product ON COMMIT DROP AS SELECT id FROM product WHERE code='DEMO_DAY';
CREATE TEMP TABLE demo_merchant ON COMMIT DROP AS SELECT id FROM merchant WHERE code IN ('MERCHANT_A','MERCHANT_B') OR place_id IN (SELECT id FROM demo_place);
CREATE TEMP TABLE demo_benefit ON COMMIT DROP AS SELECT id FROM benefit WHERE product_id IN (SELECT id FROM demo_product) OR merchant_id IN (SELECT id FROM demo_merchant);
CREATE TEMP TABLE demo_pass ON COMMIT DROP AS SELECT id FROM issued_pass WHERE product_id IN (SELECT id FROM demo_product);
CREATE TEMP TABLE demo_issued_benefit ON COMMIT DROP AS SELECT id FROM issued_benefit
  WHERE pass_id IN (SELECT id FROM demo_pass) OR benefit_id IN (SELECT id FROM demo_benefit) OR merchant_id IN (SELECT id FROM demo_merchant) OR place_id IN (SELECT id FROM demo_place);
CREATE TEMP TABLE demo_feedback ON COMMIT DROP AS SELECT f.id FROM feedback f JOIN redemption r ON r.id=f.redemption_id WHERE r.issued_benefit_id IN (SELECT id FROM demo_issued_benefit);

DELETE FROM feedback_reason WHERE feedback_id IN (SELECT id FROM demo_feedback);
DELETE FROM feedback_theme WHERE feedback_id IN (SELECT id FROM demo_feedback);
DELETE FROM feedback WHERE id IN (SELECT id FROM demo_feedback);
DELETE FROM redemption WHERE issued_benefit_id IN (SELECT id FROM demo_issued_benefit);
DELETE FROM pass_lookup WHERE pass_id IN (SELECT id FROM demo_pass) OR merchant_id IN (SELECT id FROM demo_merchant);
UPDATE journey SET issued_pass_id=NULL WHERE issued_pass_id IN (SELECT id FROM demo_pass);
DELETE FROM journey_pass_history WHERE issued_pass_id IN (SELECT id FROM demo_pass);
DELETE FROM issued_benefit WHERE id IN (SELECT id FROM demo_issued_benefit);
DELETE FROM issued_pass WHERE id IN (SELECT id FROM demo_pass);
DELETE FROM benefit WHERE id IN (SELECT id FROM demo_benefit);
DELETE FROM merchant_staff WHERE merchant_id IN (SELECT id FROM demo_merchant);
DELETE FROM merchant WHERE id IN (SELECT id FROM demo_merchant);
DELETE FROM product WHERE id IN (SELECT id FROM demo_product);
-- Trip cards of demo places go too, with their photos; the trips themselves stay.
DELETE FROM journey_photo WHERE item_id IN (SELECT id FROM journey_place WHERE place_id IN (SELECT id FROM demo_place));
DELETE FROM journey_place WHERE place_id IN (SELECT id FROM demo_place);
DELETE FROM place_feedback WHERE place_id IN (SELECT id FROM demo_place);
DELETE FROM place_theme WHERE place_id IN (SELECT id FROM demo_place);
DELETE FROM place_companion WHERE place_id IN (SELECT id FROM demo_place);
DELETE FROM place_source WHERE place_id IN (SELECT id FROM demo_place);
DELETE FROM place WHERE id IN (SELECT id FROM demo_place);
DELETE FROM region r WHERE code='REGION_EMPTY' AND NOT EXISTS(SELECT 1 FROM place WHERE region_id=r.id) AND NOT EXISTS(SELECT 1 FROM merchant WHERE region_id=r.id);
UPDATE ontology_revision SET revision=revision+1 WHERE id=1;
COMMIT;
