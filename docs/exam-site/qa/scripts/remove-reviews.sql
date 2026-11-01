-- Removes everything seed-reviews.sql made: the sample reviews, sittings, grants and the four sample students. Dev database only.
-- Student 1's sample sitting of the package's written mock goes too. A review Student 1 wrote about the package after that
-- stays (delete it from the staff Reviews page, or as Student 1).
delete from live_exam_reviews where id::text like 'f1000000-0000-4000-8000-%';
delete from live_exam_reviews where student_id::text like '5a000000-0000-4000-8000-%';
delete from live_exam_grants where id::text like '6a000000-0000-4000-8000-%';
delete from live_exam_participants where id::text like '7a000000-0000-4000-8000-%';
delete from student_profiles where user_id::text like '5a000000-0000-4000-8000-%';
delete from users where id::text like '5a000000-0000-4000-8000-%';
