# Google Stitch prompts — Exam Portal

Paste **Prompt 1** first and generate the student screens. Then paste **Prompt 2** in the same project so the staff screens share the system. Paste the **API rules** section with both. If a paste is cut off, use the screen blocks at the bottom one at a time. Each block repeats the rules it needs.

Show the **Bangla** version of every screen. Add one English version of the home screen and one English version of the module screen so the language switch is visible. Use the real copy below, not lorem ipsum.

## Decisions made without further review

- Student screens follow the client mockup: narrow column, cream paper, navy and gold, two doors on the home screen (MCQ and Written), breadcrumb drill-down, module chips, status pills.
- Staff screens use the same color, type, and radius, in a desktop app shell. A phone still works: the sidebar collapses, tables become stacked cards, and search stays on screen.
- Gold buttons use **navy text**, not white. White on `#c8962e` fails contrast. Navy body text stays as in the mockup.
- The mockup’s “negative mark 0.25” and “upload a photo or PDF” are not in the product. Do not draw them.
- Status pills from the mockup stay. Map them to real states: Live, Upcoming, Date not announced, Attended, Script pending, Result published, Not purchased.
- Notices, the instructor directory, and per-exam participation counts are part of this mockup. Draw them as finished screens. The API for those three will be added later.

## Product facts the pictures must obey

Exam Portal is a Bangla-first website for BJS and Bar exam students. A visitor can browse the catalog. Questions and PDFs stay hidden until the student has access and the exam is live. Access is a purchase or a roster mark.

- Home has two doors: **এমসিকিউ** and **রিটেন**. Then category, subcategory, module, exam. Example path: রিটেন → বিজেএস এক্সাম → ১৯তম বিজেএস → সিপিসি.
- Categories in the sample: বিজেএস এক্সাম (Judicial Service) and বার কাউন্সিল এক্সাম (coming soon). Batches: ১৯তম বিজেএস, ২০তম বিজেএস. Exams: সিপিসি, সিআরপিসি, পেনাল কোড, ইভিডেন্স.
- An exam is MCQ or Written. Paper language is Bangla or English and only changes the question typeface.
- A module can be bought whole. One exam can be bought alone. Checkout is SSLCommerz. Failed payment does not unlock the exam.
- Take exam turns on only while the server window is open. No date means the button stays off and the routine cell shows "তারিখ ঘোষণা হবে".
- MCQ: options on a formal paper, changeable until the deadline, score as soon as the paper is posted, correct answers only after the sitting ends. One later attempt only if the student never tapped Take exam. That attempt is off the leaderboard. No negative marks.
- Written: one PDF inside the page, no download button, watermark with the student name and id. The student uploads a PDF only. No camera and no photo. Replace is allowed until the deadline. No retake.
- Written marks, comment, and the marked PDF appear only after the Master Admin publishes. The student never sees the examiner’s name.
- Leaderboard is per exam, student names, opens five minutes after the window. Empty state before that.
- Master Admin sets prices, deletes, archives, edits the roster, assigns the one instructor, and publishes written results. System Admin authors the catalog and reads submissions, and does not get those actions. Instructor sees assigned modules only, saves a draft mark, and has no publish control.
- Notices are in-app only. A student with access gets one reminder one to two days before an exam, a notice when their MCQ score posts, and a notice when their written result is published. The notice names that exam and that student’s own marks. It never shows another student, a phone number, or the examiner. There is no compose box and no email, SMS, or push.
- The instructor directory is a searchable list: name, phone, email, status, and the one assigned module. Assigning from a module opens this list. It does not ask for an id.
- Participation is four counts per exam: sitting, submitted, failed to submit, and written drafts waiting for publish. Master Admin and System Admin see every exam. An instructor sees only assigned modules. The counts are a read, not a chart product.
- Do not draw courses, books, bKash, Nagad, SMS OTP, or a password field on any “create user” form.

## Visual system

Light: page `#f6f4ee`, card `#ffffff`, text `#14213d`, muted `#5d6578`, gold `#c8962e`, border `#e3dfd3`, pending `#e8590c`, success `#2f9e44`.

Dark: page `#0e1424`, card `#172038`, text `#eef0f6`, muted `#a3abc0`, gold `#e0b254`, border `#26304d`. Navy becomes the ink on gold buttons in both themes.

