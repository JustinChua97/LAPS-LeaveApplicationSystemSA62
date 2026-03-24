-- Test seed data (H2-compatible — no ON CONFLICT syntax)
-- Safe to use plain INSERT because ddl-auto=create-drop gives a fresh schema each run

INSERT INTO leave_types (name, description, max_days_per_year, half_day_allowed, active) VALUES
    ('Annual', 'Annual leave entitlement based on designation', 21, false, true),
    ('Medical', 'Medical leave - certificate required for more than 2 consecutive days', 60, false, true),
    ('Compensation', 'Compensation leave earned from overtime work (4 hrs = 0.5 day)', 999, true, true);

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
    ('2026-12-25', 'Christmas Day', 2026);
