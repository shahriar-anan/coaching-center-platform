# Student flow prompts

Three prompts for a UI generator. Copy one prompt at a time, from its heading through the line `END OF PROMPT`. Each prompt stands alone.

The pictures are the student website only. Bangla is the default. Generate the Bangla screens, plus the one English screen named at the end of each prompt.

Sample people and exams, reused in every prompt:

- Student: নাঈম হাসান, phone `01711000001`, email `naeem@example.com`, student code `S18492017`.
- Path: রিটেন → বিজেএস এক্সাম → ১৯তম বিজেএস → সিপিসি (written), সিআরপিসি (MCQ).
- বার কাউন্সিল এক্সাম is an empty category.
- Module price ৳ ২,৫০০. Each exam ৳ ৪০০. সিপিসি has no date yet. সিআরপিসি starts ১২ নভেম্বর, দুপুর ১২:০০, runs ৪০ minutes, so it ends দুপুর ১২:৪০.

---

# Prompt 1 — Student onboarding

END OF PROMPT is the last line of this prompt.

## What to generate

A mobile-first website called এক্সাম পোর্টাল. This prompt is only the entrance: the landing page, creating an account, signing in, recovering a password, and activating an invited account. Do not design the exam catalog, purchase, notices, or an exam paper. Those are later prompts. After a successful sign-in, stop on a simple “হোমে যান” confirmation. Do not invent the home beyond the landing page specified here.

## Visual system — match the attached client HTML

Text, color, and layout come from that file. Do not restyle them.

- Page `#f6f4ee`, card `#ffffff`, text `#14213d`, muted `#5d6578`, gold `#c8962e`, border `#e3dfd3`. Pending `#e8590c`. Success `#2f9e44`.
- Dark: page `#0e1424`, card `#172038`, text `#eef0f6`, muted `#a3abc0`, gold `#e0b254`, border `#26304d`. Follow the system theme until the student toggles it. The toggle is a pill in the header.
- Type: Hind Siliguri, then Noto Sans Bengali, then SolaimanLipi. Line height 1.6. Body at least 16px. Section titles 20px, weight 700. Card titles 17px, weight 700.
- Column max 760px, centered, 16px padding. Respect the safe-area inset. On a wide screen keep this column. Do not become a split marketing page.
- Header: “⚖️ এক্সাম**পোর্টাল**”, with পোর্টাল in gold, 18px, weight 700. On the right, a language pill (shows `EN` while the UI is Bangla) and a theme pill.
- Cards: white, 1px border, radius 16px, padding 18px. Hover lifts 2px and turns the border gold. Press has no extra decoration.
- Primary button: gold fill, radius 12px, padding 12px 18px, full width, weight 700. Secondary button: transparent, navy text, 1px border.
- Links and chevrons are gold. Muted text is for help, not for actions.
- Breadcrumbs are 14px muted, with gold links, separated by `›`. The landing page has no breadcrumb.
- Screen changes fade up 6px in 0.25s. If the user prefers reduced motion, do not animate.
- Pills are fully rounded, 12px, weight 700.

## Standards beyond the HTML

The HTML’s white label on gold fails contrast. Put navy `#14213d` text on gold buttons in both themes. Keep white text only on navy or on the orange and green pills.

- Every control is at least 44px tall. Inputs have a visible label, not only a placeholder. The error sits under its field. A focus ring is visible.
- Do not use color as the only status. A pill always contains words.
- One primary button per screen. The keyboard order follows the visual order. Escape closes a dialog.
- Password fields have a show/hide control and `autocomplete` of `new-password` or `current-password`. Email uses an email keyboard. Phone uses a numeric keyboard.
- Disable the primary button and show a spinner while a request is in flight. Leave the typed values in place after an error.
- Loading is a skeleton of the same cards, not a blank page. A failed load has “আবার চেষ্টা” and does not clear the form.
- 320px width must not scroll sideways. The two home cards may stack under 380px.

## Rules the API enforces