Type: Hind Siliguri, then Noto Sans Bengali. Student column max 760px, centered, 16px page padding. Cards radius 16px, buttons 12px, pills fully rounded. Gold top border 4px on the three module chips. Header: logo “⚖️ এক্সাম**পোর্টাল**” with পোর্টাল in gold, then an EN/বাংলা pill and a theme pill. Breadcrumbs in muted text; links in gold. Hover lifts a card 2px and turns the border gold. Respect safe areas, 44px targets, visible focus, and `prefers-reduced-motion` (no pulsing pill). Body text at least 16px. Do not use a generic purple gradient, Inter-only UI, or a second accent color.

---

# API rules — paste this into Stitch

These are the rules the server enforces today. Draw only controls the server will accept. If a rule forbids an action, omit the control or show it disabled with the reason on the screen. Do not add a second path that avoids the rule. Times are Asia/Dhaka. Money is taka, two decimal places.

Notices, the instructor directory, and the participation counts are screens to draw anyway. The server does not enforce those three yet. Do not invent extra restrictions for them beyond what this section says about access and roles.

## Who sees which shell

- A visitor with no account can open the published catalog only. No questions, no PDF, no Take exam, no dashboard.
- A student never sees the staff shell. Staff and instructors never see the student sitting.
- Master Admin: catalog, price, delete, archive, roster, the one instructor assignment, written marking, written publish, students, system admins, instructors, audit.
- System Admin: catalog authoring and student management. No price field, no delete, no archive, no roster, no instructor assignment, no written marking, no written publish. Can open a written submission and can see the examiner’s name.
- Instructor: assigned modules only, written submissions on those modules, draft mark. No catalog, no publish, no other instructor’s modules.
- Only a Master Admin can delete anything. The Master Admin account itself cannot be edited, deactivated, or deleted by anyone. No screen offers that.
- Creating a student, a system admin, or an instructor asks for name, phone, and email. There is never a password field. The new person sets a password later with a one-time code. Status can only be active or inactive.

## Accounts

- Register and login take email or phone plus password. Phone is `01` and nine more digits, stored as `+8801` and nine digits. Any other shape is rejected inline.
- Duplicate phone or duplicate email is an inline error. Do not clear the rest of the form.
- Email verification is optional. Login works before the email is verified. Do not block the app with a verify wall.
- There is no SMS OTP anywhere.
- A wrong password and a locked login look calm. Do not say which field was wrong. After too many failures, say to wait and try again.
- Forgot-password and activation codes that are expired, used, or tried too often all say the code is no longer valid. Offer a new code. Do not explain which failure it was.
- Nobody sets, sees, or resets another person’s password. Change-password always asks for the current password. The Master Admin cannot be reset from another account.
- Google is a student door only. It links when Google says that email is verified. An unverified Google email fails. An admin or instructor Google account fails, with copy that this door is for students. A deactivated account fails.
- A new Google student has no phone. Show one phone field, once. After it is saved, do not show it again. Checkout and Take exam stay disabled until the phone exists, with “ফোন নম্বর যোগ করুন”.
- A Google-only student may have no password. Do not force a password step after Google.

## Catalog

- The public tree shows a category, module, or exam only when it is published and no ancestor category is archived. Drafts are absent. An archived category hides the whole branch, including a direct link.
- The public exam shows titles, descriptions, rules, type, paper language, duration, price, and the Dhaka start and end. It does not show stems, options, the correct option, an explanation, a file name, or PDF bytes.
- Staff lists include drafts. A student who is signed in still uses the public pages to browse. Their own list, “আমার মডিউল”, contains only published modules they can sit: a roster mark, a module purchase, or a purchase of one exam in that module.
- On that private module page, a module purchase or a roster mark lists every published exam. An exam purchase lists that exam only. The public module page still lists every published exam.
- Both languages are stored for titles, descriptions, and rules. The language switch changes that chrome. It does not rewrite the question body.
- Paper language is Bangla or English, chosen per exam. It only selects the typeface of the stems, the options, or the written PDF.
- A category cannot be deleted while it still has a subcategory or a module. Say so. Archive is allowed and hides the branch.
- Two categories under the same parent cannot share a position. Two modules in the same category cannot share a position.
- A module belongs to one category. Its access mode stays manual. Do not draw a course link or a second access mode.
- An archived module cannot be edited. Show it read-only.
- Any edit of a module or an exam returns it to draft and clears the final save. Say “সম্পাদনা করলে আবার ড্রাফট হবে” before save.
- Publish stays disabled until final save has been done.
- A module cannot be published while its category branch is archived.

