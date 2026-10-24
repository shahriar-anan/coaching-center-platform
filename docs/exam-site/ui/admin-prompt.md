# Admin prompt — Master Admin, System Admin, Instructor

Copy from the heading **Prompt** through `END OF PROMPT`. This is the exam-site staff tool only. Do not use the student mockup’s cream page, gold accent, giant hero, or 760px column. Do not design the student shop, a student question paper, courses, books, or bKash. Exam-module and per-exam payments are part of this tool.

Generate the Bangla version of every screen, and one English version of the Master Admin overview so the language switch is visible.

Sample data: module “১৯তম বিজেএস”, exams সিপিসি (written, date not set), সিআরপিসি (MCQ, ১২ নভেম্বর ১২:০০, ৪০ minutes, pass ৪০%, maximum ৫০). Instructor “ফারিহা রহমান”. Student “নাঈম হাসান”, code `S18492017`.

---

# Prompt

## Product

Design a desktop-first operations site for running live exams. Three roles share one login and one shell. The navigation changes with the role. A control the server would reject is absent, not disabled in a way that looks broken, except where the reason itself is the point of the screen.

Roles:

- **Master Admin** runs the catalog, sets prices, deletes and archives, marks the roster, assigns the one instructor, reads exam payments, marks written papers, and publishes written results. Also manages students, system admins, and instructors, and reads the audit log.
- **System Admin** authors the catalog, manages students, and reads exam payments. No price input, no delete, no archive, no roster, no instructor assignment, no marking, no result publish, no audit log, no instructor directory, no system-admin directory. A payment row is read-only.
- **Instructor** sees only assigned modules, their participation counts, and their written submissions. Saves a draft mark. Cannot publish. No catalog and no people directory.

A student who signs in here sees “এই অংশ কর্মীদের জন্য” and a way back. Do not build the student site on these screens.

## Visual system

Calm and dense. Light background `#f4f6f8`, surface `#ffffff`, text `#1c2430`, muted `#5c6b7a`, border `#d8e0e8`, action `#1f4e79`, action text `#ffffff`. Danger is a text button in `#9b2c2c` until a confirm dialog. Success `#1f7a4d`. Warning `#9a6700`. Dark theme: background `#12181f`, surface `#1c2631`, text `#e7edf2`, muted `#9aabba`, border `#2c3b4a`, action `#7eb0d8` with dark text.

Type: Noto Sans Bengali for Bangla, a neutral sans for English and digits. Body 14px in tables, 16px in forms. Page title 22px. Line height 1.45.

Shell: left sidebar 232px, logo “এক্সাম পোর্টাল” and a role chip (মাস্টার অ্যাডমিন, সিস্টেম অ্যাডমিন, or ইনস্ট্রাক্টর). Content column max 1120px. Top bar: the page title, language pill, theme pill, the person’s name, logout. Breadcrumbs under the title on every page except that role’s home.

At 1280px this is the main picture. At 768px the sidebar becomes a menu button and tables stay tables with horizontal scroll. At 390px each table row becomes a stacked card with the same fields and the same primary action. Do not invent a separate mobile product.

## How an admin site stays fast and clear

- Server pages of 20 rows. Remember the page, search, and filters in the address bar so Back and refresh return to the same list.
- Debounce search by 300ms. Do not search on every keypress. Show the query in the field while the previous rows stay visible, then replace them. A failed search keeps the last good rows and shows “আবার চেষ্টা”.
- Skeletons match the table they replace. The first load is a skeleton, not a spinner on a blank page.
- Do not autosave. Saving a published module or exam returns it to draft. The save button’s helper says “প্রকাশিত থাকলে সেভ করলে আবার ড্রাফট হবে।” A dirty form warns before the user leaves.
- One primary button per screen. Destructive actions are quiet until a dialog names the object.
- Keyboard: `/` focuses the list search, Escape closes a dialog, Enter submits the focused form. Focus returns to the control that opened a dialog.
- Targets are at least 40px. Status is a word, not a color alone. Errors sit on the field that failed. A focus ring is visible. Respect reduced motion.
- Times are Asia/Dhaka. Money is ৳ with two decimals. An empty price is “দাম নেই”, not zero.
- Both languages are fields, side by side on a wide screen and stacked on a narrow one. The language pill switches chrome and which title a list shows. It does not delete the other language.
- Do not draw charts, exports, bulk delete, or a dashboard of vanity numbers. The only counts are the participation counts the server returns.