- Self-registration creates an active student. Fields are name, phone, email, and password. Password is 8 to 72 characters. Phone must be `01` plus nine digits, or already `+8801` plus nine digits. There is no SMS code.
- Registration does not sign the student in. The next screen is a success message with a button to the login page.
- Duplicate phone and duplicate email are separate inline errors. Keep the other fields.
- Login accepts email or phone, plus password. A wrong password, an unknown account, a deactivated account, and an account that has not set a password all look the same: the credentials were not accepted. Do not say which part failed.
- Too many failures return a lock. Show “একটু পরে আবার চেষ্টা করুন।” Do not show a countdown the server did not send.
- Google sign-in is for students. The button starts a redirect. There is no Google popup designed here. On return: a student with a phone continues; a new student with no phone must set a phone before anything else; an admin, an instructor, an unverified Google email, or a deactivated account sees “এই প্রবেশ শিক্ষার্থীদের জন্য” or “এই অ্যাকাউন্ট দিয়ে প্রবেশ করা যাবে না” and stays signed out.
- The phone can be set only once. The field disappears after it is saved. A duplicate phone is an inline error.
- An invited student has no password. Activation asks for phone, the one-time code, and a new password of 8 to 72 characters. Success does not sign them in. Send them to login. An expired, used, or over-tried code all say “কোডটি আর ব্যবহার করা যাবে না।” Offer no extra diagnosis.
- Forgot password asks for an email only. The confirmation is always “এই ইমেইলে অ্যাকাউন্ট থাকলে একটি বার্তা পাঠানো হয়েছে।” Show that sentence even when the email is unknown. Reset asks for the code from the mail and a new password, then returns to login. Same invalid-code sentence as activation.
- Email verification does not block login. Do not design a wall that traps a new student. A later account screen can verify. It is not part of this entrance.
- A signed-in session that has expired returns to login and keeps what the student was trying to open. This prompt only needs the login page reading “আবার প্রবেশ করুন.”
- No account in this flow sets another person’s password. No staff screens.

## Pages

**Landing.** Centered hero. The word “এক্সাম” is `clamp(64px, 22vw, 110px)`, weight 800, line height 1.05, gradient from gold to navy, clipped to the glyphs. Under it, muted: “আপনার প্রস্তুতি, আপনার পরীক্ষা — এক জায়গায়”. Two equal cards: “✅ এমসিকিউ” with “দ্রুত অনুশীলন”, and “✍️ রিটেন” with “লিখিত পরীক্ষা”. Those two cards are the product entrance. In this prompt they do not open a catalog. Header has no account yet: add a text button “প্রবেশ” beside the language and theme pills. Footer of the hero area: text links “অ্যাকাউন্ট খুলুন” and “কোড দিয়ে সক্রিয় করুন”.

**Login.** Breadcrumb হোম › প্রবেশ. Title “প্রবেশ”. Fields: “ইমেইল বা ফোন”, “পাসওয়ার্ড”. Primary “প্রবেশ করুন”. Secondary full-width “গুগল দিয়ে চালু করুন”. Text links: “অ্যাকাউন্ট খুলুন”, “পাসওয়ার্ড ভুলে গেছেন?”, “কোড দিয়ে সক্রিয় করুন”. Error state under the form, not an alert dialog: “ইমেইল, ফোন, বা পাসওয়ার্ড মিলছে না।” Locked state replaces that sentence with the wait sentence. Success state is a card “প্রবেশ হয়েছে” and “হোমে যান”. Do not design the destination.

**Register.** Breadcrumb হোম › অ্যাকাউন্ট. Title “অ্যাকাউন্ট খুলুন”. Fields: নাম, ফোন, ইমেইল, পাসওয়ার্ড. Helper under the phone: “০১ এবং আরও ৯ সংখ্যা।” Helper under the password: “৮ থেকে ৭২ অক্ষর।” No OTP field. Primary “অ্যাকাউন্ট খুলুন”. Link back to login. Show the duplicate-phone error and the duplicate-email error as two separate states. Success is its own screen: “অ্যাকাউন্ট তৈরি হয়েছে” and primary “প্রবেশ করুন”. The student is not inside the app yet.

**Set phone.** Only after Google, and only when the account has no phone. Title “ফোন নম্বর”. One field and the helper “এই নম্বরটি একবারই লাগবে। পরে বদলানো যাবে না।” Primary “সংরক্ষণ”. No skip. Duplicate-phone error stays on this page. Success continues to “প্রবেশ হয়েছে”.