## Price and purchase

- Price is optional. When set, it is greater than zero, in taka, with at most two decimal places. Only the Master Admin sees an input. System Admin sees the amount as text.
- A null price has no buy button. The note is “দাম এখনো নির্ধারণ হয়নি।”
- Buy is shown on a published exam or module that is not in an archived branch. A draft cannot be bought.
- The student sends either a module or an exam, never both.
- A roster mark or a module purchase means the student already has the whole module. Hide every buy button on that module and on its exams, and show “কেনা হয়েছে” or “অ্যাক্সেস আছে”.
- An exam purchase marks that exam only. The student can still buy the whole module. They cannot buy that same exam again.
- Checkout needs a phone. The gateway is SSLCommerz, in taka. There is no bKash, Nagad, or cash button, and no staff button that marks an order paid.
- Starting checkout twice reopens the same pending order. It does not create a second one.
- Cancel exists only while the order is pending. Paid, failed, and cancelled orders have no cancel button.
- The return screen polls. Pending stays pending. Paid names the module or exam and links into it. Failed and cancelled grant nothing and offer another try. If the gateway is down, say it is unavailable. Do not show a success state.

## Exams and the window

- An exam is MCQ or Written. The type does not change after create. Do not draw a type switch on an existing exam.
- Duration is required and is a positive number of minutes. Pass percent is 0 through 100. It may be empty in a draft. Publish stays disabled until it is set.
- Start may be empty. The routine cell then says “তারিখ ঘোষণা হবে”. End is start plus duration. Do not ask the author for an end time.
- The server clock decides the window. Do not trust the phone clock for enabling Take exam. Show the server start and a countdown from the deadline the server returns.
- Inside one module, a dated exam must start at least five minutes after every other dated published exam ends. Show the conflict with the other exam’s name and time, and keep publish disabled. Another module may use the same clock time. An exam with no date is left out of this check.
- A dated exam whose window has already ended cannot be published.
- Setting or clearing the start time, and any other edit, returns the exam to draft. The new time is live only after publish succeeds.
- After the start instant, the question paper, the PDF, the language, and the schedule are locked. The editor becomes read-only and says so. Edits before the start are allowed and return the exam to draft.
- MCQ publish needs at least one question. There is no maximum field. The total is the sum of the question marks.
- Each MCQ question has a stem, marks of at least 1, an optional explanation, and at least two options. Exactly one option is correct. The editor makes a second “correct” uncheck the first.
- Written publish needs a maximum of at least 1 and one question PDF. The PDF must be a real PDF. There is no question builder on a written exam.
- A wrong MCQ option scores nothing. There is no negative-mark field and no negative-mark stat.

## Sitting

- Take exam, retake, and checkout require a phone. Without one, the button is disabled.
- Take exam also requires a roster mark or a purchase. Without access, the public offer shows price and buy. It does not show Take exam.
- Take exam creates the sitting only when the server time is inside the shared window. Before the start, after the end, or with no date, the button is disabled and the reason is visible: not started, ended, or date not announced.
- Opening Take exam again during that same sitting resumes it. Do not draw a second attempt.
- One sitting row exists per student per exam. A student who sat the live paper cannot start another.
- MCQ questions appear only after Take exam, and only while that sitting is open or, later, in review. They are absent on the offer page.
- During the sitting the page must remember the selected option itself. The server does not echo the selection, and it does not reveal the correct option or the explanation, until the deadline has passed.
- Saving answers replaces the whole paper. The student can leave questions blank. A blank paper scores zero and is not a pass.
- The student may submit early. The score can show then. The options stay editable until the deadline, and the score is settled again at the deadline. Copy near the score: “সময় শেষ হওয়া পর্যন্ত উত্তর বদলানো যাবে।”
- If the student never submits, the deadline posts the saved answers anyway. Do not make a submit click the only way the score exists.
- The correct option, the student’s choice, and the explanation appear only after the deadline. Not after early submit.
- A later MCQ attempt is a separate button, shown only when all of these are true: the exam is MCQ, the student has access, the shared window has ended, a date exists, and the student never tapped Take exam. It uses a personal timer of the exam’s duration. Label it so it is clearly off the live leaderboard. Hide it in every other case. A written exam never shows it.
- The written question PDF is requested only during that sitting. Show it inside the page. No download, no new tab, no public link. Overlay the student’s name and id. When the deadline passes, the viewer closes.
- The written answer is one PDF. Check the header, not the file extension. Images, photos, and a camera button are absent. The ceiling is 80 MB. A new file replaces the old one. Replacement stays available until the deadline, including after a submit click. The submit click does not lock the upload.
- At the deadline, no stored PDF is “জমা হয়নি”, score zero. That zero is not an instructor mark and it cannot be marked later.

