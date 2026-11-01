-- Sample reviews for testing the reviews feature. Dev database only. Run it after the API has started once with V56.
--
--   cd docs/exam-site/qa/scripts
--   psql -h localhost -U postgres -d coaching_center_dev -f seed-reviews.sql
--   (run it from this folder: it includes remove-reviews.sql by relative path)
--
-- It adds four sample students (+8801788000001 to +8801788000004, no password, they cannot sign in), each with a submitted
-- sitting of the dev exam "BJS Written Mock: Criminal Law" (e0000000-...-0002). Three of them also hold the "Bar Council
-- Complete Preparation Package" and sat its written mock. It also gives Student 1 (+8801711000011) a submitted sitting of that
-- written mock, so Student 1 can review the package as well. Student 1 and Student 2 have NO review yet, so you can write
-- one yourself.
--
-- Exam reviews (BJS Written Mock: Criminal Law): 5 stars with a comment, approved and featured; 4 stars with a comment,
-- waiting for review; 5 stars alone, approved; 2 stars with a comment, hidden. The average counts three (5, 4, 5), so it shows.
-- Package reviews: 5 stars approved and featured, 4 stars approved, 5 stars waiting for review.
--
-- Safe to run again: it removes its own rows first. remove-reviews.sql removes everything it made.

\i remove-reviews.sql

-- Sample students
insert into users (id, phone, email, role, status, created_at, updated_at) values
  ('5a000000-0000-4000-8000-000000000001', '+8801788000001', 'sample.review1@example.com', 'STUDENT', 'ACTIVE', now(), now()),
  ('5a000000-0000-4000-8000-000000000002', '+8801788000002', 'sample.review2@example.com', 'STUDENT', 'ACTIVE', now(), now()),
  ('5a000000-0000-4000-8000-000000000003', '+8801788000003', 'sample.review3@example.com', 'STUDENT', 'ACTIVE', now(), now()),
  ('5a000000-0000-4000-8000-000000000004', '+8801788000004', 'sample.review4@example.com', 'STUDENT', 'ACTIVE', now(), now());
insert into student_profiles (user_id, full_name, student_code) values
  ('5a000000-0000-4000-8000-000000000001', 'Farhana Akter', 'SMP-RV001'),
  ('5a000000-0000-4000-8000-000000000002', 'Rakib Hasan', 'SMP-RV002'),
  ('5a000000-0000-4000-8000-000000000003', 'Nusrat Jahan', 'SMP-RV003'),
  ('5a000000-0000-4000-8000-000000000004', 'Mehedi Rahman', 'SMP-RV004');

-- Submitted sittings of the written mock "Criminal Law (Penal Code)" for the four sample students, and of the package's
-- written mock for three of them and for Student 1.
insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, status, attempt_no, created_at, updated_at)
select ('7a000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'e0000000-0000-4000-8000-000000000002', ('5a000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'LIVE', timestamptz '2026-10-29 10:00+06', timestamptz '2026-10-29 10:30+06', timestamptz '2026-10-29 10:25+06',
       'SUBMITTED', 1, now(), now()
from generate_series(1, 4) as n;

insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, status, attempt_no, created_at, updated_at)
select ('7a000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid,
       'e0000000-0000-4000-8000-000000000006', ('5a000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'LIVE', timestamptz '2026-10-29 11:00+06', timestamptz '2026-10-29 11:30+06', timestamptz '2026-10-29 11:25+06',
       'SUBMITTED', 1, now(), now()
from generate_series(1, 3) as n;

insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, status, attempt_no, created_at, updated_at) values
  ('7a000000-0000-4000-8000-000000000199', 'e0000000-0000-4000-8000-000000000006', '50000000-0000-4000-8000-000000000001',
   'LIVE', timestamptz '2026-10-29 11:00+06', timestamptz '2026-10-29 11:30+06', timestamptz '2026-10-29 11:25+06', 'SUBMITTED', 1, now(), now());

-- The three sample students hold the package (a manual grant, as staff would give).
insert into live_exam_grants (id, student_id, scope, module_id, created_at, source, reason)
select ('6a000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       ('5a000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'MODULE', 'b0000000-0000-4000-8000-000000000001', now(), 'MANUAL', 'Sample data for the reviews test'
from generate_series(1, 3) as n;

-- Exam reviews
insert into live_exam_reviews (id, exam_id, module_id, student_id, rating, comment, status, hide_reason, featured, moderated_at, created_at, updated_at) values
  ('f1000000-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000002', null, '5a000000-0000-4000-8000-000000000001', 5,
   'প্রশ্নগুলো পরীক্ষার মানের সঙ্গে মিলে গেছে। উত্তর লেখার সময়টাও যথেষ্ট ছিল, আত্মবিশ্বাস বেড়েছে।', 'APPROVED', null, true,
   now() - interval '2 days', now() - interval '3 days', now() - interval '2 days'),
  ('f1000000-0000-4000-8000-000000000002', 'e0000000-0000-4000-8000-000000000002', null, '5a000000-0000-4000-8000-000000000002', 4,
   'Good paper, but the second question was longer than the time allowed for it.', 'PENDING', null, false,
   null, now() - interval '1 day', now() - interval '1 day'),
  ('f1000000-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-000000000002', null, '5a000000-0000-4000-8000-000000000003', 5,
   null, 'APPROVED', null, false, null, now() - interval '20 hours', now() - interval '20 hours'),
  ('f1000000-0000-4000-8000-000000000004', 'e0000000-0000-4000-8000-000000000002', null, '5a000000-0000-4000-8000-000000000004', 2,
   'This comment was written to test hiding a review.', 'HIDDEN', 'Not about the exam', false,
   now() - interval '10 hours', now() - interval '12 hours', now() - interval '10 hours');

-- Package reviews
insert into live_exam_reviews (id, exam_id, module_id, student_id, rating, comment, status, hide_reason, featured, moderated_at, created_at, updated_at) values
  ('f1000000-0000-4000-8000-000000000011', null, 'b0000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000001', 5,
   'এক প্যাকেজেই এমসিকিউ ও রিটেন দুটোর প্রস্তুতি হয়ে যায়। দাম অনুযায়ী খুবই ভালো।', 'APPROVED', null, true,
   now() - interval '36 hours', now() - interval '2 days', now() - interval '36 hours'),
  ('f1000000-0000-4000-8000-000000000012', null, 'b0000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000002', 4,
   'Well organised. I would like a few more MCQ sets in it.', 'APPROVED', null, false,
   now() - interval '30 hours', now() - interval '30 hours', now() - interval '30 hours'),
  ('f1000000-0000-4000-8000-000000000013', null, 'b0000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000003', 5,
   'Waiting for review: the written mock feedback was detailed.', 'PENDING', null, false,
   null, now() - interval '5 hours', now() - interval '5 hours');

select status, count(*) from live_exam_reviews where id::text like 'f1000000-0000-4000-8000-%' group by status order by status;