**Activate.** Breadcrumb হোম › সক্রিয়করণ. For a student who was created by an admin. Fields: ফোন, কোড, নতুন পাসওয়ার্ড. Primary “সক্রিয় করুন”. Invalid-code state uses the single sentence above. Success: “অ্যাকাউন্ট সক্রিয়” and “প্রবেশ করুন”. Not signed in yet.

**Forgot password.** One email field. Primary “কোড পাঠান”. The following screen always shows the generic mail sentence and a link back to login. Do not say “we found your account”. The mail contains a code, not a link.

**Reset password.** Fields: কোড, নতুন পাসওয়ার্ড. Primary “পাসওয়ার্ড বদলান”. Invalid code. Success returns to login with “এখন নতুন পাসওয়ার্ড দিয়ে প্রবেশ করুন।”

**English landing.** Same layout. Hero “Exam”. Tagline “Your preparation, your exams — all in one place”. Cards “MCQ / Quick practice” and “Written / Written test”. The language pill reads “বাংলা”.

## Do not draw

Catalog lists, prices, exams, payment, notices, a question paper, a PDF, a dashboard, or any admin screen. No negative marks. No photo upload. No SMS code.

END OF PROMPT

---

# Prompt 2 — Signed-in browsing, purchase, notices, and account

END OF PROMPT is the last line of this prompt.

## What to generate

The same এক্সাম পোর্টাল, for a student who is already signed in. Generate every page they need to browse categories and modules, read an exam offer, buy one exam or a whole module, see the payment result, read notices, and manage the account. Do not generate the live question paper or the written PDF viewer. Those belong to the next prompt. Stop the “take exam” path at the exam offer.

The catalog is also readable with no account. Generate the module page twice: signed out, and signed in. Purchase, notices, the dashboard, and account exist only when signed in.

## Visual system — match the attached client HTML

- Page `#f6f4ee`, card `#ffffff`, text `#14213d`, muted `#5d6578`, gold `#c8962e`, border `#e3dfd3`. Pending `#e8590c`. Success `#2f9e44`.
- Dark: page `#0e1424`, card `#172038`, text `#eef0f6`, muted `#a3abc0`, gold `#e0b254`, border `#26304d`.
- Type: Hind Siliguri, then Noto Sans Bengali. Body at least 16px, line height 1.6. Titles 20px. Card titles 17px, weight 700.
- Column max 760px, centered, 16px padding, safe-area inset. Do not stretch into a dashboard on desktop.
- Header: “⚖️ এক্সাম**পোর্টাল**”, পোর্টাল in gold. Signed in: an unread bell, the language pill, the theme pill, and the name “নাঈম”. Signed out: language, theme, and “প্রবেশ”.
- Cards radius 16px, border, padding 18px, gold chevron on the right when the card navigates. Hover lifts 2px.
- Primary button full width, gold, radius 12px, navy text (the HTML uses white; navy is required for contrast). Secondary button is an outline.
- Three module chips in a row. Each has a 4px top border: gold for routine, orange `#e8590c` for script, green `#2f9e44` for result. They scroll the page to that section. They are not separate routes.
- Pills: orange and pulsing for a waiting state, green without a pulse for a calm state, muted for upcoming. Reduced motion removes the pulse. A pill always has words.
- Routine is a table inside a card. The table scrolls sideways instead of squeezing the columns. Header cells are muted, weight 600.
- Breadcrumbs 14px, gold links, `›` between them. No breadcrumb on the home screen.
- Sections are cards with a 17px title. Lists inside them are muted.

## Standards beyond the HTML

- 44px targets, visible labels, errors under the field that failed, visible focus, logical tab order.
- Status is never color alone.
- Skeletons match the cards they replace. Empty states say what is missing and what to do. A failed load offers “আবার চেষ্টা” and keeps the last good content if there was any.
- Confirm before leaving the site for payment: name of the purchase, amount, and what it unlocks. After confirmation, the only next step is the gateway. Do not draw a card form. SSLCommerz is outside this site.
- The signed-in header bell shows a count only when unread notices exist. Tapping it opens the inbox, not a tiny menu that hides the list.
- 320px must not overflow. The two home cards may stack under 380px. The three chips stay in one row and may shrink their labels, not wrap into a broken grid.
- Prices use Bengali digits and the ৳ sign in the Bangla UI. Absence of a price is a sentence, not a zero.