## Results and leaderboard

- MCQ score and pass or fail can show as soon as the paper is posted. Pass uses the exam’s pass percent against the maximum. A blank paper is not a pass.
- Written marks, pass or fail, comment, and the marked PDF stay hidden until the Master Admin publishes. Show “অপেক্ষমাণ”. A failed-to-submit zero is the exception: the student can see that zero without publish.
- The student never sees the examiner’s name, on the result or on the marked PDF.
- The leaderboard is per exam. It is empty until five minutes after the window, and empty when the date is not announced. Say when it opens.
- Rows are live sittings only. The later MCQ attempt is excluded. Highest score first. Equal scores share a rank, and the next rank skips. Columns are name, rank, score, maximum, and pass. No phone number.
- A written row waits for publish, except a failed-to-submit zero, which is on the board.
- Staff and the assigned instructor can open the same board for that module. A student without access cannot.

## Roster, instructor, marking

- Roster search, mark, paste, and remove are Master Admin only. Paste reports how many matched and lists the lines that matched nothing. Removing a student after they have started does not erase the sitting. Their result stays readable. System Admin has no roster tab.
- One instructor per module. The control exists only while the module has none. The chosen person must already be an instructor. There is no replace and no remove.
- The submission list is published written exams only. MCQ exams are not rows in it. An instructor sees assigned modules only. System Admin can read every module’s list and the examiner’s name, and cannot download the answer PDF.
- Download of the answer PDF is Master Admin or the assigned instructor, and only when a file exists.
- A draft mark requires a submitted paper that has an answer PDF, marks from 0 through the maximum, and a marked PDF. The comment is optional. The marked PDF is required on every save, including a later one. A failed-to-submit row has no mark form.
- Saving a draft, including a Master Admin save, does not publish. Each later save clears publish and the result returns to hidden until the Master Admin publishes again. Say that next to the save button.
- Publish is a separate Master Admin button. It stays disabled until a draft mark exists. Instructor and System Admin do not see it. MCQ has no publish button. MCQ scores post by themselves.

---

# Prompt 1 — student website

Design a mobile-first responsive website, Bangla default, for “এক্সাম পোর্টাল”. Use the visual system in the section above. Desktop keeps the same column; do not stretch the exam into a wide dashboard. Include light and dark. Generate these screens.

**1. Home.** Centered hero. The word “এক্সাম” is huge, gradient from gold to navy, clipped to the text. Under it: “আপনার প্রস্তুতি, আপনার পরীক্ষা — এক জায়গায়”. Two equal cards: “✅ এমসিকিউ / দ্রুত অনুশীলন” and “✍️ রিটেন / লিখিত পরীক্ষা”. No breadcrumb on home.

**2. Written categories.** Breadcrumb হোম. Title “রিটেন এক্সাম”. Two full-width cards with a gold chevron: “⚖️ বিজেএস এক্সাম / জুডিশিয়াল সার্ভিস পরীক্ষা” and “🏛️ বার কাউন্সিল এক্সাম / আইনজীবী নথিভুক্তি পরীক্ষা”.

**3. Coming soon.** Breadcrumb হোম › রিটেন › বার কাউন্সিল. A card with pill “শীঘ্রই আসছে” and the line “এই ক্যাটাগরির পরীক্ষা এখানে যুক্ত হবে।”

**4. Batches.** Breadcrumb back to বিজেএস. Title “বিজেএস এক্সাম”. Cards “১৯তম বিজেএস” and “২০তম বিজেএস”, subtitle “পরীক্ষাসমূহ দেখুন”.

