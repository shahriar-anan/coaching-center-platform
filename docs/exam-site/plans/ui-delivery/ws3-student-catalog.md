# WS3 — Student home, catalog, exam page, purchase

Branch `ui/ws3-student-catalog`, agent C, from the start. Build against the WS0 and WS1 **Contracts** until those merge, then merge `catalog-acquisition` into your branch. Read [README.md](README.md) and [ws0-foundation.md](ws0-foundation.md). Layout and copy follow the client mockup ([../../ui/client-mockup.html](../../ui/client-mockup.html)) and Prompt 2 of [../../ui/student-flow-prompts.md](../../ui/student-flow-prompts.md), with the changes below. This is the screen the client will look at first, so match the mockup's look closely and improve only where this file says.

**Copy:** Bangla quoted in this file shows intent only. Write the final Bangla by [bangla-copy.md](bangla-copy.md) (its glossary and rewrites win, for example লগইন not প্রবেশ, সরাসরি ভর্তি not রোস্টার, শিক্ষক not ইনস্ট্রাক্টর, তারিখ পরে জানানো হবে). Build every link to the other surface with `siteHref()` or `staffHref()` from `lib/config`.

## Owns

- `web/src/app/(site)/page.tsx` (home; WS0 leaves a placeholder), `web/src/app/(site)/mcq/**`, `web/src/app/(site)/written/**`
- `web/src/app/(site)/exam/[examId]/page.tsx` (the exam page; `/sit`, `/result`, `/leaderboard` under it belong to WS4)
- `web/src/app/(site)/purchase/result/**` (move the existing `web/src/app/purchase/result` here and delete the old folder), `web/src/app/(site)/my-modules`, `web/src/app/(site)/routine`
- `web/src/components/student/catalog/**`, `web/src/components/student/purchase/**`, `web/src/components/student/home/**`
- `web/src/lib/api/exam/catalog.ts`, `web/src/lib/api/exam/purchase.ts`
- `web/src/i18n/messages/{bn,en}/{catalog,purchase,home}.ts`
- Tests under `web/tests/ws3/**`

## Routes

