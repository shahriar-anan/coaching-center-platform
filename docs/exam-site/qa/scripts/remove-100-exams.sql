-- Removes the exams made by seed-100-exams.sql. Dev database only.
delete from live_exams where id::text like 'e1000000-0000-4000-8000-%';
select count(*) as remaining_sample_exams from live_exams where id::text like 'e1000000-0000-4000-8000-%';