**5. Module, signed-out visitor.** Title “১৯তম বিজেএস”. Three chips in a row: Routine (gold top, “তারিখ ঘোষণা হবে”), Script (orange top, pulsing pill “অপেক্ষমাণ”), Result (green top, pill “শীঘ্রই”). Then a card “পরীক্ষার রুটিন” with a table: পরীক্ষা, তারিখ, সময়. Four rows সিপিসি, সিআরপিসি, পেনাল কোড, ইভিডেন্স, dates as an em dash. Then “পরীক্ষার তালিকা”: each exam is a card with a status pill — সিপিসি “দেওয়া হয়েছে” green, সিআরপিসি “খাতা অপেক্ষমাণ” orange, the other two “বাকি” muted. A second block on the same screen shows the signed-in buyer version: module price “৳ ২,৫০০” with “মডিউল কিনুন”, and each exam shows “৳ ৪০০” or “কেনা হয়েছে”. Routine dates that are unset say “তারিখ ঘোষণা হবে”.

**6. Written exam, no access.** Title “সিপিসি” and pill “১৯তম বিজেএস”. Short description and rules. Price “৳ ৪০০” and primary button “পরীক্ষা কিনুন”. Secondary button “মডিউল কিনুন · ৳ ২,৫০০”. A note: “প্রশ্নপত্র কেনার আগে দেখা যাবে না।” No question preview. Take exam is absent.

**7. Written exam, owned, not live yet.** Same header. Instructions card: “প্রশ্নপত্র পরীক্ষার সময় আনলক হবে” and “উত্তর পিডিএফ আপলোড করুন। ছবি গ্রহণযোগ্য নয়।” Button “প্রশ্নপত্র দেখুন” disabled. Upload area disabled. Status line “শুরু: ১২ নভেম্বর, দুপুর ১২:০০”.

**8. Written sitting, live.** Sticky bar: exam name, countdown “০১:১২:৪০”, and “জমা আছে”. The PDF sits in the page at a readable height, no download, no open-in-new-tab. A diagonal watermark reads “নাঈম হাসান · STU-1042”. Below, a dashed drop zone: “খাতার পিডিএফ এখানে আপলোড করুন” and helper “শুধু পিডিএফ, সর্বোচ্চ ৮০ এমবি”. Primary “খাতা জমা দিন”. After a file is chosen, show the file name and “বদলে দিন”.

**9. Written result, unpublished.** Three stats: প্রাপ্ত নম্বর “—”, মেধাক্রম “—”, খাতা মূল্যায়ন “অপেক্ষমাণ”. No examiner name. No marked-PDF link.

**10. Written result, published.** Stats “৪২ / ৫০”, rank “৬”, “উত্তীর্ণ”. A comment from the examiner with no name. Button “মূল্যায়িত খাতা দেখুন”. Link “মেধাতালিকা”.

**11. MCQ list.** Same card pattern as categories. Four subjects with Bangla subtitles: সিপিসি দেওয়ানি কার্যবিধি, সিআরপিসি ফৌজদারি কার্যবিধি, পেনাল কোড দণ্ডবিধি, ইভিডেন্স সাক্ষ্য আইন.

**12. MCQ offer, owned and live.** Stats row: “৫০ প্রশ্ন” and “৪০ মি.” only. Do not show negative marks. Primary “পরীক্ষা শুরু করুন”. Pass mark “৪০%”.

**13. MCQ paper.** Looks like an exam sheet, not a quiz app. Header with title, question “১২ / ৫০”, and a countdown. One question in a serif-like Bangla block, four options A–D as rows with a selected state on B. Footer: “আগের”, “পরের”, “সংরক্ষিত”, and “জমা দিন”. A thin progress track.

**14. MCQ review, after the sitting.** Same paper. The chosen wrong option is pending-orange, the correct option is success-green, and a short explanation sits under the question. Score “৩৮ / ৫০” at the top. Banner if this was the later attempt: “এই চেষ্টা মেধাতালিকায় উঠবে না।”

**15. Leaderboard.** Title and “লাইভ উইন্ডোর ৫ মিনিট পর খুলবে” as the empty state. Filled state: ranks 1–8, tied ranks share a number, columns নাম, নম্বর, উত্তীর্ণ. No phone numbers.