## Rules the API enforces

Browse:

- Anyone can read a published category, module, and exam. Drafts are absent. An archived category hides its whole branch, including an opened link. A missing record is a not-found page, not an empty list.
- Show the language the student selected. Bangla uses `titleBn`, `descriptionBn`, and `rulesBn`. English uses the `En` fields. Switching language does not change the exam’s paper. Paper language is a separate fact: “প্রশ্নপত্রের ভাষা: বাংলা” or “English”. It means the typeface of the future paper, not a translation of the shop text.
- A category has a title and children. A module has both titles, both descriptions, a price that may be null, and its exams. An exam has both titles, both descriptions, both rules, type (`MCQ` or `WRITTEN`), paper language (`BN` or `EN`), start, end, duration in minutes, and a price that may be null. End is start plus duration. The server sends both. Do not ask the student for an end time.
- A null start means the date is not announced. Show “তারিখ ঘোষণা হবে” in the routine. Do not show a fake date.
- The module page is the routine. There is no separate routine document. Order exams with undated ones last.
- The public offer does not include questions, options, the correct answer, an explanation, a file name, pass percent, or maximum marks. Do not invent them on the offer. Pass percent and maximum appear only after the student has access and opens the owned exam. Question count appears only after they start. That screen is the next prompt.
- Signed-in “আমার মডিউল” lists published modules this student can sit: a roster mark, a module purchase, or a purchase of one exam in the module. Inside that private list, a module purchase or a roster mark shows every published exam. An exam purchase shows that exam only. The public module page still shows every published exam to everyone.
- The personal routine lists granted published exams whose window has not ended, soonest first. Undated exams are omitted. Reading it does not start an exam.

Purchase:

- Only a signed-in student can buy. A signed-out buy button goes to login and comes back to the same exam or module.
- Buying needs a phone. If `phoneRequired` is true, the buy button is disabled and links to a one-field phone page. The phone can be saved only once.
- One purchase is either the module or one exam, never both. The module price and the exam price are independent.
- A module purchase unlocks every exam in it now and every exam published there later. Say that before the student leaves for payment. An exam purchase unlocks that exam only. Say that too.
- No price means no buy button. Copy: “দাম এখনো নির্ধারণ হয়নি।”
- A roster mark or an existing module purchase hides every buy button on that module and its exams. Show “অ্যাক্সেস আছে”.
- An existing exam purchase hides the buy button on that exam only. The module can still be bought.
- Checkout opens SSLCommerz. Do not collect a card. Starting twice continues the same pending order. Cancel exists only while the status is pending.
- The return page polls. Pending: “পেমেন্ট যাচাই হচ্ছে”. Paid: name the product and link to it. Failed and cancelled grant nothing. If the gateway is down: “পেমেন্ট গেটওয়ে এখন পাওয়া যাচ্ছে না।” Never show paid unless the status is paid.

Notices, as the product will enforce. The inbox screen is in this prompt even though the notice API is not shipped yet. Do not add email, SMS, push, a compose box, or another student’s data.

- One reminder, one to two days before a dated exam the student can already sit. No reminder when the date is empty. No reminder without access.
- One notice when that student’s MCQ score is posted.
- One notice when that student’s written result is published. A draft mark does not send it.
- The row names the exam and, for a result, that student’s own marks. It never names the examiner.

Account:

- The profile shows name, email, phone, student code, and whether the email is verified. The student can edit the name only. Email is read-only. Phone, once set, is read-only.
- Change password asks for the current password and a new one of 8 to 72 characters. A wrong current password stays on the form.
- Logout ends the session and returns to the landing page. There is no “reset my password” that skips the current password.

## Pages

**Home, signed in.** Same hero and two doors as the client file. Under the doors, a section “এখন চলছে” with one card, সিআরপিসি, and “পরীক্ষা শুরু করুন” disabled in this prompt with the note “পরের ধাপ”. Then “আসন্ন” with one dated row. Then “আমার মডিউল” with ১৯তম বিজেএস and a green pill “অ্যাক্সেস আছে”. This sample student has bought the module. A second home state, signed out, has the same hero and doors, no dashboard sections, and “প্রবেশ” in the header.

