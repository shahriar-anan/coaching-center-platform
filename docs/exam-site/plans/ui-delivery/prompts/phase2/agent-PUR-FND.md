# Agent PUR-FND (phase 2): shared purchase components

You are agent **PUR-FND**. You build the shared purchase components that the student purchase pages use. STU-PUR and STU-LMS depend on your stubs, so your **first commit is the stubs** (exact props below), and you tell the integrator when it is committed. Read [README.md](README.md) first (rules, lock, ready message).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/pur-fnd -b ui/pur-fnd catalog-acquisition`
2. Work only inside `F:\cc-wt\pur-fnd`. Run `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: sections 2 (decisions), 4 (S-005 and the purchase findings), 7 (style) and 9 (your row).
2. `docs/exam-site/ui/student-flow-prompts.md`, Prompt 2: the purchase states and the confirm-purchase copy.
3. `docs/exam-site/ui/client-mockup.html` (the look) and `docs/exam-site/plans/ui-delivery/bangla-copy.md`.
4. The FND components you reuse: `web/src/components/ui/` (`dialog.tsx`, `pill.tsx`, `button.tsx`, `sticky-action-bar.tsx`, `field.tsx`, `card.tsx`) and `web/src/components/ui/print-area.tsx` for the existing pattern.

## Owned files

- `web/src/components/ui/purchase/**` (new folder for the components below)
- `web/src/components/ui/index.ts` only if it already exists; otherwise import by path.
- `web/tests/pur-fnd/**` (new)
- `web/src/i18n/messages/{bn,en}/common.ts`: add keys only, do not change existing ones. Namespace prefix `purchase.shared.*` for everything you add.
- Your section in `ws9-gaps-and-polish.md`.

Do not touch any page file, any `components/student/**` file, or `lib/api/**`.

## Stub contract (commit first, exact names and props)

Export from `@/components/ui/purchase/<file>` as named exports:

| Export | File | Props |
|---|---|---|
| `PriceBar` | `price-bar.tsx` | `price: number \| null; priceNote?: string; status?: ReactNode; action?: ReactNode; secondary?: ReactNode` |
| `AccessBadge` | `access-badge.tsx` | `via: "MODULE" \| "EXAM" \| "ROSTER" \| "MANUAL"` |
| `ConfirmSheet` | `confirm-sheet.tsx` | `open: boolean; onOpenChange(open: boolean): void; title: string; amountLabel: string; unlock: string; primary: ReactNode; cancelLabel: string; leavingNote?: string; children?: ReactNode` |
| `SelectableExamList` | `selectable-exam-list.tsx` | `items: { id: string; title: string; meta?: ReactNode; price: number \| null; disabledReason?: string }[]; selected: string[]; onSelectedChange(ids: string[]): void; summaryLabel(count: number, total: number \| null): string; action: ReactNode` |
| `ProductCard` | `product-card.tsx` | `href: string; title: string; subtitle?: string; pills?: ReactNode; price: number \| null; examCount: number; footer?: ReactNode` |
| `ConfigNotice` | `config-notice.tsx` | `show: boolean` (renders nothing when false; `role="status"` when true) |

The stub may render plain placeholder markup and must compile. Commit it, then send the integrator the message "PUR-FND stubs committed: `<hash>`".

## Items

**P-1. PriceBar.** The price bar at the top of an offer. Shows the price (formatted, Bengali digits), or "দাম এখনো নির্ধারণ হয়নি।" when `price` is null, with `priceNote` as an extra line. `status` and `action` go to the right on desktop and below the price on phones. Tokens only.

**P-2. AccessBadge.** A green (`ok`) pill with "অ্যাক্সেস আছে". The `via` prop chooses an extra small label: MODULE "মডিউল", EXAM "এই পরীক্ষা", ROSTER "রোস্টার", MANUAL "প্রশাসক দিয়েছেন". Use FND's `Pill` tone `ok`.

**P-3. ConfirmSheet.** The purchase confirmation. On phones it slides up from the bottom (full width, safe-area padding); on desktop it is a centred dialog. Built on FND's `Dialog`. Shows the title, the amount, the unlock sentence, the primary button, and an outline cancel button. `leavingNote` is shown under the buttons. Focus goes to the primary on open and returns to the trigger on close. Escape closes it. Tests: open and close, focus, Escape, and that the primary is disabled while `primary` is a loading state.

**P-4. SelectableExamList.** Rows with a native checkbox (keyboard works without extra code). A row with `disabledReason` is shown greyed out, with the reason as text (not only a tooltip), and its checkbox disabled. A sticky summary bar (FND's `StickyActionBar`) shows `summaryLabel(count, total)` and the `action`. When `total` is null (some exams have no price), the summary shows the count only. Tests: select, deselect, disabled row, summary with and without total, keyboard toggle with Space.

**P-5. ProductCard.** A card (16 px radius, border, hover lift and gold border, as the mockup `.card`), with the title, subtitle, pills, exam count, price line ("৳ …" or "দাম এখনো নির্ধারণ হয়নি।"), and footer. The whole card is a link (`href`). Tests: link target, price line both ways, count formatting.

**P-6. ConfigNotice.** A banner with "পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।" (use this text unless `bangla-copy.md` gives an approved wording). `role="status"`. Renders nothing when `show` is false. Test both.

**P-7. Shared copy.** Add the keys the components need under `purchase.shared.*` in `common.ts`, in Bangla and English. Do not add a new namespace.

**P-8. Toast size (S-029), check only.** FND already changed `web/src/components/ui/toast.tsx` to `text-label` (commit `a453d4c`, merged). Check that the line has no `text-[15px]` and that the toast renders at that class; add the test. No other change to the toast; props stay the same.

## Done

- Each item has tests in `web/tests/pur-fnd/**`.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `PURFND`.
- No raw colours, no `text-[..]`, no `text-sm` in your files.
- Your report section, then the ready message from the README.
- Stub commit reported to the integrator in the first hour.
