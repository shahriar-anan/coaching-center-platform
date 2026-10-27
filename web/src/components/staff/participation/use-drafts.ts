"use client";

import { useQueries } from "@tanstack/react-query";
import { moduleExamsQuery, useModules, type StaffExam, type StaffModule } from "@/lib/api/exam/staff-catalog";

/** A module or an exam the students cannot see yet because it is still a draft. */
export type DraftItem =
  | { kind: "module"; id: string; titleEn: string; titleBn: string; finallySaved: boolean; examCount: number }
  | {
      kind: "exam";
      id: string;
      moduleId: string;
      moduleTitleEn: string;
      moduleTitleBn: string;
      examType: StaffExam["examType"];
      titleEn: string;
      titleBn: string;
      liveAt: string | null;
      finallySaved: boolean;
    };

/**
 * Draft modules, and draft exams inside modules that are already published (a draft module
 * keeps all its exams back, so those show under the module). The API has no list of exams
 * across modules, so each published module's exam list is read, the way the admin agenda does.
 */
export function useDraftContent(): { items: DraftItem[]; pending: boolean; failed: boolean } {
  const modules = useModules();
  const list: StaffModule[] = modules.data?.content ?? [];
  const published = list.filter((entry) => entry.status === "PUBLISHED");
  const exams = useQueries({ queries: published.map((entry) => moduleExamsQuery(entry.id)) });

  const items: DraftItem[] = [];
  for (const draft of list.filter((entry) => entry.status === "DRAFT")) {
    items.push({
      kind: "module",
      id: draft.id,
      titleEn: draft.titleEn,
      titleBn: draft.titleBn,
      finallySaved: draft.finallySaved,
      examCount: (draft.mcqCount ?? 0) + (draft.writtenCount ?? 0),
    });
  }
  published.forEach((owner, index) => {
    for (const exam of exams[index]?.data?.content ?? []) {
      if (exam.status === "DRAFT") {
        items.push({
          kind: "exam",
          id: exam.id,
          moduleId: owner.id,
          moduleTitleEn: owner.titleEn,
          moduleTitleBn: owner.titleBn,
          examType: exam.examType,
          titleEn: exam.titleEn,
          titleBn: exam.titleBn,
          liveAt: exam.liveAt ?? null,
          finallySaved: exam.finallySaved,
        });
      }
    }
  });
  // Ready to publish first: those need one click, the others need work.
  items.sort((a, b) => Number(b.finallySaved) - Number(a.finallySaved));

  return {
    items,
    pending: modules.isPending || exams.some((query) => query.isPending),
    failed: modules.isError || exams.some((query) => query.isError),
  };
}