## Rules the server enforces

Accounts:

- Login is email or phone, plus password. A wrong password, an unknown person, a deactivated account, and an account with no password yet all show the same failure. After too many tries, say to wait. Do not say which field was wrong.
- Creating a student, instructor, or system admin takes name, phone, and email. Phone is `01` plus nine digits. There is never a password field. The created account is pending. The response includes a one-time activation code. Show it once in a dialog with Copy, and say it will not be shown again. Reissue replaces it and shows the new code the same way.
- Activation, on a separate signed-out page, is phone, code, and a new password of 8 to 72 characters. An expired, used, or over-tried code all say the code is no longer valid.
- Forgot password asks for email and always says a message was sent if an account exists. Reset is the code plus a new password.
- Status is only active or inactive. Delete is Master Admin only, and it asks for the person’s name in the dialog. Nothing can edit, deactivate, or delete the Master Admin. That account is not in the system-admin list.
- Staff can change their own password if they know the current one. They cannot edit their own email or name. A phone can be added only when the account has none.
- Student search is one box. The server does not filter students by status. Show status as a column. Instructor search does filter by status (`ACTIVE`, `PENDING_ACTIVATION`, `INACTIVE`) and by whether a module is assigned.
- The system-admin list has no search. Paginate it.

Catalog:

- A category tree can be any depth. Titles in both languages. Two siblings cannot share a position. A category that still has a subcategory or a module cannot be deleted. Say so. Archive hides that whole branch from students. Master Admin only for delete and archive.
- A module has one category, both titles, both descriptions, an optional price, and status draft, published, or archived. Access stays manual. Do not draw a course link. An archived module is read-only. Any edit returns it to draft and clears final save. Publish stays disabled until final save, and until the category branch is not archived.
- Price is greater than zero when set, at most two decimal places. Only the Master Admin has the input. System Admin sees the amount as text.
- An exam is MCQ or written. The type is chosen at create and does not change. Both titles, descriptions, and rules. Paper language is Bangla or English and only selects the question typeface. Duration is a positive number of minutes. Pass percent is 0 through 100 and may be empty in a draft. Publish needs it.
- Start may be empty. The list then says “তারিখ ঘোষণা হবে”. End is start plus duration. Do not ask for an end time. Inside one module, a dated exam must start at least five minutes after every other dated published exam ends. Show the other exam’s name and time and keep publish disabled. Another module may use the same time. An undated exam is left out of that check. A dated exam whose window has already ended cannot be published.
- After the start instant, the paper, the PDF, the language, and the schedule are locked. The editor is read-only and says so. Edits before the start are allowed and return the exam to draft.
- MCQ has no maximum field. The total is the sum of question marks. Publish needs at least one question. Each question has a stem, marks of at least 1, an optional explanation, and at least two options with exactly one correct. A second “correct” clears the first. Master Admin and System Admin can delete a question. That delete also returns the exam to draft.
- Written has a maximum of at least 1 and one question PDF. The file must be a real PDF. There is no question builder. There is no negative mark anywhere.
- Deleting an exam is Master Admin only.

Roster and instructor:

- Roster is Master Admin only. Search by name, exact email, or phone. Paste is one email or phone per line. The result lists who was added and which lines matched nobody. Remove is per student. Marking someone already marked is a no-op, not an error.
- The screen’s main table is everyone already marked: name, phone, email, and remove. Search and paste add to that table. An empty table means nobody is marked. The current API returns search hits and the paste result, not this full membership list. Draw the full list anyway. An operator cannot grant access without seeing who already has it.
- Removing someone after they have started does not erase the sitting. Their result stays. The row leaves the roster.
- One instructor per module. Assign only while the module has none, and only a person whose role is instructor. To change, the Master Admin removes the current instructor, then assigns another. Both actions confirm. System Admin does not see this tab.
- The instructor directory is the picker. Each row shows the modules already held. Do not ask anyone to type an id.

