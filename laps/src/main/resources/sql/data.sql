-- ============================================================
-- LAPS - Leave Application Processing System
-- Static Seed Data (non-sensitive) for PostgreSQL
-- User accounts are created by DataInitializer.java at startup
-- ============================================================

-- Leave Types
INSERT INTO leave_types (name, description, max_days_per_year, half_day_allowed, active, default_type)
VALUES
    ('Annual', 'Annual leave entitlement based on designation', 21, false, true, 'ANNUAL'),
    ('Medical', 'Medical leave - certificate required for more than 2 consecutive days', 14, false, true, 'MEDICAL'),
    ('Hospitalisation', 'Hospitalisation leave - issued by the hospital. Maximum 46 days per year.', 46, false, true, 'HOSPITALISATION'),
    ('Compensation', 'Compensation leave earned from overtime work (4 hrs = 0.5 day)', 108, true, true, 'COMPENSATION')
ON CONFLICT (name) DO NOTHING;

-- Compensation leave is capped at a maximum of 108 days.
-- Employees can only work up to 12 hours a day, or 4 overtime hours a day.
-- There is a cap of 72 overtime hours a month. That means they can clock up to 9 days compensation leave.
-- (4 hours overtime = 0.5 days compensation leave, 72 hours overtime = 9 days)
-- This works out to 9 * 12 = 108 days in a year that they can claim back compensation leave.

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
