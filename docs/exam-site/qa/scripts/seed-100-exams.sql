-- 100 published sample exams for checking pagination, scrolling and the All / Free / Live / Archived tabs.
-- Dev database only (coaching_center_dev). Safe to run again: it removes its own rows first.
-- Ids are e1000000-0000-4000-8000-0000000000NN, so remove-100-exams.sql can find them.
--
-- Where they go (4 dev categories):
--   i  1..60  Advocate Enrolment Exam      (child of Bar Council Enrolment)
--   i 61..80  Bar Council Enrolment        (parent)
--   i 81..90  Bangladesh Judicial Service  (parent)
--   i 91..100 Assistant Judge Recruitment  (child of BJS)
-- Type: every 4th exam is WRITTEN, the rest MCQ.
-- Window (by i mod 5): 0 practice (no window), 1 ended, 2 upcoming, 3 running now, 4 ended.
-- Price (by i mod 3): 0 free, 1 paid, 2 paid with a 10..40 % discount that ends in 20 days.
begin;

delete from live_exams where id::text like 'e1000000-0000-4000-8000-%';

insert into live_exams (
  id, module_id, exam_type, title_en, title_bn, description_en, description_bn, rules_en, rules_bn,
  live_at, ends_at, duration_minutes, pass_percent, written_maximum, status, finally_saved,
  created_at, updated_at, paper_language, price, category_id, instructor_id, mode, published_at,
  answers_changeable, is_free, discount_percent, discount_ends_at, popular, preview_enabled
)
select
  ('e1000000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
  null,
  case when g.i % 4 = 0 then 'WRITTEN' else 'MCQ' end,
  'Sample Exam ' || lpad(g.i::text, 3, '0') || ': ' ||
    (array['Civil Procedure','Criminal Law','Evidence Act','Constitutional Law','Contract Act','Penal Code','Family Law','Land Law','Company Law','Legal Ethics'])[1 + g.i % 10] ||
    case when g.i % 5 = 0 then ' Practice Set' else ' Model Test' end,
  'নমুনা পরীক্ষা ' || translate(lpad(g.i::text, 3, '0'), '0123456789', '০১২৩৪৫৬৭৮৯'),
  'Sample exam for testing the lists.',
  'তালিকা পরীক্ষার জন্য নমুনা পরীক্ষা।',
  'Sample rules.',
  'নমুনা নিয়ম।',
  case g.i % 5
    when 0 then null
    when 1 then now() - interval '30 days'
    when 2 then now() + (g.i % 9 + 1) * interval '1 day'
    when 3 then now() - interval '1 day'
    else now() - interval '40 days'
  end,
  case g.i % 5
    when 0 then null
    when 1 then now() - interval '20 days'
    when 2 then now() + (g.i % 9 + 1) * interval '1 day' + interval '30 days'
    when 3 then now() + interval '7 days'
    else now() - interval '30 days'
  end,
  20 + (g.i % 5) * 10,
  40,
  case when g.i % 4 = 0 then 50 else null end,
  'PUBLISHED',
  true,
  now(),
  now(),
  'BN',
  case g.i % 3 when 0 then null when 1 then 200 + (g.i % 7) * 50 else 300 + (g.i % 5) * 100 end,
  case
    when g.i <= 60 then 'a0000000-0000-4000-8000-000000000004'
    when g.i <= 80 then 'a0000000-0000-4000-8000-000000000003'
    when g.i <= 90 then 'a0000000-0000-4000-8000-000000000001'
    else 'a0000000-0000-4000-8000-000000000002'
  end::uuid,
  case when g.i % 4 = 0 then '60000000-0000-4000-8000-000000000001'::uuid else null end,
  case when g.i % 5 = 0 then 'PRACTICE' else 'LIVE' end,
  now() - interval '1 day',
  true,
  g.i % 3 = 0,
  case when g.i % 3 = 2 then 10 + (g.i % 4) * 10 else null end,
  case when g.i % 3 = 2 then now() + interval '20 days' else null end,
  false,
  false
from generate_series(1, 100) as g(i);

commit;

select 'total sample exams' as what, count(*) from live_exams where id::text like 'e1000000-0000-4000-8000-%'
union all select 'in Advocate Enrolment', count(*) from live_exams where id::text like 'e1000000-%' and category_id = 'a0000000-0000-4000-8000-000000000004'
union all select 'MCQ', count(*) from live_exams where id::text like 'e1000000-%' and exam_type = 'MCQ'
union all select 'free', count(*) from live_exams where id::text like 'e1000000-%' and is_free
union all select 'practice', count(*) from live_exams where id::text like 'e1000000-%' and mode = 'PRACTICE';
