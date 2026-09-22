-- Existing databases only; apply once. No existing rows are changed.
SET NAMES utf8mb4;
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
