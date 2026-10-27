# Agent STU-PUR (phase 2): module, exam and purchase pages

You are agent **STU-PUR**. You build the student purchase flow on the module and exam pages: the access state from the API, the purchase confirmation, the config notice, the selection flow for several exams, and the upcoming-row copy. Read [README.md](README.md) first (rules, lock, ready message).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/stu-pur -b ui/stu-pur catalog-acquisition`
2. Work only inside `F:\cc-wt\stu-pur`. Run `npm ci` in `web/`.
3. PUR-FND is building the shared components at the same time. Until its stubs are merged, import them from your own stand-in folder `web/src/components/student/stand-ins-pur/` with the same names and props (see PUR-FND's prompt, "Stub contract"). When the integrator says PUR-FND is merged, switch the imports to `@/components/ui/purchase/...` and delete the folder in a commit named "switch to PUR-FND components".

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 4 (findings S-001, S-003, S-005, S-010 and S-011, S-014), section 5 (who can do what), section 8 (the contract shapes you code against), section 9 (your row).
2. `docs/exam-site/ui/student-flow-prompts.md`, Prompt 2: every purchase state, the confirm copy, the phone-required copy, and the "buy the module, exam offer" states. Follow the copy there.
3. `docs/exam-site/ui/client-mockup.html` (the look) and `bangla-copy.md`.
4. The current code you change: `web/src/components/student/catalog/` (`module-page.tsx`, `exam-page.tsx`, `door-discovery.tsx`, `catalog-helpers.ts`), `web/src/components/student/purchase/` (`purchase-sheet.tsx`, `purchase-result.tsx`), `web/src/lib/api/exam/catalog.ts` and `purchase.ts`, and `web/src/i18n/messages/{bn,en}/catalog.ts` and `purchase.ts`.

## Owned files

- `web/src/components/student/catalog/**` **except** `my-modules-page.tsx` (STU-LMS owns that file)
- `web/src/components/student/purchase/**`
- `web/src/components/student/stand-ins-pur/**` (temporary, deleted at the switch)
- `web/src/app/(site)/mcq/**`, `web/src/app/(site)/written/**`, `web/src/app/(site)/exam/**` (page wrappers only, if they need a change)
- `web/src/app/(site)/account/payments/**` (receipt and history, if needed)
- `web/src/lib/api/exam/catalog.ts`, `web/src/lib/api/exam/purchase.ts`
- `web/src/i18n/messages/{bn,en}/catalog.ts`, `{bn,en}/purchase.ts`
- `web/tests/pur-stu/**` (new)
- Your section in `ws9-gaps-and-polish.md`

Do not touch `notices/**`, `components/student/result/**`, `components/student/home/**`, `my-modules-page.tsx`, `lib/api/exam/notices.ts`, `sitting.ts`, or `messages/*/notices.ts`, `result.ts`, `sitting.ts`. Those belong to STU-LMS.

## Access state (contract: plan section 8)

The `access` object on the public module and exam responses (present for a signed-in student) is `{ state: "NONE" | "MODULE" | "EXAM" | "ROSTER" | "MANUAL", buyable: boolean, blockedReason: null | "PHONE_REQUIRED" | "PRICE_REQUIRED" | "ALREADY_HAS_ACCESS" | "CHECKOUT_OFF" }`. The status endpoint is `GET /api/v1/public/exam-site/status` with `{ checkoutEnabled: boolean }`.

- Build one function `resolveAccess` in `catalog.ts`: it reads `access` when present. When it is absent (older API), it falls back to the current probe behaviour, and it is marked with a comment `// fallback until A1 access object is merged; delete after`.
- Remove the probes (`useMyExam`, `useMyModule` used for ownership) once the integrator says A1 is merged. Do this in a separate commit named for S-003.
- Until then, tests use mocked responses in the new shape.

## Items

