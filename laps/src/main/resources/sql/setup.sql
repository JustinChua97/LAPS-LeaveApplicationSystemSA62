-- ============================================================
-- LAPS – Leave Application Processing System
-- PostgreSQL Setup Script
--
-- Run this ONCE against the lapsdb database:
--   psql -U postgres -d lapsdb -f setup.sql
--
-- Or create the database first:
--   psql -U postgres -c "CREATE DATABASE lapsdb;"
--   psql -U postgres -d lapsdb -f setup.sql
--
-- After running this script, start the Spring Boot app.
-- DataInitializer.java will seed employee accounts at startup.
-- ============================================================

-- ============================================================
-- EXTENSIONS
-- ============================================================
-- (none required for this schema)

-- ============================================================
-- DROP TABLES (safe re-run order: children first)
-- ============================================================
DROP TABLE IF EXISTS compensation_claims CASCADE;
DROP TABLE IF EXISTS leave_entitlements CASCADE;
DROP TABLE IF EXISTS leave_applications CASCADE;
DROP TABLE IF EXISTS public_holidays CASCADE;
DROP TABLE IF EXISTS leave_types CASCADE;
DROP TABLE IF EXISTS employees CASCADE;

-- ============================================================
-- EMPLOYEES
-- ============================================================
CREATE TABLE employees (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('ROLE_ADMIN','ROLE_MANAGER','ROLE_EMPLOYEE')),
    designation VARCHAR(30)  NOT NULL CHECK (designation IN ('ADMINISTRATIVE','PROFESSIONAL','SENIOR_PROFESSIONAL')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    manager_id  BIGINT       REFERENCES employees(id) ON DELETE SET NULL
);

-- ============================================================
-- LEAVE TYPES
-- ============================================================
CREATE TABLE leave_types (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(50)  NOT NULL UNIQUE,
    description        TEXT,
    max_days_per_year  INTEGER      NOT NULL,
    half_day_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================
-- PUBLIC HOLIDAYS
-- ============================================================
CREATE TABLE public_holidays (
    id            BIGSERIAL PRIMARY KEY,
    holiday_date  DATE        NOT NULL UNIQUE,
    description   VARCHAR(100) NOT NULL,
    year          INTEGER      NOT NULL
);

-- ============================================================
-- LEAVE APPLICATIONS
-- ============================================================
CREATE TABLE leave_applications (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id       BIGINT       NOT NULL REFERENCES leave_types(id),
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    duration            DOUBLE PRECISION NOT NULL,
    reason              TEXT         NOT NULL,
    work_dissemination  TEXT,
    contact_details     VARCHAR(200),
    status              VARCHAR(20)  NOT NULL DEFAULT 'APPLIED'
                            CHECK (status IN ('APPLIED','APPROVED','REJECTED','UPDATED','DELETED','CANCELLED')),
    manager_comment     TEXT,
    applied_date        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_date        TIMESTAMP,
    half_day            BOOLEAN      NOT NULL DEFAULT FALSE,
    half_day_type       VARCHAR(5)   CHECK (half_day_type IN ('AM','PM'))
);

-- ============================================================
-- LEAVE ENTITLEMENTS
-- ============================================================
CREATE TABLE leave_entitlements (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT           NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id  BIGINT           NOT NULL REFERENCES leave_types(id),
    year           INTEGER          NOT NULL,
    total_days     DOUBLE PRECISION NOT NULL,
    used_days      DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT uq_entitlement UNIQUE (employee_id, leave_type_id, year)
);

-- ============================================================
-- COMPENSATION CLAIMS
-- ============================================================
CREATE TABLE compensation_claims (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT           NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    overtime_date     DATE             NOT NULL,
    overtime_hours    INTEGER          NOT NULL CHECK (overtime_hours >= 1 AND overtime_hours <= 4),
    compensation_days DOUBLE PRECISION NOT NULL,
    status            VARCHAR(20)      NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    manager_comment   TEXT,
    claimed_date      TIMESTAMP        NOT NULL DEFAULT NOW(),
    processed_date    TIMESTAMP,
    reason            TEXT
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_leave_app_employee   ON leave_applications(employee_id);
CREATE INDEX idx_leave_app_status     ON leave_applications(status);
CREATE INDEX idx_leave_app_dates      ON leave_applications(start_date, end_date);
CREATE INDEX idx_entitlement_employee ON leave_entitlements(employee_id, year);
CREATE INDEX idx_comp_claim_employee  ON compensation_claims(employee_id);
CREATE INDEX idx_ph_year              ON public_holidays(year);

-- ============================================================
-- SEED DATA – Leave Types
-- ============================================================
INSERT INTO leave_types (name, description, max_days_per_year, half_day_allowed, active)
VALUES
    ('Annual',
     'Annual leave entitlement based on designation. For durations ≤14 days, weekends and public holidays are excluded. For durations >14 days, all calendar days are counted.',
     21, FALSE, TRUE),
    ('Medical',
     'Medical leave — a medical certificate is required for more than 2 consecutive days. Maximum 60 days per year.',
     60, FALSE, TRUE),
    ('Compensation',
     'Compensation leave earned from overtime work. Every 4 hours of overtime = 0.5 day compensation leave.',
     999, TRUE, TRUE)
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- SEED DATA – Singapore Public Holidays 2025
-- ============================================================
INSERT INTO public_holidays (holiday_date, description, year) VALUES
    ('2025-01-01', 'New Year''s Day',          2025),
    ('2025-01-29', 'Chinese New Year',          2025),
    ('2025-01-30', 'Chinese New Year Holiday',  2025),
    ('2025-03-31', 'Hari Raya Puasa',           2025),
    ('2025-04-18', 'Good Friday',               2025),
    ('2025-05-01', 'Labour Day',                2025),
    ('2025-05-12', 'Vesak Day',                 2025),
    ('2025-06-06', 'Hari Raya Haji',            2025),
    ('2025-08-09', 'National Day',              2025),
    ('2025-10-20', 'Deepavali',                 2025),
    ('2025-12-25', 'Christmas Day',             2025)
ON CONFLICT (holiday_date) DO NOTHING;

-- ============================================================
-- SEED DATA – Singapore Public Holidays 2026
-- ============================================================
INSERT INTO public_holidays (holiday_date, description, year) VALUES
    ('2026-01-01', 'New Year''s Day',          2026),
    ('2026-01-29', 'Chinese New Year',          2026),
    ('2026-01-30', 'Chinese New Year Holiday',  2026),
    ('2026-03-20', 'Hari Raya Puasa',           2026),
    ('2026-04-03', 'Good Friday',               2026),
    ('2026-05-01', 'Labour Day',                2026),
    ('2026-05-19', 'Vesak Day',                 2026),
    ('2026-05-27', 'Hari Raya Haji',            2026),
    ('2026-08-09', 'National Day',              2026),
    ('2026-10-20', 'Deepavali',                 2026),
    ('2026-12-25', 'Christmas Day',             2026)
ON CONFLICT (holiday_date) DO NOTHING;

-- ============================================================
-- NOTE: Employee accounts are seeded at application startup
-- by DataInitializer.java using BCrypt-encoded passwords.
-- No plaintext or pre-hashed passwords are stored here.
-- ============================================================

-- Verify
SELECT 'leave_types'    AS "table", COUNT(*) AS rows FROM leave_types
UNION ALL
SELECT 'public_holidays', COUNT(*) FROM public_holidays;