Participation:

- A paged list, one row per exam. Filters the server actually accepts: module, type MCQ or written, from, to, text query, and sort by date, title, or sitting count. Columns: exam, module, type, start, end, sitting, submitted, failed to submit, awaiting a mark, drafts waiting for publish, published, later attempts.
- Written rows have the marking columns. MCQ rows show “—” there, because an MCQ is not marked or published by staff. Later attempts are the single MCQ retake and stay off the live leaderboard. Do not add a chart.
- The home summary is two numbers only: papers awaiting a mark, and drafts awaiting publish.
- Master Admin and System Admin see every exam. An instructor sees assigned modules only. Opening a written count goes to that module’s submission list with that exam named. An MCQ count does not open a paper. Staff cannot read MCQ answers.

Marking:

- The submission list is published written exams only, sorted by student name. Columns: student, exam, status, score, maximum, kind (live or later), answer file yes or no, evaluated file yes or no, examiner name, published yes or no.
- Instructor: assigned modules only, can download the answer PDF when a file exists, can save a draft. No publish button.
- System Admin: can open the list and see the examiner’s name. Cannot download the answer PDF, cannot save a mark, cannot publish.
- Master Admin: can download, save a draft, and publish.
- A draft needs a submitted paper that has an answer PDF, marks from 0 through the maximum, and a marked PDF on every save. The comment is optional. A missing answer PDF has no form. The row says “জমা হয়নি · নম্বর দেওয়া যাবে না.” A failed-to-submit zero is already the score and is not an instructor mark.
- Saving, including a Master Admin save, does not publish. A later save clears publish. The note next to save says the student cannot see it yet, and that saving again hides a published result until the next publish.
- Publish is a separate Master Admin button, disabled until a draft mark exists. MCQ has no publish control.

Payments:

- A student buys one exam or one whole module through SSLCommerz, in taka. There is no bKash, Nagad, cash, or card form on this staff site.
- An order is pending, paid, failed, or cancelled. Pending is the open checkout. Paid is the only status that grants access. Failed and cancelled grant nothing.
- A paid module covers every exam in that module now and every exam published there later. A paid exam covers that exam only. Say which one it is on the row.
- A roster mark is the other grant. It does not create an order. The payments page does not edit the roster. The Master Admin uses the roster when someone should sit without paying.
- Nobody on staff marks an order paid. There is no such button. A pending row says the gateway confirms it, and a paid row appears when that happens. Do not invent a verify or override action.
- Buying again after a grant is rejected. The list shows the paid row. It does not show a second charge.
- A module with no price cannot be bought. That is fixed on the module editor, not on the payments page.
- System Admin can read the list and open a row. Master Admin can read it and can change the price on the catalog. Instructor has no payments page.
- Draw the payments list and the payment detail even though a staff order list is not a current endpoint. The orders exist. An operator has to look up a student, a module, and a transaction.

Access, which an operator has to answer:

- On a student, show every module and exam they can sit, and why: roster, module purchase, or exam purchase. A module purchase and a roster mark cover the whole module. An exam purchase covers one exam.
- This combined access list is required. The server does not return it as one call. Draw it on the student page.

Audit:

- Master Admin only. A paged list, newest available order, columns time, action, entity type, entity id, actor id. The server has no search. Do not draw filters that do nothing.

Out of this tool: a student notice composer, a button that marks an exam order paid, courses, books, bKash, and the old exam-module builder.

## Pages

Generate each of these. Where a role is named, also generate the other roles’ shells only when their navigation differs. Do not repeat a catalog editor for the instructor.

**1. Staff login.** Email or phone, password, “প্রবেশ করুন”. Links to forgot password and activation. No Google button. Generic failure. Locked state. A student who gets through sees the staff-only message.

**2. Activate.** Phone, code, new password. Invalid-code state. Success sends them to login. Not signed in yet.

**3. Forgot and reset.** Email only, then the generic sent sentence. Reset is code and new password, then login.