| Route | Page |
|---|---|
| `/` | Home: hero and the two doors; signed-in dashboard sections below |
| `/mcq`, `/written` | Door: the root categories for that type |
| `/mcq/c/[categoryId]`, `/written/c/[categoryId]` | Category: child categories and modules (the mockup's "batches"), or "শীঘ্রই আসছে" |
| `/mcq/m/[moduleId]`, `/written/m/[moduleId]` | Module: routine, status strip, and exam list for that type only |
| `/exam/[examId]` | Exam page: offer, owned states, and the action into the sitting |
| `/my-modules` | The student's modules (all types), with progress |
| `/routine` | Agenda of the student's upcoming exams grouped by day |
| `/purchase/result` | Payment result (moves into the site shell) |

Breadcrumbs: হোম › এমসিকিউ or রিটেন › category path › module › exam. The exam page knows its type, so it links back to the matching door.

## Home (`/`)

Signed out: the mockup hero ("এক্সাম" gradient word, tagline) and two big door cards, ✅ এমসিকিউ "দ্রুত অনুশীলন" and ✍️ রিটেন "লিখিত পরীক্ষা". Under them, a short strip of three value points (live exams on a set time, instant MCQ score, written scripts marked by instructors) and a "অ্যাকাউন্ট খুলুন" call to action. Keep it short; this is not a marketing page.

Signed in (student), under the doors, in this order:

0. **Nudges** (only when they apply, dismissible for the session): "চালিয়ে যান" for an exam with `myStatus` `IN_PROGRESS` (link to `/exam/[id]/sit`, highest priority); "ইমেইল যাচাই করুন" when `emailVerifiedAt` is null (link to `/account`); "ফোন নম্বর যোগ করুন" when `phoneRequired`; a one-time welcome card when `localStorage` has `exam-portal.welcome=1` (set by WS2 on registration; clear it after showing). *(welcome card P1)*
1. **Next exam countdown** (U12): the soonest exam in `GET /api/v1/me/exam-routine` whose window has not ended. Before the start: "সিআরপিসি শুরু হতে ২ ঘণ্টা ১৪ মিনিট" with the date and time. While open: "এখন চলছে" with "পরীক্ষা দিন" (links to `/exam/[id]`). Uses `Countdown` and `serverNow()`.
2. **এখন চলছে**: routine rows whose window is open now.
3. **আসন্ন**: the next three upcoming rows, then "সব দেখুন" to `/my-modules`.
4. **আমার মডিউল**: up to four granted modules from `GET /api/v1/live-exam-modules`, each with a green "অ্যাক্সেস আছে" pill.

Empty signed-in state: "এখনো কোনো পরীক্ষা কেনা হয়নি" with links to the two doors. Staff signed in: the signed-out home plus a "কর্মী প্যানেল" link.

## Door and category pages

`GET /api/v1/public/live-exam-categories?examType=MCQ|WRITTEN` (WS1 B). Show root categories as mockup cards with emoji, title, subtitle (the description's first line) and gold chevron. A category with child categories lists them; a category with modules lists modules as cards ("পরীক্ষাসমূহ দেখুন", the type count like "৫টি এমসিকিউ", and the module price). A category with nothing shows the "শীঘ্রই আসছে" pill card from the mockup. Cache the tree for 60 s; navigating within a door does not refetch.

## Module page

Data: `GET /api/v1/public/live-exam-modules/{id}` and `.../exams?examType=` when signed out; when a student is signed in, also the student read `GET /api/v1/live-exam-modules/{moduleId}/exams?examType=` for `myStatus`, `retakeAvailable`, pass percent and maximum. Decide access from whether the student read returns the module.

Layout (mockup):

1. Title, the type pill (এমসিকিউ or রিটেন), the description.
2. **Strip** of chips that scroll to sections: রুটিন (shows the next date or "তারিখ ঘোষণা হবে"), খাতা (written door only; pill from the student's script states), রেজাল্ট (pill "শীঘ্রই" or "প্রকাশিত"). Signed out, the strip shows the routine chip only.
3. **Price bar**: "মডিউল ৳ ২,৫০০" and "মডিউল কিনুন", with "এমসিকিউ ও রিটেন দুই ধরনের সব পরীক্ষা খুলবে" (U3). Owned: green "অ্যাক্সেস আছে" instead. No price: "দাম এখনো নির্ধারণ হয়নি" and no button.
4. **রুটিন** card: table of পরীক্ষা, তারিখ, সময় (start–end), dated soonest first, undated last with "তারিখ ঘোষণা হবে". Scrolls sideways inside the card on small phones.
5. **পরীক্ষার তালিকা**: exam cards with title, short subject line (description's first line), type, and on the right either the price and "কিনুন" (not owned) or the status pill (owned).

Status pill mapping from `myStatus` (owned exams):

| `myStatus` | Pill | Tone |
|---|---|---|
| `NOT_ANNOUNCED` | তারিখ ঘোষণা হবে | muted |
| `UPCOMING` | আসন্ন | muted |
| `OPEN` | চলছে · দিন | orange, pulsing |
| `IN_PROGRESS` | চলমান | orange, pulsing |
| `SUBMITTED` | জমা হয়েছে | green |
| `AWAITING_RESULT` | খাতা অপেক্ষমাণ | orange |
| `RESULT_READY` | ফল প্রকাশিত | green |
| `FAILED_TO_SUBMIT` | জমা হয়নি | danger |
| `MISSED` | সময় শেষ (with "পরে দিন" when `retakeAvailable`) | muted |

## Exam page (`/exam/[examId]`)

Public data: `GET /api/v1/public/live-exams/{id}`. Owned: `GET /api/v1/live-exams/{id}` adds pass percent, maximum, `myStatus`, `retakeAvailable`.

Sections as cards: বিবরণ, নিয়মাবলি, "প্রশ্নপত্রের ভাষা: বাংলা/English", সময় (duration), শুরু and শেষ (or "তারিখ ঘোষণা হবে"), মূল্য (not owned), পাসের নম্বর and পূর্ণমান (owned). Written exams add the card "প্রশ্নপত্র ও নির্দেশনা": "প্রশ্নপত্র পরীক্ষার সময় আনলক হবে" and "উত্তর একটি পিডিএফ। ছবি গ্রহণযোগ্য নয়।" (the mockup's photo upload line is wrong).

One primary action, by state:

| State | Primary | Notes |
|---|---|---|
| Not signed in | "পরীক্ষা কিনুন · ৳ ৪০০" to login with `next` | Secondary outline "মডিউল কিনুন · ৳ ২,৫০০" |
| Signed in, not owned, priced | "পরীক্ষা কিনুন" opens the confirm sheet | Secondary module buy |
| Not owned, no price | none | "দাম এখনো নির্ধারণ হয়নি" |
| `phoneRequired` | "ফোন নম্বর যোগ করুন" to `/set-phone?next=` | Buying and sitting need a phone |
| `NOT_ANNOUNCED` | disabled "তারিখ ঘোষণা হবে" | |
| `UPCOMING` | disabled "পরীক্ষা শুরু করুন" | Reason with the start time, and the countdown |
| `OPEN` | "পরীক্ষা শুরু করুন" opens the **start confirm**, then `/exam/[id]/sit?start=live` | WS4 calls take |
| `IN_PROGRESS`, `SUBMITTED` (window open) | "পরীক্ষায় ফিরুন" to `/exam/[id]/sit` | Never "আবার শুরু" |
| `MISSED` + `retakeAvailable` | outline "পরে একবার দিন · মেধাতালিকায় উঠবে না" opens the start confirm (retake wording), then `/exam/[id]/sit?start=retake` | MCQ only |

**Start confirm** (LMS pre-start screen, a `Sheet`): exam title, duration, "শুরু করলেই সময় চলতে থাকবে, বিরতি দেওয়া যাবে না", the deadline it will end at (for a live sitting, the shared end; for the later attempt, now plus duration), the number of questions is not shown (it is unknown before start), the rules summary, and for a live MCQ: "শুরু করলে পরে আর আলাদা করে দেওয়ার সুযোগ থাকবে না" (taking the live sitting gives up the later attempt). Written: "উত্তর একটি পিডিএফ ফাইল হিসেবে জমা দিতে হবে". Primary "শুরু করুন", outline "এখন না". A good connection reminder line.
| `MISSED` without retake | none | "সময় শেষ" |
| `AWAITING_RESULT`, `RESULT_READY`, `FAILED_TO_SUBMIT`, ended `SUBMITTED` | "ফলাফল দেখুন" to `/exam/[id]/result` | Plus "মেধাতালিকা" link |

## Purchase

Confirm sheet (bottom sheet on phones): product, amount, what it unlocks ("শুধু এই পরীক্ষাটি খুলবে।" or "এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে, এমসিকিউ ও রিটেন দুটোই।"), the trust line "SSLCommerz-এর মাধ্যমে নিরাপদ পেমেন্ট · বিকাশ, নগদ, রকেট, কার্ড", a link to `/refund` (opens in the same tab after the sheet closes), primary "পেমেন্টে যান", outline "বাতিল". When the exam belongs to a priced module, show a quiet comparison line: "পুরো মডিউল ৳ ২,৫০০ — সব পরীক্ষা" with a switch link. *(comparison P1)* On confirm: `POST /api/v1/live-exam-orders` with `examId` or `moduleId`, then `window.location.assign(redirectUrl)`. Handle `ALREADY_PURCHASED` (refresh, show access), `PHONE_REQUIRED`, `PRICE_REQUIRED` and `RESOURCE_NOT_FOUND` (refresh the page), and `PAYMENT_GATEWAY_UNAVAILABLE` ("পেমেন্ট গেটওয়ে এখন পাওয়া যাচ্ছে না।"; when the error carries an `orderId`, offer cancel). A repeated start continues the same pending order.

Payment result (`/purchase/result?tranId=`): restyle the existing page into the site shell. States: pending (quiet repeating status, polls every 3 s for 60 s, then "পরে আবার দেখুন"), paid ("১৯তম বিজেএস খুলেছে" with "মডিউলে যান" or "পরীক্ষায় যান"; invalidate the catalog and routine queries), failed ("টাকা কাটা হয়নি", retry), cancelled ("বাতিল হয়েছে", retry), not signed in (sign-in form that returns here).

## My modules (`/my-modules`)

Granted modules with type counts, a progress bar "৩/৫ পরীক্ষা সম্পন্ন" (exams with `SUBMITTED`, `AWAITING_RESULT`, `RESULT_READY` or `FAILED_TO_SUBMIT` over published exams the student can sit) *(progress P1)*, and under each the next routine rows for that module. Links to the module page in the door matching each exam type. Empty: links to both doors. The module page shows the same progress bar when owned. *(P1)*

## Routine (`/routine`)

`GET /api/v1/me/exam-routine`, grouped by day in Asia/Dhaka ("আজ", "আগামীকাল", then the date). Each row: time range, exam title, module, type pill, status pill, and a link to the exam page. Live rows are on top with the orange pulsing pill. Undated exams are not in the routine (API rule); a footer line says "তারিখ ঘোষণা হলে এখানে দেখা যাবে". Each row has "ক্যালেন্ডারে যোগ করুন", which downloads an `.ics` file made in the browser (title, start, end, URL of the exam page). *(.ics P1)*

## Tests

Start confirm copy (live MCQ forfeits the later attempt, retake wording, written PDF line), routine grouping and the `.ics` content, nudges order, door filtering (mocked responses for both types), coming-soon category, module page signed out, owned and no-price variants, status pill mapping table, exam page primary action per state, confirm sheet copy for exam and module, gateway-unavailable error, payment result states, home countdown before and during the window, empty dashboard.

## Report

(Agent fills this in.)
