-- P17 (docs/offline-admission/public-admission-plan.md, decision P17, superseding P5): a batch is offline and carries no exams or
-- packages. Exams and packages are chosen per student in the admission setup, as before. The batch-level defaults table created in
-- V93 is dropped; nothing else references it. V93 and V94 are not edited.

DROP TABLE IF EXISTS offline_batch_items;