**4. Account.** Name, email, phone, and role, all read-only. Change password asks for the current password. Logout confirms. If the phone is empty, one field can set it, with “একবারই”.

**5. Master Admin home.** Three summary cards: “নম্বরের অপেক্ষায়”, “প্রকাশের অপেক্ষায়”, and “পেমেন্ট অপেক্ষমাণ”. The first two open participation. The third opens payments filtered to pending. Under them, the next exams from the participation list, soonest dated first, each showing the four operational counts. Clicking a written count opens that submission list. Undated exams show “তারিখ ঘোষণা হবে”.

**6. System Admin home.** The same three cards and the same exam list. The payment card opens the read-only payments list. No price, delete, or publish actions on the rows.

**7. Instructor home.** Only assigned modules, as cards. Each card shows the module title and the summary counts for that module. No catalog links. Empty: “এখনো কোনো মডিউল দেওয়া হয়নি।”

**8. Category tree.** Search within the loaded tree. Nested rows: বিজেএস, under it ১৯তম and ২০তম. Status chips ড্রাফট, প্রকাশিত, আর্কাইভ. “নতুন ক্যাটাগরি”. Master Admin has archive and delete. Delete on a category that still has children explains why it failed. System Admin sees the tree without those two actions.

**9. Category editor.** English title and Bangla title side by side. Parent selector, including none for a root. Position. Save. Dirty line “সেভ হয়নি”.

**10. Module list.** Search, filter by category and status. Columns: name, category, price, status, exam count. Master Admin can open the editor. System Admin opens the same editor without price, roster, instructor, delete, or archive.

**11. Module editor, details.** Both titles, both descriptions, category, price for Master Admin only, final save, publish. Helper about returning to draft. Archive and delete only for Master Admin, each with a named confirm. Archived state is read-only.

**12. Module editor, exams.** The four law exams. Columns: name, type, language, date, status. “নতুন পরীক্ষা” chooses MCQ or written and does not allow switching later. Each row links to participation.

**13. Roster tab, Master Admin only.** The table is the current roster: name, phone, email, remove. Search sits above it. A hit that is not already listed has “যোগ করুন”. Paste box, one email or phone per line. After paste, show “১২ জন যোগ হয়েছে” and list the unknown lines. Those people appear in the table. Empty table: “এই মডিউলে কেউ নেই।” Remove confirms with the student’s name. System Admin does not see this tab.

**14. Instructor tab, Master Admin only.** Empty state “কেউ নেই” and “ইনস্ট্রাক্টর দিন”, opening the directory as a picker with search. Filled state shows the person’s name and phone and “সরান”. Remove confirms, then the empty state returns. A second assign without a remove shows “এই মডিউলে ইতিমধ্যে একজন আছেন।”

**15. MCQ exam editor.** Both titles, descriptions, and rules. Paper language. Duration. Pass percent. Start, with a switch “তারিখ এখনো নয়”. Question list: stem, four options, one correct, marks, explanation, reorder, delete. Running total “মোট ৫০”. Final save and publish. A five-minute clash names the other exam. After the start time, the form is locked. System Admin sees this page without delete-exam.

**16. Written exam editor.** The same meta fields. Maximum marks. One PDF field showing the file name, a way to open that stored PDF, and replace by uploading another. No question list. The same lock, draft, and publish rules. Opening the stored question PDF is required so the author can confirm the file. The student still cannot download it.

**17. Participation.** The filter row and columns from the rules. Sample row সিপিসি: sitting ৮৪, submitted ৭১, failed ১৩, awaiting ৬২, drafts ৯, published ০, later attempts ০. সিআরপিসি shows “—” for the three written-only columns. Sortable date, title, and sitting. Instructor variant is the same table with only assigned modules.

**18. Students.** Search. Columns: name, phone, email, code, status. “নতুন শিক্ষার্থী” is name, phone, email. On create, the one-time code dialog. Row actions: active or inactive, “কোড আবার দিন”, and, for Master Admin, delete. The student page shows the name, which Master Admin and System Admin can edit, and an access section: each module or exam, with the reason “রোস্টার”, “মডিউল কেনা”, or “পরীক্ষা কেনা”. A module row links to that module. A paid row links to the payment. Master Admin sees “রোস্টারে যোগ” from a module line. System Admin sees the same access list and cannot change it.