**16. Student dashboard.** For a signed-in student. Greeting “নাঈম হাসান”. Section “এখন চলছে” with one live card and “পরীক্ষা শুরু করুন”. Section “আসন্ন” with two rows, soonest first. Section “আমার মডিউল” with ১৯তম বিজেএস and a “কেনা হয়েছে” pill. Header bell with an unread count “২” that opens the notices screen. Do not invent a settings maze behind it.

**17. Accounts.** Four short screens, same card width. Login: email or phone, password, “গুগল দিয়ে চালু করুন”, links to register and forgot password. Register: name, phone, email, password, no OTP field. Set phone: one phone field and “এই নম্বরটি একবারই লাগবে”, shown after Google for a new student. Activate: one-time code and a new password, for an invited account. Forgot password: email only, then a calm “মেইল গেলে লিংক থাকবে” confirmation.

**18. Payment result.** Three states of one screen: Pending “পেমেন্ট যাচাই হচ্ছে”, Paid “১৯তম বিজেএস খুলেছে” with “মডিউলে যান”, Failed “টাকা কাটা হয়নি” with “আবার চেষ্টা করুন”. No access granted on the failed state.

**19. Account.** Name editable, email read-only, phone, student code, language, theme, “পাসওয়ার্ড বদলান” asking for the current password. Logout.

**20. Empty, loading, error.** One module screen as a skeleton. One list that says “কিছু পাওয়া যায়নি” after a search. One inline error “পরীক্ষা এখনো শুরু হয়নি” on a disabled Take exam button. One session-expired login redirect.

**21. Notices.** A full inbox, not a dropdown. Title “নোটিশ”. Unread rows have a gold dot. Three rows: “সিপিসি আগামীকাল, দুপুর ১২:০০” (reminder), “সিআরপিসি এমসিকিউ · ৩৮/৫০” (score posted), “পেনাল কোড · ফল প্রকাশিত · ৪২/৫০” (written publish). Each row shows the time and opens that exam. “পঠিত করুন” on a row, and “সব পঠিত” at the top. A second state of the same screen is empty: “এখন কোনো নোটিশ নেই।” No reply field, no broadcast, no examiner name, no other student’s result.

Also generate the English home screen (hero “Exam”, tagline “Your preparation, your exams — all in one place”, cards MCQ and Written) and the English module screen with the same chips translated: Routine, Script, Result.

---

# Prompt 2 — staff website

Same brand as Prompt 1. This is a desktop web app for operating the exams. Min width 1280 for the main mock, plus a 768px tablet variant of the catalog list and a 390px variant where the sidebar is a menu and each table row is a card. Include light and dark, Bangla default, EN toggle in the top bar.

Shell: left sidebar 240px, logo “⚖️ এক্সামপোর্টাল”, role pill, nav, then a top bar with a global search (“পরীক্ষা, মডিউল, শিক্ষার্থী, ফোন, ইমেইল”), the user name, language, theme, logout. Content max about 1200px. Breadcrumbs on every page except the home of that role. Tables: sticky header, sortable column labels, 20 rows, a page size control, and a footer “১–২০ / ১২৮”. Search is always visible. Filters are chips, not a hidden panel. Selected filters can be cleared with one control. Empty, no-results, loading skeleton, and an inline error with “আবার চেষ্টা” each appear once in the set. Destructive buttons are the outline style until a confirm dialog. Do not use a password field when creating a person. Do not add courses, books, or manual bKash.

Make three nav variants. Master Admin: সারসংক্ষেপ, ক্যাটাগরি, মডিউল, অংশগ্রহণ, শিক্ষার্থী, ইনস্ট্রাক্টর, অডিট, অ্যাকাউন্ট. System Admin: ক্যাটাগরি, মডিউল, অংশগ্রহণ, শিক্ষার্থী, জমা তালিকা, অ্যাকাউন্ট. Hide price, delete, archive, roster, assign, and “ফলাফল প্রকাশ”. Instructor: আমার মডিউল, অংশগ্রহণ, অ্যাকাউন্ট. The instructor participation item lists assigned modules only. Nothing else. Students are the only role with a notices inbox.

**A. Master Admin overview.** Four stat cards: চলমান পরীক্ষা, জমা, জমা হয়নি, প্রকাশের অপেক্ষায়. A table of exams in the next seven days, each with the four participation counts. Clicking a stat or a count opens the participation screen already filtered.