**Written categories.** Breadcrumb হোম › রিটেন. Title “রিটেন এক্সাম”. Cards: “⚖️ বিজেএস এক্সাম / জুডিশিয়াল সার্ভিস পরীক্ষা” and “🏛️ বার কাউন্সিল এক্সাম / আইনজীবী নথিভুক্তি পরীক্ষা”. Gold chevron.

**Coming soon.** Breadcrumb back to রিটেন. Title “বার কাউন্সিল এক্সাম”. One card, pill “শীঘ্রই আসছে”, sentence “এই ক্যাটাগরির পরীক্ষা এখানে যুক্ত হবে।” No empty table.

**Batches.** Title “বিজেএস এক্সাম”. Cards “১৯তম বিজেএস” and “২০তম বিজেএস”, subtitle “পরীক্ষাসমূহ দেখুন”.

**Module, signed out.** Title “১৯তম বিজেএস”. Show the Bangla description as a paragraph under the title: two or three lines about the judicial-service package. Chips: রুটিন (“তারিখ ঘোষণা হবে”), খাতা (pill “অপেক্ষমাণ”), রেজাল্ট (pill “শীঘ্রই”). Routine table columns পরীক্ষা, তারিখ, সময়. Rows: সিপিসি with em dashes, সিআরপিসি with ১২ নভেম্বর and দুপুর ১২:০০–১২:৪০, plus পেনাল কোড and ইভিডেন্স undated. Then “পরীক্ষার তালিকা”. Each card has the Bangla title, the short subject name, the type, the price ৳ ৪০০, and a pill: সিপিসি “বাকি” muted, সিআরপিসি “বাকি” muted. A bar under the description: “মডিউল ৳ ২,৫০০” and a primary “মডিউল কিনুন” that goes to login.

**Module, signed in, module already owned.** Same content. The price bar becomes a green pill “অ্যাক্সেস আছে”. Exam cards have no individual buy button. সিপিসি’s date cells say “তারিখ ঘোষণা হবে”.

**Module, signed in, nothing owned, one exam has no price.** পেনাল কোড shows “দাম এখনো নির্ধারণ হয়নি” and no buy button. The others show “৳ ৪০০” and “কিনুন”. The module buy button remains.

**Exam offer, signed out.** Breadcrumb back through ১৯তম. Title “সিআরপিসি” and a gold pill “১৯তম বিজেএস”. A muted type pill “এমসিকিউ”. Sections, each a card, and only these fields: বিবরণ, নিয়মাবলি, “প্রশ্নপত্রের ভাষা: বাংলা”, “সময় ৪০ মিনিট”, “শুরু ১২ নভেম্বর, দুপুর ১২:০০”, “শেষ দুপুর ১২:৪০”, “মূল্য ৳ ৪০০”. Sentence: “প্রশ্ন কেনার আগে দেখা যাবে না।” Primary “পরীক্ষা কিনুন”. Secondary outline “মডিউল কিনুন · ৳ ২,৫০০”. No Take exam button. No question preview.

**Exam offer, owned, not yet live.** Same sections, plus পাসের নম্বর “৪০%” and পূর্ণমান “৫০”, because access now allows those two fields. No question count. Primary “পরীক্ষা শুরু করুন” disabled. Reason: “১২ নভেম্বর, দুপুর ১২:০০ এর আগে শুরু হবে না।” No buy buttons.

**Exam offer, undated.** সিপিসি, written. Description, rules, paper language, duration, no start. “তারিখ ঘোষণা হবে।” Take exam absent. If not owned, buy is present. If owned, a disabled control says the date is not announced.

**Confirm purchase.** A sheet over the exam or the module. For an exam: “সিআরপিসি · ৳ ৪০০” and “শুধু এই পরীক্ষাটি খুলবে।” For a module: “১৯তম বিজেএস · ৳ ২,৫০০” and “এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে।” Primary “পেমেন্টে যান”. Outline “বাতিল”. No card number.

**Payment result.** Three states of one column. Pending, with a quiet repeating status and “অপেক্ষা করুন”. Paid: “১৯তম বিজেএস খুলেছে” and “মডিউলে যান”. Failed: “টাকা কাটা হয়নি” and “আবার চেষ্টা করুন”. A fourth line on the failed screen: cancelled uses “বাতিল হয়েছে” and the same retry. Gateway down replaces the page with the unavailable sentence and a retry.