**S-003. Ownership from the access object.** Module and exam pages read `access.state` and `access.blockedReason`. Owned states show the `AccessBadge` (PUR-FND) and no buy buttons, as in the spec: "অ্যাক্সেস আছে", with "এই মডিউলের সব পরীক্ষা চালু আছে" on a module-owned page. A `MANUAL` state reads the same way with the "প্রশাসক দিয়েছেন" label. Tests: each state, and that no probe request is made when `access` is present.

**S-005. Config notice.** Module and exam pages call the status endpoint once (cached 60 s). When `checkoutEnabled` is false: `ConfigNotice` (PUR-FND) shows, and every buy button is disabled. Tests: enabled and disabled; a missing status endpoint means enabled (older API) and the page says nothing.

**S-001. Several exams in one payment (decision D1).** On the module page's exam list, `SelectableExamList` (PUR-FND) lets the student select exams that they can still buy. The summary shows the count and the total, and the action is "বেছে নেওয়া পরীক্ষা কিনুন". It calls the bundle order (`POST /api/v1/live-exam-orders` with `{ examIds }`) through a new `useBundleOrder` in `purchase.ts`. Put the whole selection behind the config flag `bundleOrders` in `lib/config` (default false). Turn it on only after the integrator says A4 is merged. Tests: selection with a total, a row with no price disables the total, a bundle with one owned exam shows the `ALREADY_PURCHASED` message and charges nothing.

**S-010, ST-2 (partial, finish it). The upcoming row.** In `door-discovery.tsx`, a row with a price shows it. A row with no price and `moduleOnly` (the exam is sold only inside a module) shows "শুধু মডিউলে কেনা যাবে". A row that is live now shows "চালু আছে" (the copy `catalog.upcoming.live` exists in English as "Live now"; add the Bangla key). The "price not set" text is not used on this row. The API sends `moduleOnly` on each upcoming exam (S-027, A1). Read it into the `UpcomingExam` type. Until A1 is merged, the field is missing: the row then shows no price line (current behaviour), and the report says so. Tests for the three cases.

**S-011 (done in STU; check only).** Confirm the search still filters by the door type. No change unless the test fails.

**Confirm sheet copy.** Replace the module unlock sentence in `purchase.ts` with the spec wording: "এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে।" (student-flow-prompts, Prompt 2). Exam: "শুধু এই পরীক্ষাটি খুলবে।" Use `ConfirmSheet` (PUR-FND) for the sheet, keeping the existing lines: amount, "পেমেন্টে যান", "বাতিল", and the SSLCommerz note.

**Blocked reasons.** Map each `blockedReason` to copy and to the disabled control: `PHONE_REQUIRED` gives the set-phone link ("কেনার আগে একটি ফোন নম্বর লাগবে। পরে বদলানো যাবে না।"); `PRICE_REQUIRED` gives "দাম এখনো নির্ধারণ হয়নি।"; `ALREADY_HAS_ACCESS` gives the badge; `CHECKOUT_OFF` gives `ConfigNotice`.

**Module page exam list.** Keep the date and price per exam. Rows that the student already has show the badge, not a buy link. Rows with no price show "দাম এখনো নির্ধারণ হয়নি।" and no buy link.

**Module page "কী পাবেন".** Keep the existing summary (U11).

**Unused export (from FND's clean-up).** `useOrder` in `web/src/lib/api/exam/purchase.ts` has no user. Remove it, unless your work starts using it, and say which in your report.

**Size fix (from the STU follow-up).** `components/student/catalog/module-page.tsx` line 67 uses `text-2xl`. Replace it with the FND scale class for that role. This file is yours, so the STU follow-up leaves it alone.

**Product cards in the door and category pages.** Use `ProductCard` (PUR-FND) where a module card is shown, with the exam count and the price line. Keep the links.

## Done

- Each item has tests in `web/tests/pur-stu/**`. Every state in the spec's Prompt 2 has a test, in Bangla and English.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `STUPUR`.
- The stand-in folder is deleted once PUR-FND is merged. Until then the report says so.
- Probes removed in their own commit after A1 is merged (or the report says "waiting for A1").
- No raw colours, no `text-[..]`, no `text-sm` in your files.
- Your report section, then the ready message from the README.