**B. Category tree.** Search. A nested list: বিজেএস এক্সাম, under it ১৯তম and ২০তম, under ১৯তম the modules. Status chips ড্রাফট, প্রকাশিত, আর্কাইভ. Buttons নতুন ক্যাটাগরি. Archive and delete only in the Master Admin version, with a confirm dialog that names the branch. System Admin sees the same tree without those two buttons.

**C. Category editor.** Side-by-side fields শিরোনাম (English) and শিরোনাম (বাংলা). Parent category selector. Position. Save. Dirty-state line “সেভ হয়নি”.

**D. Module list.** Search, filter by category and status, sort by position. Columns: নাম, ক্যাটাগরি, দাম, স্ট্যাটাস, পরীক্ষা সংখ্যা. Master Admin price cell is editable in the editor, not inline. System Admin sees price as text and has no price input.

**E. Module editor, Master Admin.** Tabs: বিবরণ, পরীক্ষাসমূহ, রোস্টার, ইনস্ট্রাক্টর. Description tab: both languages, price “৳”, final-save, publish, and a note “সম্পাদনা করলে আবার ড্রাফট হবে”. Exam tab: the four law exams, type, language, date, draft/published, “নতুন পরীক্ষা”, and a text button “অংশগ্রহণ” on each row. Roster tab: search by name, phone, or email; results to mark; a paste box “ইমেইল বা ফোন, এক লাইনে একটি”; the current roster with remove. Instructor tab: the one assigned person and their phone, or an empty state “কেউ নেই” with “ইনস্ট্রাক্টর দিন” opening the directory as a picker. Once someone is assigned, the picker is gone. There is no replace and no remove. A second assign shows “এই মডিউলে ইতিমধ্যে একজন আছেন।” System Admin variant of this screen: no price field, no roster tab, no instructor tab, no delete. The exam rows still link to participation.

**F. Exam editor, MCQ.** Both titles, descriptions, and rules. Type এমসিকিউ. Paper language বাংলা / English. Duration, pass percent, start time with a “তারিখ এখনো নয়” switch. Question builder: stem, four options, one marked correct, marks, explanation, drag handle, add question. Running total “মোট ৫০”. Final save and publish. A warning if the next exam in the module starts less than five minutes after this one ends.

**G. Exam editor, Written.** Same meta. One PDF field “প্রশ্নপত্র” with the file name. Maximum marks typed by the admin, not a sum. No question builder.

**H. Students.** Search by name, phone, email. Filters: active, pending activation. Columns: নাম, ফোন, ইমেইল, স্ট্যাটাস. “নতুন শিক্ষার্থী” opens a form with name, phone, email only. Row actions: status, “কোড আবার দিন”. Delete only on the Master Admin screen, with confirm. No password, no “reset their password”.

**I. Instructor directory.** Master Admin only. This is a full list, not a note under another page. Search by name, phone, or email. Filters: সক্রিয়, অ্যাক্টিভেশন বাকি, মডিউল নেই. Columns: নাম, ফোন, ইমেইল, স্ট্যাটাস, মডিউল. A row shows “১৯তম বিজেএস” or “কোনো মডিউল নেই”. One instructor, one module, so the module cell never lists two. “নতুন ইনস্ট্রাক্টর” opens name, phone, email only. Pending rows show “অ্যাক্টিভেশন কোড আবার দিন”. Clicking a row opens a small profile: contact, status, the assigned module, and “মডিউল থেকে সরান” is absent — removal is done by assigning someone else. Empty search: “কোনো ইনস্ট্রাক্টর পাওয়া যায়নি।” The same list, without the create button, is the picker used from a module. System Admin does not have this page.

**I2. System admin directory.** Master Admin only. Same search, filters, and create form as students. Columns: নাম, ফোন, ইমেইল, স্ট্যাটাস. Pending rows can reissue the activation code. The Master Admin’s own row is absent. No password and no role change.

**J. Instructor home.** Only “১৯তম বিজেএস” and one other module. Each card shows the four participation counts and how many drafts are waiting. No catalog links.