**Phone required.** A single phone field reached from a disabled buy button. Helper: “কেনার আগে একটি ফোন নম্বর লাগবে। পরে বদলানো যাবে না।”

**Notices.** Title “নোটিশ”. “সব পঠিত” at the top. Three rows, unread ones with a gold dot: “সিআরপিসি আগামীকাল, দুপুর ১২:০০”; “সিআরপিসি এমসিকিউ · ৩৮/৫০”; “পেনাল কোড · ফল প্রকাশিত · ৪২/৫০”. Each row opens that exam offer. A second state of the same page: “এখন কোনো নোটিশ নেই।”

**Account.** Name editable, save button for the name only. Email read-only, with “যাচাই হয়নি” and an outline “যাচাইয়ের কোড পাঠান”. Phone read-only. Student code `S18492017` read-only. Language and theme repeat the header controls. “পাসওয়ার্ড বদলান” opens current password and new password. Logout is an outline button at the bottom, with a confirm “বেরিয়ে যাবেন?”. Wrong-current-password error stays inline.

**Empty, loading, missing.** Skeleton of the module page. A category search with no match is not required. A bad link is “কিছু পাওয়া যায়নি” and “হোমে ফিরুন”.

**English module.** Same ১৯তম structure. Title “19th BJS”. Chips Routine, Script, Result. Table headers Exam, Date, Time. The language pill reads “বাংলা”.

## Do not draw

A question stem, an option, a PDF, a countdown exam paper, a camera, a negative-mark stat, bKash, Nagad, a card form, or any staff page.

END OF PROMPT

---

# Prompt 3 — Take a live exam the student already owns

END OF PROMPT is the last line of this prompt.

## What to generate

এক্সাম পোর্টাল, for নাঈম হাসান, who already has access to ১৯তম বিজেএস. He is about to sit সিআরপিসি, an MCQ, and সিপিসি, a written exam. Generate both flows and every state the server can put him in. Do not generate registration, the shop, or payment. The entrance to these screens is the owned exam page.

## Visual system — match the attached client HTML

- Page `#f6f4ee`, card `#ffffff`, text `#14213d`, muted `#5d6578`, gold `#c8962e`, border `#e3dfd3`. Pending `#e8590c`. Success `#2f9e44`.
- Dark: page `#0e1424`, card `#172038`, text `#eef0f6`, muted `#a3abc0`, gold `#e0b254`, border `#26304d`.
- Type: Hind Siliguri, then Noto Sans Bengali, for the chrome. Body at least 16px, line height 1.6.
- The question body uses the exam’s paper language, not the site language. Bangla paper: Noto Sans Bengali. English paper: a Latin face already on the page. A Bangla site may show an English paper, and the reverse. Do not translate the stem or the PDF.
- Column max 760px, centered, 16px padding. The sitting may use the full width of that column. Do not become a two-column quiz.
- Header stays: logo, bell, language, theme. On the sitting, add a sticky bar under it with the exam name and the countdown. The bar is a card, not a new color.
- Cards radius 16px. Primary button gold with navy text, full width, radius 12px. Outline button for the secondary action.
- The client’s script block is a dashed drop zone, radius 14px, muted center text. Reuse it for the written PDF upload.
- Result stats are three cells in a row, the number in gold 20px, the label 12px muted, each cell on the page background inside the card.
- Pills keep the client colors. Reduced motion removes the pulse.

## Standards beyond the HTML

- The countdown is text, updated from the deadline the server returned, not from the phone’s own exam clock. When it hits zero, the page settles into the closed state without asking the student to refresh.
- 44px targets. The selected option has a gold border and a text label “নির্বাচিত”, not color alone. The correct option in review has a green border and the word “সঠিক”. A wrong choice has an orange border and “আপনার উত্তর”.
- The sticky bar must not cover the first question. Give the paper padding equal to the bar.
- The PDF sits in the page at a fixed readable height, with in-page page controls. No download icon, no “open in new tab”, no share. A watermark is drawn over the viewer, not baked into a file: “নাঈম হাসান · S18492017”, diagonal, low contrast, repeating enough to be visible and not enough to hide the text.
- Right-click and a download control are absent. Do not claim screenshots are blocked.
- One primary action. While a save or upload is in flight, disable that button and keep the paper on screen.
- Focus stays on the option the student just chose. The countdown must not steal focus.
- 320px: options are full-width rows. The three result stats remain one row. The drop zone does not overflow.

