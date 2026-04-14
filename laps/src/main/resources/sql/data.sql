-- ============================================================
-- LAPS - Leave Application Processing System
-- Static Seed Data (non-sensitive) for PostgreSQL
-- User accounts are created by DataInitializer.java at startup
-- ============================================================

-- Leave Types
INSERT INTO leave_types (name, description, max_days_per_year, half_day_allowed, active)
VALUES
    ('Annual', 'Annual leave entitlement based on designation', 21, false, true),
    ('Medical', 'Medical leave - certificate required for more than 2 consecutive days', 60, false, true),
    ('Compensation', 'Compensation leave earned from overtime work (4 hrs = 0.5 day)', 36, true, true)
ON CONFLICT (name) DO NOTHING;

-- Compensation leave is capped at a maximum of 36 days. 
-- Employees can only work up to 12 hours a day; with a cap of 72 overtime hours a month, or 864 overtime hours in a year.
-- This works out to 864 / 24 = 36 days in a year that they can claim back compensation leave. 

-- ============================================================
-- Singapore Public Holidays 2026
-- ============================================================
INSERT INTO public_holidays (holiday_date, description, year) VALUES
    ('2026-01-01', 'New Year''s Day', 2026),
    ('2026-01-29', 'Chinese New Year', 2026),
    ('2026-01-30', 'Chinese New Year Holiday', 2026),
    ('2026-03-20', 'Hari Raya Puasa', 2026),
    ('2026-04-03', 'Good Friday', 2026),
    ('2026-05-01', 'Labour Day', 2026),
    ('2026-05-19', 'Vesak Day', 2026),
    ('2026-05-27', 'Hari Raya Haji', 2026),
    ('2026-08-09', 'National Day', 2026),
    ('2026-10-20', 'Deepavali', 2026),
    ('2026-12-25', 'Christmas Day', 2026)
ON CONFLICT (holiday_date) DO NOTHING;
