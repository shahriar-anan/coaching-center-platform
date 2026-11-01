# Admin: Reviews page, Direct enrolment picker, Announcements by package, exam and student

Status: approved by Shahriar on 1 November 2026, including the defaults in section 11. The plan was re-checked for drawbacks the same day; section 12 lists what that found and what changed. Implementation is in progress.

## 1. What is there today

| Area | Today |
|---|---|
| Reviews, admin | The API exists (queue, counts of pending, approve or hide, feature; added this week). **There is no staff page and no sidebar entry.** |
| Direct enrolment (`/staff/enrol`, Master Admin) | "A package / An exam" switch, then a plain `<select>` of published exams or packages. The exam list loads every page of published exams into one dropdown, so it grows with every exam; with the 100 sample exams it is already a long scroll. Students are found with a search box, then added to a list; one reason; one button. |
| Announcements (`/staff/announcements`, Master Admin only) | Audiences **All students** or **One package**. One `ANNOUNCEMENT` notice is written per student. The student sees it only on the Notices page (Announcements tab). A package announcement reaches students who hold the package by a roster row or an active grant on the package. Nothing is shown on package or exam pages. A sent announcement is never edited or deleted. |
| Free exams | A free exam is open to every signed-in student. There is **no claim record**, so "claimed it when it was free" has no data behind it today (decision 1 below). |
| Lists for pickers | The staff exam list and package list already take `q` (search), `categoryId`, `status` and `type` on the server. No new API is needed to build the picker. |

## 2. Decisions already made (1 November)

1. **Recipients of a free package or exam:** students who bought or were enrolled, plus students who **started at least one attempt** on the free exam (or on any exam of the free package). No "Get it free" button.
2. **Package and exam pages:** every student who **currently holds** the package or exam sees all of its announcements on that page, newest first, including one who joined after an announcement was sent. A **free** exam or package is open to every signed-in student, so its page shows its announcements to every signed-in student (otherwise "the exam is postponed" would be hidden from a student who has not started it yet). The notification bell goes only to the students who were recipients when it was sent.
3. **Who sends:** the Master Admin **and System Admins**. Teachers do not.
4. **Picker layout:** search-and-pick list (section 4), shared by Direct enrolment, the Reviews filter and Announcements.

## 3. Shared parts

**TargetPicker** (new, `components/staff/target-picker.tsx`). One component, three uses.
- Props: `kind` ("package", "exam" or "either" with a switch), `statuses` (enrolment: published only; Reviews filter: any status, archived ones included, because their reviews still exist; Announcements: published and archived), `value`, `onChange`.
- Server search: a debounced search box sends `q`; a category select sends `categoryId`; a Type select (All, MCQ, Written) sends `examType`/`type`. Results are paged 10 at a time with "Load more", never the whole catalog.
- A row shows the title (Bangla or English by language), the category, a type pill, and for packages the number of exams. Selecting a row fills the chosen line above the list; "Change" reopens it.
- Accessible: the search box is a labelled text field and the results are a radio group, so arrow keys move through them and a screen reader reads each row.

**StudentPicker** (extract from `direct-enrolment.tsx`): the existing student search and the "students on the list" list move into `components/staff/student-picker.tsx` so Announcements can reuse them. Behaviour unchanged.

## 4. Direct enrolment

Layout (wide): the page header, then two columns as today, but step 1 is rebuilt.

```
1. Choose what to enrol into
[ A package | An exam ]
[ Search by title...              ]
Category [All v]   Type [All v]
------------------------------------------------
( ) Penal Code Practice Set          MCQ     5 exams
(•) Bar Council Written Mock         WRITTEN
( ) Civil Law Mock Test              WRITTEN
                               Load more
------------------------------------------------
Chosen: Bar Council Written Mock   [Change]
```

- The list is about six rows tall, then scrolls inside the card; the page does not become long.
- Opening the page from a package or exam menu (`?package=` / `?exam=`) shows the chosen line already filled, as now.
- Steps 2 (find students) and 3 (list, reason, enrol) are unchanged. On a phone the three steps stack.
- No API change. A test seeds 120 exams and checks that only the first 10 are fetched and that search narrows the list.

## 5. Reviews page for staff

**Where.** `/staff/reviews`, in the sidebar under "Exams and marking", after Announcements, with a pending count badge. Master Admin and System Admins manage; teachers see only the reviews of their own exams and cannot act.