**K. Submission list.** Search by student name. Filters: all, draft, published, failed to submit. Columns: শিক্ষার্থী, পরীক্ষা, স্ট্যাটাস, নম্বর, প্রকাশিত, মূল্যায়নকারী. The four counts sit above the table and match the participation screen. System Admin sees the evaluator name and no row action except view. Instructor does not see modules they are not assigned.

**L. Submission, instructor.** Student name, exam, status. Link “উত্তরপত্র ডাউনলোড” only when an answer PDF exists. Form: marks from 0 through the maximum, optional comment, and a marked PDF that is required on every save. Primary button “ড্রাফট সেভ”. A persistent note “শিক্ষার্থী প্রকাশের আগে এটা দেখবে না।” No publish button. If the student never uploaded a PDF, the form is absent and the row says “জমা হয়নি · নম্বর দেওয়া যাবে না.” The list itself contains written exams only.

**M. Submission, Master Admin.** The same form, plus “ফলাফল প্রকাশ করুন”, disabled until a draft exists. After publish, a “প্রকাশিত” pill. Helper: “আবার সেভ করলে প্রকাশ বাতিল হবে।”

**N. Audit log.** Master Admin only. Filters by day and actor. Columns: সময়, কে, কী. Read only.

**O. Staff account.** Name, role, change password with current password, language, theme, logout.

**P. Dialogs.** Confirm archive. Confirm delete. Publish confirmation. Unsaved-changes confirmation. Roster paste result: “১২ জন যুক্ত, ৩ জন পাওয়া যায়নি” with the unknown lines listed.

**Q. Participation.** A browseable list, one row per exam. Search by exam or module name. Filters: মডিউল, এমসিকিউ / রিটেন, আজ, আসন্ন, শেষ, তারিখ ঘোষণা হয়নি. Columns: পরীক্ষা, মডিউল, ধরন, তারিখ, বসেছে, জমা দিয়েছে, জমা হয়নি, প্রকাশের অপেক্ষায়. Sample row: সিপিসি, ১৯তম বিজেএস, রিটেন, ১২ নভেম্বর, ৮৪, ৭১, ১৩, ৯. Clicking a count opens the submission list filtered to that count. MCQ rows show “—” in the drafts column because there is nothing to publish. An exam with no date still appears, with the date cell “তারিখ ঘোষণা হবে” and zeros. Sort by date and by each count. Master Admin and System Admin see the full list. The instructor variant has the same columns and only the assigned modules. No export button and no chart.

---

# Single-screen prompts

Use these only if a full paste is truncated. Start each one with: “Exam Portal, Bangla, visual system from the client mockup: cream #f6f4ee, navy #14213d, gold #c8962e, card radius 16px, Hind Siliguri, light and dark, 44px targets, navy text on gold buttons.”

1. Student home, two doors, giant “এক্সাম” hero.
2. Written category list, BJS and Bar Council.
3. Bar Council coming-soon state.
4. BJS batch list, 19th and 20th.
5. 19th BJS module: routine table, exam list, status pills, prices, buy module.
6. CPC written exam, not purchased.
7. CPC written exam, purchased, locked until noon.
8. Live written sitting: in-page PDF, watermark, PDF-only drop zone, countdown.
9. Written result pending, no examiner name.
10. Written result published, marks, comment, marked script, rank.
11. MCQ subject list.
12. MCQ offer with start button, no negative-mark stat.
13. MCQ paper, formal sheet, one question, four options, timer.
14. MCQ review with correct option and explanation.
15. Leaderboard, empty and filled.
16. Student dashboard: live now, upcoming, my modules, unread bell.
17. Login with Google, register, set phone, activate, forgot password.
18. Payment result: pending, paid, failed.
19. Student account.
20. Master Admin overview.
21. Category tree and editor.
22. Module editor with description, exams, roster paste, one instructor.
23. System Admin module editor without price, roster, or instructor.
24. MCQ exam editor with question builder.
25. Written exam editor with one question PDF.
26. Student directory, create without a password.
27. Instructor home and submission list with count chips.
28. Instructor draft mark, no publish.
29. Master Admin publish action.
30. Audit log.
31. Student notices inbox: reminder, MCQ score, written result published, mark read, and the empty state.
32. Instructor directory: search, status filter, one assigned module, create without a password, and the same list as an assign picker.
33. Participation list: four counts per exam, filters, sort, and a click-through into the matching submissions. Include the instructor’s shorter list.