## Rules the API enforces

Access and the window:

- These screens assume a roster mark or a purchase. Without it, this flow does not exist. Do not put a buy button on these pages.
- Take exam and the written upload need a phone. If the phone is missing, Take exam is disabled and the only action is “ফোন নম্বর যোগ করুন”.
- The server clock is the window. Take exam is enabled only from the start instant until the shared end. Before that, the button is disabled: “১২ নভেম্বর, দুপুর ১২:০০ এর আগে শুরু হবে না।” After the end, it is disabled: “সময় শেষ।” If the start is null, it is disabled: “তারিখ ঘোষণা হবে।” The personal routine and the leaderboard are also closed while the date is null.
- Tapping Take exam during an open sitting resumes that sitting. Do not show a second paper or a “start again”.
- One sitting exists per student per exam. A student who sat the live paper cannot start another live sitting.

MCQ:

- Before Take exam, the offer shows title, description, rules, paper language, duration, start, end, pass percent, and maximum marks. It does not show stems. Do not show a question count yet. Do not show a negative mark. A wrong option scores nothing.
- After Take exam, the paper lists every stem and its options. The server does not tell the page which option is selected, and it does not send the correct option or the explanation, until the deadline has passed. The page must remember the selection.
- Saving replaces the whole set of answers. Blank questions are allowed. A blank paper scores zero and is not a pass.
- Early submit shows the score and pass or fail. The options stay editable until the deadline. Copy under the score: “সময় শেষ হওয়া পর্যন্ত উত্তর বদলানো যাবে।” The score is calculated again at the deadline. If the student never submits, the deadline posts the saved answers anyway.
- The chosen option, the correct option, and the explanation appear only after the deadline. Not after early submit.
- A later attempt exists only for an MCQ, only after the shared window has ended, only if a date exists, and only if this student never tapped Take exam. It has its own timer of the exam’s duration. It is off the leaderboard. Label the button “পরে একবার দেওয়া যাবে · মেধাতালিকায় উঠবে না।” Hide it in every other case. If he already sat the live MCQ, do not show it.
- Pass percent applies to the maximum. Show “উত্তীর্ণ” or “অনুত্তীর্ণ” from the server. Do not compute a second rule.

Written:

- The question is one PDF, available only while that sitting is open. Before Take exam, the outline button “প্রশ্নপত্র দেখুন” is disabled and the card says “প্রশ্নপত্র পরীক্ষার সময় আনলক হবে.” There is no photo instruction. The instruction is “উত্তর একটি পিডিএফ। ছবি গ্রহণযোগ্য নয়।”
- During the sitting the PDF is inline. No download and no new tab.
- The answer is one PDF, `%PDF` checked, at most 80 MB. Reject an image with “শুধু পিডিএফ।” A later file replaces the earlier one. Replacement stays available until the deadline. The submit button does not lock the upload. After a file is stored, show its name and “বদলে দিন”.
- At the deadline, no file means status “জমা হয়নি”, score 0. That zero is visible without an examiner, and it is not something an instructor will mark.
- A file that was stored waits for the Master Admin to publish. Until then the result shows “অপেক্ষমাণ” for marks, rank, comment, and the marked script. Do not show the examiner’s name after publish either. Publish reveals marks, pass or fail, the comment, and “মূল্যায়িত খাতা দেখুন”.
- A written exam has no later attempt. Do not draw a retake control.

Leaderboard, both types:

- Empty until five minutes after the shared end, with “মেধাতালিকা দুপুর ১২:৪৫ এ খুলবে।” Empty for an undated exam.
- Live sittings only. The later MCQ attempt is absent. Highest score first. Equal scores share a rank and the next rank skips. Columns: নাম, ক্রম, নম্বর, পূর্ণমান, ফল. No phone numbers.
- A written row appears when it is published, except a failed-to-submit zero, which is already on the board.

