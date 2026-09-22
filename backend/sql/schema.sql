-- MySQL 8.0.16+ / UTC; run against an empty siheung_tourpass database.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  login_id VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  role VARCHAR(8) NOT NULL CHECK(role IN ('VISITOR','STAFF','ADMIN')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE region (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE theme (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE companion_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  region_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  location_description VARCHAR(500) NOT NULL,
  material_kind VARCHAR(8) NOT NULL CHECK(material_kind IN ('REAL','DEMO')),
  source_url VARCHAR(2048),
  checked_on DATE,
  evidence_note VARCHAR(1000) NOT NULL,
  review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','APPROVED','REJECTED')),
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(reviewed_by) REFERENCES app_user(id),
  CHECK((review_status='DRAFT' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (review_status<>'DRAFT' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK(material_kind<>'DEMO' OR (source_url IS NULL AND checked_on IS NULL)),
  CHECK(material_kind<>'REAL' OR review_status<>'APPROVED' OR (source_url IS NOT NULL AND checked_on IS NOT NULL)),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY(region_id) REFERENCES region(id),
  INDEX(region_id,active,review_status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  place_id BIGINT NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  checked_on DATE NOT NULL,
  evidence_note VARCHAR(1000) NOT NULL,
  FOREIGN KEY(place_id) REFERENCES place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place_theme (
  place_id BIGINT NOT NULL,
  theme_id BIGINT NOT NULL,
  material_kind VARCHAR(8) NOT NULL CHECK(material_kind IN ('REAL','DEMO')),
  source_url VARCHAR(2048),
  checked_on DATE,
  evidence_note VARCHAR(1000) NOT NULL,
  review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','APPROVED','REJECTED')),
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(reviewed_by) REFERENCES app_user(id),
  CHECK((review_status='DRAFT' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (review_status<>'DRAFT' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK(material_kind<>'DEMO' OR (source_url IS NULL AND checked_on IS NULL)),
  CHECK(material_kind<>'REAL' OR review_status<>'APPROVED' OR (source_url IS NOT NULL AND checked_on IS NOT NULL)),
  PRIMARY KEY(place_id,theme_id),
  FOREIGN KEY(place_id) REFERENCES place(id),
  FOREIGN KEY(theme_id) REFERENCES theme(id),
  INDEX(theme_id,place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place_companion (
  place_id BIGINT NOT NULL,
  companion_type_id BIGINT NOT NULL,
  material_kind VARCHAR(8) NOT NULL CHECK(material_kind IN ('REAL','DEMO')),
  source_url VARCHAR(2048),
  checked_on DATE,
  evidence_note VARCHAR(1000) NOT NULL,
  review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','APPROVED','REJECTED')),
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(reviewed_by) REFERENCES app_user(id),
  CHECK((review_status='DRAFT' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (review_status<>'DRAFT' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK(material_kind<>'DEMO' OR (source_url IS NULL AND checked_on IS NULL)),
  CHECK(material_kind<>'REAL' OR review_status<>'APPROVED' OR (source_url IS NOT NULL AND checked_on IS NOT NULL)),
  PRIMARY KEY(place_id,companion_type_id),
  FOREIGN KEY(place_id) REFERENCES place(id),
  FOREIGN KEY(companion_type_id) REFERENCES companion_type(id),
  INDEX(companion_type_id,place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE theme_parent (
  child_theme_id BIGINT NOT NULL,
  parent_theme_id BIGINT NOT NULL,
  material_kind VARCHAR(8) NOT NULL CHECK(material_kind IN ('REAL','DEMO')),
  source_url VARCHAR(2048),
  checked_on DATE,
  evidence_note VARCHAR(1000) NOT NULL,
  review_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','APPROVED','REJECTED')),
  reviewed_by BIGINT,
  reviewed_at DATETIME(6),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(reviewed_by) REFERENCES app_user(id),
  CHECK((review_status='DRAFT' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (review_status<>'DRAFT' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK(material_kind<>'DEMO' OR (source_url IS NULL AND checked_on IS NULL)),
  CHECK(material_kind<>'REAL' OR review_status<>'APPROVED' OR (source_url IS NOT NULL AND checked_on IS NOT NULL)),
  PRIMARY KEY(child_theme_id,parent_theme_id),
  FOREIGN KEY(child_theme_id) REFERENCES theme(id),
  FOREIGN KEY(parent_theme_id) REFERENCES theme(id),
  INDEX(parent_theme_id,child_theme_id),
  CHECK(child_theme_id<>parent_theme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  place_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  location_description VARCHAR(500) NOT NULL,
  demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK(demo_only=TRUE),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(place_id) REFERENCES place(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant_staff (
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY(user_id,merchant_id),
  FOREIGN KEY(user_id) REFERENCES app_user(id),
  FOREIGN KEY(merchant_id) REFERENCES merchant(id),
  INDEX(merchant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  duration_hours INT NOT NULL DEFAULT 24 CHECK(duration_hours=24),
  demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK(demo_only=TRUE),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE benefit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  product_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  benefit_type VARCHAR(12) NOT NULL CHECK(benefit_type IN ('FREE_ONCE','DISCOUNT')),
  use_limit INT NOT NULL DEFAULT 1 CHECK(use_limit=1),
  base_price_won INT,
  discount_won INT,
  payable_won INT NOT NULL CHECK(payable_won>=0),
  reservation_required BOOLEAN NOT NULL DEFAULT FALSE CHECK(reservation_required=FALSE),
  reservation_note VARCHAR(500) NOT NULL,
  demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK(demo_only=TRUE),
  CHECK((benefit_type='FREE_ONCE' AND base_price_won IS NULL AND discount_won IS NULL AND payable_won=0) OR (benefit_type='DISCOUNT' AND base_price_won IS NOT NULL AND discount_won IS NOT NULL AND base_price_won>=0 AND discount_won>=0 AND discount_won<=base_price_won AND payable_won=base_price_won-discount_won)),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE issued_pass (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  code VARCHAR(32) COLLATE utf8mb4_bin NOT NULL UNIQUE,
  product_name VARCHAR(100) NOT NULL,
  duration_hours INT NOT NULL DEFAULT 24 CHECK(duration_hours=24),
  starts_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK(demo_only=TRUE),
  cancelled_at DATETIME(6),
  cancelled_by BIGINT,
  cancel_reason VARCHAR(500),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY(owner_id) REFERENCES app_user(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(cancelled_by) REFERENCES app_user(id),
  CHECK(expires_at=DATE_ADD(starts_at,INTERVAL 24 HOUR)),
  CHECK((cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL) OR (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND CHAR_LENGTH(TRIM(cancel_reason)) BETWEEN 1 AND 500)),
  INDEX(owner_id,cancelled_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE issued_benefit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pass_id BIGINT NOT NULL,
  benefit_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  place_id BIGINT NOT NULL,
  merchant_name VARCHAR(100) NOT NULL,
  place_name VARCHAR(100) NOT NULL,
  region_name VARCHAR(100) NOT NULL,
  location_description VARCHAR(500) NOT NULL,
  benefit_name VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  benefit_type VARCHAR(12) NOT NULL CHECK(benefit_type IN ('FREE_ONCE','DISCOUNT')),
  use_limit INT NOT NULL DEFAULT 1 CHECK(use_limit=1),
  base_price_won INT,
  discount_won INT,
  payable_won INT NOT NULL CHECK(payable_won>=0),
  reservation_required BOOLEAN NOT NULL DEFAULT FALSE CHECK(reservation_required=FALSE),
  reservation_note VARCHAR(500) NOT NULL,
  demo_only BOOLEAN NOT NULL DEFAULT TRUE CHECK(demo_only=TRUE),
  CHECK((benefit_type='FREE_ONCE' AND base_price_won IS NULL AND discount_won IS NULL AND payable_won=0) OR (benefit_type='DISCOUNT' AND base_price_won IS NOT NULL AND discount_won IS NOT NULL AND base_price_won>=0 AND discount_won>=0 AND discount_won<=base_price_won AND payable_won=base_price_won-discount_won)),
  remaining_count INT NOT NULL DEFAULT 1 CHECK(remaining_count IN (0,1)),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY(pass_id) REFERENCES issued_pass(id),
  FOREIGN KEY(benefit_id) REFERENCES benefit(id),
  FOREIGN KEY(merchant_id) REFERENCES merchant(id),
  FOREIGN KEY(place_id) REFERENCES place(id),
  UNIQUE(pass_id,benefit_id),
  INDEX(merchant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE redemption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issued_benefit_id BIGINT NOT NULL UNIQUE,
  staff_user_id BIGINT NOT NULL,
  used_at DATETIME(6) NOT NULL,
  FOREIGN KEY(issued_benefit_id) REFERENCES issued_benefit(id),
  FOREIGN KEY(staff_user_id) REFERENCES app_user(id),
  INDEX(used_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  redemption_id BIGINT NOT NULL UNIQUE,
  rating INT NOT NULL CHECK(rating BETWEEN 1 AND 5),
  comment VARCHAR(500),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(redemption_id) REFERENCES redemption(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback_reason (
  feedback_id BIGINT NOT NULL,
  reason_code VARCHAR(16) NOT NULL CHECK(reason_code IN ('INTEREST','COMPANION_FIT','COST','GUIDANCE')),
  PRIMARY KEY(feedback_id,reason_code),
  FOREIGN KEY(feedback_id) REFERENCES feedback(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback_theme (
  feedback_id BIGINT NOT NULL,
  theme_id BIGINT NOT NULL,
  PRIMARY KEY(feedback_id,theme_id),
  FOREIGN KEY(feedback_id) REFERENCES feedback(id) ON DELETE CASCADE,
  FOREIGN KEY(theme_id) REFERENCES theme(id),
  INDEX(theme_id,feedback_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ontology_revision (
  id INT PRIMARY KEY CHECK(id=1),
  revision BIGINT NOT NULL DEFAULT 1 CHECK(revision>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journey (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  travel_date DATE,
  status VARCHAR(10) NOT NULL DEFAULT 'PLANNING' CHECK(status IN ('PLANNING','ARCHIVED')),
  issued_pass_id BIGINT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(owner_id) REFERENCES app_user(id),
  FOREIGN KEY(issued_pass_id) REFERENCES issued_pass(id),
  INDEX(owner_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journey_place (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  journey_id BIGINT NOT NULL,
  place_id BIGINT NOT NULL,
  position INT NOT NULL CHECK(position>=1),
  place_name VARCHAR(100) NOT NULL,
  region_name VARCHAR(100) NOT NULL,
  location_description VARCHAR(500) NOT NULL,
  material_kind VARCHAR(8) NOT NULL CHECK(material_kind IN ('REAL','DEMO')),
  visited_on DATE,
  note VARCHAR(500),
  rating INT CHECK(rating BETWEEN 1 AND 5),
  CHECK(rating IS NULL OR visited_on IS NOT NULL),
  FOREIGN KEY(journey_id) REFERENCES journey(id),
  FOREIGN KEY(place_id) REFERENCES place(id),
  UNIQUE(journey_id,place_id),
  INDEX(journey_id,position,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