**19. Instructors, Master Admin.** Search. Filters: active, pending, inactive, assigned, unassigned. Columns: name, phone, email, status, modules. A row with none says “কোনো মডিউল নেই”. Create is name, phone, email, then the code dialog. Pending rows can reissue the code. This same list, without create, is the assign picker. System Admin has no page.

**20. System admins, Master Admin.** A short paged list. Columns: name, phone, email, status. Create without a password. Status, reissue code, delete. The Master Admin is not a row. No role dropdown.

**21. Submission list.** Module title in the breadcrumb. Search by the names already loaded, because the server sends the module’s written rows together. Filters that work on that list: all, has an answer, failed to submit, draft mark, published. The participation counts for the module sit above the table. System Admin rows open read-only. Instructor rows open the draft form.

**22. Submission, instructor.** Student, exam, status. “উত্তরপত্র ডাউনলোড” only when a file exists. Marks, optional comment, required marked PDF. Primary “ড্রাফট সেভ”. Persistent note that the student cannot see it, and that a later save clears publish. No publish button. The no-file state has no form.

**23. Submission, Master Admin.** The same form, plus “ফলাফল প্রকাশ করুন”, disabled until a draft exists. After publish, a “প্রকাশিত” chip. Helper: “আবার সেভ করলে প্রকাশ বাতিল হবে।”

**24. Submission, System Admin.** The same facts, including the examiner’s name. No download, no form, no publish.

**25. Payments.** Master Admin and System Admin. A paged list. Search by student name, phone, email, or transaction id. Filters: pending, paid, failed, cancelled, module, exam, and a date range. Columns: time, student, phone, product, scope, amount, status, transaction id. Sample rows: নাঈম হাসান, ১৯তম বিজেএস, মডিউল, ৳ ২,৫০০, পরিশোধিত; another student, সিআরপিসি, পরীক্ষা, ৳ ৪০০, অপেক্ষমাণ; a third, সিপিসি, পরীক্ষা, ৳ ৪০০, ব্যর্থ. Pending copy on the row: “নিশ্চিত হলে অ্যাক্সেস খুলবে.” Paid module copy: “পরের পরীক্ষাও খুলবে.” Paid exam copy: “শুধু এই পরীক্ষা.” Failed and cancelled: “অ্যাক্সেস হয়নি.” No row action marks it paid. Instructor has no item in the nav.

**26. Payment detail.** The same facts, plus updated time and a link to the module or exam. If it is paid, the access line names the grant. If it is not paid, the line says there is no grant. Master Admin gets a text link “পেমেন্ট ছাড়া দিতে রোস্টার খুলুন” that opens that module’s roster. System Admin does not get that link. No refund button and no second charge.

**27. Staff leaderboard.** Opened from an exam after the window. Read only. Columns: name, rank, score, maximum, pass. Empty state names the time it opens, five minutes after the end, or “তারিখ ঘোষণা হবে” when there is no date. Live sittings only. The later MCQ attempt is absent. A written row appears after publish, except a failed-to-submit zero, which is already listed. Master Admin and the assigned instructor can open it. System Admin gets the same read-only page. It is how staff answer a rank question. No editing.

**28. Audit log.** Time, action, entity, entity id, actor id. Pager. No search box.

**29. Dialogs.** Confirm archive. Confirm delete, with the name. Confirm unassign. Confirm publish of a result. Confirm remove from the roster. Unsaved changes. Activation code, shown once. Roster paste result.

**30. English overview.** The Master Admin home with English chrome: “Awaiting a mark”, “Awaiting publish”, “Payment pending”. Exam titles stay as stored. The language pill reads “বাংলা”.

## Do not draw

The student storefront, a buy button, a card form, a bKash or Nagad method, a button that marks an exam order paid, a notice composer, a chart, a password on a create-person form, a second instructor on one module, a publish button for an instructor or a System Admin, or a price field for a System Admin.

END OF PROMPT
