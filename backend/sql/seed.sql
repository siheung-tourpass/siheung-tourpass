-- Demo credentials: DemoPass!2026 (all five accounts). Empty DB only.
SET NAMES utf8mb4;

SET time_zone='+00:00';

INSERT INTO app_user(id,login_id,password_hash,display_name,role) VALUES
(1,'visitor','pbkdf2-sha256$210000$Ch82rOiakzdiOt90TsnxMg==$droZhbBmakEBtOSEXGe1+3hUfiRiZKeYBhIy/GRMZPk=','visitor','VISITOR'),
(2,'staff-a','pbkdf2-sha256$210000$9mOdWvsIN1Yv4CfsIVWv6g==$anwWWKFW96DX99YcilclVjgRXiw23Ts3c5+9JUG8pZs=','staff-a','STAFF'),
(3,'staff-b','pbkdf2-sha256$210000$FbOUzV7X2FoGzpvMjqv88Q==$73D94KSPOHcWfbERJXAtd/Q+Of5IXax5T5NwoK4aAcs=','staff-b','STAFF'),
(4,'admin','pbkdf2-sha256$210000$EEGizThWLZzuXOD1BTfbEA==$IEVEklVf/x9wReNhzcbQOqg1pBRV3aM6uX1rgbwMJyM=','admin','ADMIN'),
(5,'visitor2','pbkdf2-sha256$210000$jBaMBh7/39aBdO6Sc3OqrA==$AquZsRVwJo+PpJ9lgn24kfHDoRMl3v4sssGhO+TDOQk=','visitor2','VISITOR');

INSERT INTO region(id,code,name) VALUES
(1,'REGION_GEOBUK','거북섬'),
(2,'REGION_OIDO','오이도'),
(3,'REGION_EMPTY','시연 빈 지역');

INSERT INTO theme(id,code,name) VALUES
(1,'THEME_MARINE_EXPERIENCE','해양 체험'),
(2,'THEME_EXPERIENCE','체험'),
(3,'THEME_REST','휴식');

INSERT INTO companion_type(id,code,name) VALUES
(1,'COMPANION_FAMILY','가족'),
(2,'COMPANION_FRIENDS','친구');

INSERT INTO place(id,code,region_id,name,description,location_description,material_kind,evidence_note,review_status,reviewed_by,reviewed_at) VALUES
(1,'PLACE_A',1,'A 가상 체험','시연 자료·실제 사용 불가','가상 위치입니다.','DEMO','시연 설정: A 가상 체험','APPROVED',4,'2026-10-03 00:00:00'),
(2,'PLACE_B',2,'B 가상 음료','시연 자료·실제 사용 불가','가상 위치입니다.','DEMO','시연 설정: B 가상 음료','APPROVED',4,'2026-10-03 00:00:00'),
(3,'PLACE_C',1,'C 가상 전시','시연 자료·실제 사용 불가','가상 위치입니다.','DEMO','시연 설정: C 가상 전시','APPROVED',4,'2026-10-03 00:00:00'),
(4,'PLACE_D',2,'D 가상 쉼터','시연 자료·실제 사용 불가','가상 위치입니다.','DEMO','시연 설정: D 가상 쉼터','APPROVED',4,'2026-10-03 00:00:00');

INSERT INTO place_theme(place_id,theme_id,material_kind,evidence_note,review_status,reviewed_by,reviewed_at) VALUES
(1,1,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(2,3,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(3,2,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(4,3,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00');

INSERT INTO place_companion(place_id,companion_type_id,material_kind,evidence_note,review_status,reviewed_by,reviewed_at) VALUES
(1,1,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(2,1,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(3,1,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00'),
(4,2,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00');

INSERT INTO theme_parent(child_theme_id,parent_theme_id,material_kind,evidence_note,review_status,reviewed_by,reviewed_at) VALUES
(1,2,'DEMO','시연 설정: 관계 분류','APPROVED',4,'2026-10-03 00:00:00');

INSERT INTO merchant(id,code,place_id,name,location_description) VALUES
(1,'MERCHANT_A',1,'가맹점 A','거북섬 배경의 가상 위치입니다.'),
(2,'MERCHANT_B',2,'가맹점 B','오이도 배경의 가상 위치입니다.');

INSERT INTO merchant_staff(user_id,merchant_id) VALUES
(2,1),
(3,2);

INSERT INTO product(id,code,name,description) VALUES
(1,'DEMO_DAY','시연용 1일 패스','시연용·실제 사용 불가. 발급부터 24시간');

INSERT INTO benefit(id,code,product_id,merchant_id,name,description,benefit_type,base_price_won,discount_won,payable_won,reservation_note) VALUES
(1,'BENEFIT_A',1,1,'가상 체험 1회 무료','시연용 가상 체험','FREE_ONCE',NULL,NULL,0,'예약 불필요'),
(2,'BENEFIT_B',1,2,'가상 음료 1,000원 할인','시연용 가상 음료 5,000−1,000=4,000원','DISCOUNT',5000,1000,4000,'예약 불필요');

INSERT INTO ontology_revision(id,revision) VALUES
(1,1);