**Page.**
1. Header with a summary strip: Waiting, Published, Hidden, Featured (counts).
2. Tabs: **Waiting** (default), Published, Hidden, All.
3. Filters: Type (All, Exam, Package), **Target** (TargetPicker in a popover: pick one exam or package), Stars (any, 1 to 5), Featured only, search by student (name, code or phone), sort (newest, oldest, lowest stars, highest stars). Filters live in the URL so a link can be shared and the browser Back button works.
4. Rows: stars, the comment (long ones expand), the student (links to the student's staff page), the target (links to it), date, status pill and the hide reason if hidden.
5. Row actions by role and state: **Approve**, **Hide** (asks for a reason, 300 characters, as the API requires), **Feature** and **Unfeature** (only for approved reviews with a comment). Each action updates the row, the counts and the sidebar badge, and shows a toast.
6. Deep links: the exam and package row menus get "Reviews", opening this page already filtered to that target.
7. Empty states per tab ("Nothing is waiting"), loading skeletons, error state with retry, and a "Load more" button (pages of 20).

**API additions.** `GET /api/v1/staff/reviews` gets `q` (student name, code or phone), `featured` (true/false) and `sort` (`newest`, `oldest`, `lowest`, `highest`). New `GET /api/v1/staff/reviews/counts` returns `{pending, approved, hidden, featured}` for the same filters except `status`, so the tab badges are right under any filter. Teachers keep the owned-exams scope on both.

## 6. Announcements: the model

| Audience | Who gets the notice | Where it shows |
|---|---|---|
| `ALL` | Every active student | Notices page |
| `MODULE` (a package) | Active holders: a roster row, an active grant on the package, an active grant on one of its exams, or a started attempt on any of its exams (covers a free package) | Notices page, and the package page for every current holder |
| `EXAM` (new) | Active holders: an active grant on the exam, an active grant or roster row on a package that contains it, or a started attempt on it (covers a free exam) | Notices page, and the exam page for every current holder |
| `SELECTED` (new) | The students the admin picked (1 to 500) | Notices page only |

Rules:
- "Holder" is one definition in one place (`AnnouncementAudience` helper), used for both the recipients at send time and the page visibility check. A revoked grant or expired access is not a holder. The page check adds one case: a free exam or package shows its announcements to every signed-in student.
- The package rule changes from today: the old code described "a grant on one of its exams" but only checked roster rows and grants on the package. The new rule checks all three, plus a started attempt.
- A **withdrawn** announcement drops out of the Notices list, the unread count and mark-as-read (a withdrawn notice answers 404), and out of the page cards.
- Sending to a package or exam with no recipients is allowed: nobody is notified now, and a student who joins later sees it on the page. The confirm dialog says so.
- Only active, non-deleted students are ever notified. `SELECTED` rejects any id that is not an active student (400 with the list of bad ids); duplicates are ignored.
- A sent announcement is not edited. **Withdraw** (section 11, default yes) hides it from the pages and the Notices page without deleting the record.

## 7. Announcements: API

Migration V57:
- `announcements`: add `exam_id uuid null references live_exams`, add `withdrawn_at timestamptz null`, `withdrawn_by uuid null`; replace the audience check with `ALL, MODULE, EXAM, SELECTED`; check that `module_id` is set only for `MODULE` and `exam_id` only for `EXAM`.
- Notices keep one row per recipient, as now. An `EXAM` notice carries `exam_id` so the existing inbox rule hides it if the exam is deleted.

Endpoints:
- `POST /api/v1/announcements` takes `audience` ALL, MODULE, EXAM or SELECTED, plus `moduleId`, `examId` or `studentIds` as the audience needs. The response is as today (`recipients`, id).
- `GET /api/v1/announcements/preview?audience=&moduleId=&examId=` returns `{recipients}` so the confirm dialog says "This will notify about 38 students" (the exact number is in the toast after sending, since students can join in between).
- `GET /api/v1/announcements` (history) adds `examId`, exam titles, and `withdrawn` to each row; filter by `audience`.
- `PUT /api/v1/announcements/{id}/withdraw` (default yes).
- `GET /api/v1/me/announcements?kind=package|exam&id=` returns the target's non-withdrawn announcements (titles and bodies, both languages, newest first) **only when the caller holds the target**; a non-holder gets an empty list, never an error that would reveal that the announcement exists.
- The notice payload (`NoticeAnnouncement`) adds `examId`, so the Notices page can link to the exam as it links to the package today.
- Permission `LIVE_EXAM_ANNOUNCE` is granted to System Admins as well. Every send and withdraw is recorded in the audit log; `AuditLogIT` lists the new operations.

## 8. Announcements: staff screens

`/staff/announcements` is open to the Master Admin and System Admins, and its sidebar entry too.
1. **Audience** is four option cards: All students, A package, An exam, Selected students.
2. A package or exam audience shows the **TargetPicker**; the selected students audience shows the **StudentPicker** (search, add, remove, count).
3. The title and body fields (Bangla and English) are unchanged.
4. **Send** opens the confirm dialog with the audience, the target, and the live recipient count from the preview endpoint ("This will notify 38 students. Students who join later will see it on the page." for a package or exam).
5. **History** shows the audience with its target, recipients, date, a status (Sent, Withdrawn), an audience filter, and Withdraw (with confirmation).

## 9. Announcements: student screens

- **Package page and exam page (holders, and everyone signed in for a free one):** an "Announcements" card under the header, above the tabs. It shows the latest three, newest first, each with date, title and body (long bodies expand with the existing preview helper), and "Show all" for more. It is absent when there are none. The page asks for it only when the student holds the target or the target is free, so a visitor or a non-holder makes no extra request. Bodies are shown as plain text, never as HTML.
- **Notices page (Announcements tab):** unchanged for All and Selected. A package or exam announcement shows the target name and a link, "Open the package" or "Open the exam".
- Bangla and English copy for everything; Bangla uses "পাতা", never "পেজ".

## 10. Delivery

Two agents at a time (Haiku 5.5, high effort). The pairs are chosen so that no two agents edit the same files at once.

| Step | Agents | Work | Tests |
|---|---|---|---|
| 1 | **A** API, announcements ‖ **U1** web, foundation | **A:** V57, audiences, holder helper, preview, withdraw, `me/announcements`, inbox queries skip withdrawn, permission for System Admins, audit entries. **U1:** TargetPicker, StudentPicker extraction, rebuilt Direct enrolment | **A:** `AnnouncementAudiencesIT` (each audience's recipients, free exam started vs not, revoked grant, SELECTED validation, preview equals send, withdraw hides from page, notices and unread count, non-holder gets an empty list, free page visible to any signed-in student, System Admin may send, student may not), `StudentNoticeGroupIT` still passes. **U1:** Vitest: search, category filter, load more, preset from the URL, enrol flow unchanged |
| 2 | **B** Reviews, API and page ‖ **U3** web, announcements | **B:** the reviews API additions (`q`, `featured`, `sort`, `counts`), then the staff Reviews page, sidebar entry and badge, deep links from the exam and package menus. **U3:** staff announcements form and history, student package and exam cards, Notices links | `ExamReviewIT` additions; Vitest per screen; footer, nav and copy-parity tests |
| 3 | me | QA checklist sections 18 to 20, `seed-announcements.sql` with one announcement of each audience, full web suite, API tests for the changed classes, restart and check live | Manual |

Limits I will keep: 500 selected students per send; 10 rows per picker page; 20 rows per page in the reviews queue.

## 11. Defaults I will use unless you say otherwise

1. **Withdraw** an announcement (hide from pages and Notices, keep the record). Without it, a mistaken package announcement would stay on the package page forever.
2. **Expired or revoked access is not a holder:** such a student gets no notice and sees no announcements on that page.
3. **Selected students limit 500** per send. A bigger group should use "All students" or a package.
4. **No bulk approve** on the Reviews page for now: reviews are read one by one so a bad comment is not approved by accident. It is easy to add later.
5. **Announcements stay in-app only** (no email or SMS), as today.
6. **Direct enrolment stays Master Admin only.** Only announcements open to System Admins.

## 12. Review of this plan: drawbacks found and what changed

| # | Drawback | Change |
|---|---|---|
| 1 | A free exam is open to everyone, but only a student who had started it would see its announcements on its page. A student who plans to take it would miss "postponed". | Decision 2 now shows a free exam's or package's announcements to every signed-in student (sections 2, 6, 9). Notifications still go only to who started it. |
| 2 | A withdrawn announcement would still count as unread and still open from the bell. | The inbox list, unread count and mark-as-read skip withdrawn announcements (section 6). |
| 3 | The Reviews filter picker would not list archived exams, whose reviews still exist. | The picker takes a status set per use: Reviews filter lists every status (section 3). |
| 4 | The page card would call the API on every exam and package page, including for visitors. | The page asks only when the student holds the target or it is free (section 9). |
| 5 | The recipient count in the confirm dialog can differ from the real count if a student joins in between. | The dialog says "about" and the toast shows the exact number (section 7). |
| 6 | An EXAM notice with an exam id could be treated by the Notices screen as an exam notice. | The student screen decides by `kind` first; `StudentNoticeGroupIT` and a new test cover an EXAM announcement (section 10). |
| 7 | Today's package rule did not match its own comment (grants on the package's exams were missed). | One shared holder rule for sending and for the page (section 6). |
| 8 | Two agents would edit the audit list and the staff nav at the same time. | The pairs in section 10 keep API work and web work separate; only one agent edits `AuditLogIT` per step. |
| 9 | Choosing 500 students one search at a time is slow. | Accepted: the typical group is a few dozen. A "paste phone numbers" box can be added later if you need it. |
| 10 | Announcement text could carry markup. | Shown as plain text only, as the Notices page does today (section 9). |
