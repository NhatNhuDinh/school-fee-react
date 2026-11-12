--liquibase formatted sql

--changeset school-fee:026-seed-academic-year-history
--comment Development data: each demo school gets four years (2023-2024 to 2026-2027) of two terms each, so the year selector and the year screens have something to switch between.

-- 2025-2026 already exists from 019. A year that is already there is left as it
-- is, which is also what makes this safe on a database where somebody added one
-- by hand.
INSERT INTO academic_years (organization_id, name, start_date, end_date, created_at)
SELECT o.id, v.name, v.start_date, v.end_date, TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00'
FROM organizations o
CROSS JOIN (VALUES
  ('2023-2024', DATE '2023-09-05', DATE '2024-05-27'),
  ('2024-2025', DATE '2024-09-05', DATE '2025-05-27'),
  ('2026-2027', DATE '2026-09-05', DATE '2027-05-27')
) AS v(name, start_date, end_date)
WHERE o.code IN ('NBK', 'CG')
ON CONFLICT ON CONSTRAINT uq_academic_years_organization_name DO NOTHING;

-- Two terms for every demo year that has none yet, on the same dates 019 gives
-- 2025-2026: the second starts the day after the first ends, so the terms touch
-- without overlapping, as the API requires.
INSERT INTO academic_periods (academic_year_id, name, start_date, end_date)
SELECT y.id, t.name,
       make_date(EXTRACT(YEAR FROM y.start_date)::int + t.start_offset, t.start_month, t.start_day),
       make_date(EXTRACT(YEAR FROM y.start_date)::int + t.end_offset,   t.end_month,   t.end_day)
FROM academic_years y
JOIN organizations o ON o.id = y.organization_id AND o.code IN ('NBK', 'CG')
CROSS JOIN (VALUES
  ('Học kỳ 1', 0, 9, 5, 1, 1, 18),
  ('Học kỳ 2', 1, 1, 19, 1, 5, 27)
) AS t(name, start_offset, start_month, start_day, end_offset, end_month, end_day)
WHERE y.name IN ('2023-2024', '2024-2025', '2026-2027')
  AND NOT EXISTS (SELECT 1 FROM academic_periods p WHERE p.academic_year_id = y.id);

--rollback DELETE FROM academic_periods WHERE academic_year_id IN (SELECT id FROM academic_years WHERE name IN ('2023-2024', '2024-2025')); DELETE FROM academic_years WHERE name IN ('2023-2024', '2024-2025');
