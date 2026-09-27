-- Voting Management System schema (MySQL 8 and H2 in MySQL mode).
-- Contains no data. Demo data: demo-data.sql (synthetic only).

CREATE TABLE IF NOT EXISTS constituencies (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  district      VARCHAR(80)  NOT NULL,
  name          VARCHAR(120) NOT NULL,
  CONSTRAINT uq_constituency UNIQUE (district, name)
);

CREATE TABLE IF NOT EXISTS users (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(190) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,           -- PBKDF2-HMAC-SHA256, salted (see PasswordHasher)
  full_name       VARCHAR(120) NOT NULL,
  mobile          VARCHAR(20),
  role            VARCHAR(16)  NOT NULL,           -- ADMIN | VOTER | CANDIDATE
  constituency_id INT,
  status          VARCHAR(16)  NOT NULL,           -- PENDING | APPROVED | REJECTED
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_user_email UNIQUE (email),
  CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'VOTER', 'CANDIDATE')),
  CONSTRAINT ck_user_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT fk_user_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (id)
);

CREATE TABLE IF NOT EXISTS elections (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(160) NOT NULL,
  status      VARCHAR(16)  NOT NULL,               -- DRAFT | OPEN | CLOSED
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_election_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS nominations (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  election_id       INT          NOT NULL,
  candidate_user_id INT          NOT NULL,
  constituency_id   INT          NOT NULL,
  party             VARCHAR(120) NOT NULL,
  status            VARCHAR(16)  NOT NULL,         -- PENDING | APPROVED | REJECTED
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_nomination UNIQUE (election_id, candidate_user_id),
  CONSTRAINT ck_nomination_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT fk_nom_election FOREIGN KEY (election_id) REFERENCES elections (id),
  CONSTRAINT fk_nom_candidate FOREIGN KEY (candidate_user_id) REFERENCES users (id),
  CONSTRAINT fk_nom_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (id)
);

-- Who has voted (one row per voter per election). The primary key is the
-- database-level guarantee against double voting.
CREATE TABLE IF NOT EXISTS participation (
  election_id    INT NOT NULL,
  voter_user_id  INT NOT NULL,
  PRIMARY KEY (election_id, voter_user_id),
  CONSTRAINT fk_part_election FOREIGN KEY (election_id) REFERENCES elections (id),
  CONSTRAINT fk_part_voter FOREIGN KEY (voter_user_id) REFERENCES users (id)
);

-- What was voted. Deliberately no voter reference. Each ballot is linked to
-- the previous ballot of the same election by a SHA-256 hash chain, so
-- later edits or deletions are detectable (tamper-evident, not tamper-proof).
CREATE TABLE IF NOT EXISTS ballots (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  election_id     INT         NOT NULL,
  seq             INT         NOT NULL,
  nomination_id   INT         NOT NULL,
  constituency_id INT         NOT NULL,
  prev_hash       CHAR(64)    NOT NULL,
  hash            CHAR(64)    NOT NULL,
  CONSTRAINT uq_ballot_seq UNIQUE (election_id, seq),
  CONSTRAINT fk_ballot_election FOREIGN KEY (election_id) REFERENCES elections (id),
  CONSTRAINT fk_ballot_nomination FOREIGN KEY (nomination_id) REFERENCES nominations (id),
  CONSTRAINT fk_ballot_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (id)
);