## Pages

Generate these as separate screens.

**1. MCQ offer, owned, window open.** Title সিআরপিসি, pill ১৯তম বিজেএস, type এমসিকিউ. Description, rules, “প্রশ্নপত্রের ভাষা: বাংলা”, ৪০ মিনিট, start and end, পাস ৪০%, পূর্ণমান ৫০. Primary “পরীক্ষা শুরু করুন”. No buy button. No negative mark.

**2. MCQ offer, owned, before the window.** The same page. The primary button is disabled and the reason is the start time.

**3. MCQ offer, owned, window over, he never started.** No “পরীক্ষা শুরু করুন”. Outline button for the single later attempt, with the leaderboard warning in the label. A second version of this page where he did sit the live exam: that button is absent, and a link “ফলাফল” is present.

**4. MCQ paper, in progress.** Sticky bar: সিআরপিসি, countdown ০০:৩২:১০, “সংরক্ষিত”. Question “১২ / ৫০” once the paper is open. One stem set as a formal paper: a number, the stem in the Bangla face, then A–D as full-width rows. B is selected. Footer: “আগের”, “পরের”, and primary “জমা দিন”. A thin progress track. No correct-answer styling.

**5. MCQ, just submitted, time still running.** The score card “৩৮ / ৫০ · উত্তীর্ণ” and the sentence that answers can still change. The same question below, still editable, still without the answer key.

**6. MCQ review, after the deadline.** Score stays. The stem shows B as “আপনার উত্তর” in orange and D as “সঠিক” in green, plus one line of explanation. Link “মেধাতালিকা”.

**7. Later MCQ attempt.** Same paper layout. A banner at the top: “এটি পরে দেওয়া চেষ্টা। মেধাতালিকায় উঠবে না।” The countdown is his own ৪০ minutes, labeled “আপনার সময়”.

**8. Written offer, owned, not yet open.** Title সিপিসি, type রিটেন. Description, rules, paper language, duration, and “তারিখ ঘোষণা হবে” or, in the dated variant, the start time. Card “প্রশ্নপত্র ও নির্দেশনা” with the two sentences: unlocks at exam time, and PDF only. “প্রশ্নপত্র দেখুন” disabled. The drop zone is visible and disabled. No retake. No camera.

**9. Written sitting, live.** Sticky countdown. The PDF viewport with the watermark. Under it, the dashed zone “খাতার পিডিএফ এখানে আপলোড করুন” and “শুধু পিডিএফ, সর্বোচ্চ ৮০ এমবি”. Primary “খাতা জমা দিন”. After upload, the file name `cpc-naeem.pdf` and “বদলে দিন” stay on screen while the countdown remains.

**10. Written, image rejected.** The same sitting with an inline error “শুধু পিডিএফ” and the drop zone ready again.

**11. Written, deadline passed, no file.** Stats: নম্বর “০”, ফল “জমা হয়নি”, মেধাক্রম “—”. No upload. No examiner. Link to the leaderboard.

**12. Written, deadline passed, file stored, not published.** Stats: প্রাপ্ত নম্বর “—”, মেধাক্রম “—”, খাতা মূল্যায়ন “অপেক্ষমাণ”. No comment and no marked-PDF button.

**13. Written, published.** Stats “৪২ / ৫০”, rank “৬”, “উত্তীর্ণ”. A comment with no name. Button “মূল্যায়িত খাতা দেখুন”. Link “মেধাতালিকা”.

**14. Leaderboard, closed.** Title and the opening time. No rows.

**15. Leaderboard, open.** Eight rows. Ranks 1 and 2 are different. Ranks 3 and 3 share a score, and the next is 5. One row is নাঈম হাসান. No phone column.

**16. Resume.** The MCQ offer when a sitting is already open. The primary button reads “পরীক্ষায় ফিরুন”, not “আবার শুরু করুন”.

**17. English chrome on the Bangla MCQ paper.** Site language English: countdown label, Save, Submit, and the score labels are English. The stem and options stay Bangla. This single screen is enough to show the split.

## Do not draw

A shop, a price, a buy button, a card form, a camera, a photo upload, a download icon, a negative-mark stat, a retake on the written exam, a second live attempt, the examiner’s name, or any staff page.

END OF PROMPT
