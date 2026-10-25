# Bangla copy guide

Every workstream writes Bangla copy by this guide. **Where a brief, the README, or the UI prompts in `docs/exam-site/ui/` quote Bangla that conflicts with this file, this file wins.** The quotes in the briefs show intent, not final wording.

Bangla is the main language of the site, not a translation of the English. Write the Bangla first, the way a coaching centre in Dhaka would say it to its students, then write the English separately in plain English. Never translate word by word in either direction.

## Principles

1. **The client's own words come first.** His mockup uses এক্সাম, এমসিকিউ, রিটেন, রুটিন, খাতা, রেজাল্ট, প্রশ্নপত্র ও নির্দেশনা, খাতা জমা দিন, ফলাফল ও ফিডব্যাক, প্রাপ্ত নম্বর, মেধাক্রম, খাতা মূল্যায়ন, অপেক্ষমাণ, শীঘ্রই আসছে, পরীক্ষা শুরু করুন, দেওয়া হয়েছে, বাকি. Use them as he does.
2. **Use the words Bangladeshi students already use on the web.** Common English loanwords written in Bangla are normal and clearer than invented formal words: লগইন, লগআউট, পাসওয়ার্ড, অ্যাকাউন্ট, পেমেন্ট, মডিউল, ড্যাশবোর্ড, নোটিশ, রিফান্ড. Where a plain Bangla word is just as common, prefer it (যাচাই, not ভেরিফাই; জমা দিন, not সাবমিট). Do not coin Sanskrit-heavy words (প্রবেশ for login, সক্রিয়করণ for activation, পঠিত for read).
3. **Coaching-centre vocabulary beats software vocabulary.** ভর্তি, শিক্ষক, খাতা, মেধাতালিকা, উত্তীর্ণ are what students and staff say. "Roster", "instructor", "access", "draft" are software words; use the glossary instead.
4. **Address the student as আপনি**, politely and directly. Short sentences. Verbs at the end. End full sentences with দাঁড়ি (।). Buttons and labels have no দাঁড়ি.
5. **Buttons say what happens**, as a short verb phrase: "জমা দিন", "পেমেন্ট করুন", "লগইন করুন". Not a noun ("জমা") and not a vague "ঠিক আছে" when a specific verb fits.
6. **Errors say what happened and what to do**, in that order, without blame: "ফোন নম্বরটি সঠিক নয়। ০১ দিয়ে শুরু করে ১১ সংখ্যার নম্বর লিখুন।"
7. **Keep buttons under about 20 characters** so they fit on a 320 px phone. Long explanations go in the helper text under the control.
8. **One term for one thing everywhere.** Use the glossary. If you need a term that is not there, add it to your Report so the integrator adds it here.

## Numbers, money, dates

- Bengali digits in the Bangla UI everywhere: dates, times, prices, marks, counts, page numbers. Latin digits in English. Phone numbers, transaction ids and student codes stay as typed (Latin), because people copy them.
- Money: `৳২,৫০০` for a whole amount and `৳২,৫০০.৫০` only when there are paisa. No space after ৳. Comma grouping in the Bangladeshi style is optional; keep it to thousands (২,৫০০; ১২,০০০).
- Counts use টি: "৫টি পরীক্ষা", "১২টি প্রশ্ন". Progress: "৫টির মধ্যে ৩টি পরীক্ষা দেওয়া হয়েছে".
- Dates: "১২ নভেম্বর ২০২৬" (no year when it is the current year: "১২ নভেম্বর"). Weekday when it helps: "বৃহস্পতিবার, ১২ নভেম্বর".
- Times with the day period, never AM/PM: ভোর (4–6), সকাল (6–12), দুপুর (12–3), বিকেল (3–6), সন্ধ্যা (6–8), রাত (8–4). "দুপুর ১২:০০", "রাত ৯:৩০". A range: "দুপুর ১২:০০ – ১২:৪০".
- Relative: "আজ", "আগামীকাল", "গতকাল". Countdown: "শুরু হতে বাকি ২ ঘণ্টা ১৪ মিনিট", "সময় বাকি ০০:৩২:১০".
- Duration: "৪০ মিনিট", "১ ঘণ্টা ৩০ মিনিট".

## Glossary

| Concept | Use | Do not use |
|---|---|---|
| Site name | এক্সাম পোর্টাল | |
| Log in / log out | লগইন করুন / লগআউট | প্রবেশ করুন, বেরিয়ে যান |
| Sign up | অ্যাকাউন্ট খুলুন | নিবন্ধন করুন |
| Activate an invited account | অ্যাকাউন্ট চালু করুন; link: "কোড দিয়ে অ্যাকাউন্ট চালু করুন" | সক্রিয় করুন, সক্রিয়করণ |
| Forgot password | পাসওয়ার্ড ভুলে গেছেন? | |
| Verify email | ইমেইল যাচাই করুন; status "যাচাই করা হয়েছে" / "যাচাই করা হয়নি" | ভেরিফাই |
| Google sign-in | গুগল দিয়ে লগইন | গুগল দিয়ে চালু করুন |
| Account page | আমার অ্যাকাউন্ট | |
| Student / teacher (instructor) | শিক্ষার্থী / শিক্ষক | ইনস্ট্রাক্টর |
| Master Admin / System Admin | মাস্টার অ্যাডমিন / সিস্টেম অ্যাডমিন | প্রধান প্রশাসক |
| Staff area (as a whole) | অ্যাডমিন প্যানেল | কর্মী প্যানেল |
| Exam (general) | পরীক্ষা (headings may use এক্সাম where the client does, as in the home doors) | |
| MCQ / written exam | এমসিকিউ / রিটেন (the client's words) | বহুনির্বাচনী, লিখিত (only in long sentences if clearer) |
| Category / module | ক্যাটাগরি / মডিউল | শ্রেণি, প্যাকেজ |
| Exam schedule | রুটিন; full page title "পরীক্ষার রুটিন" | সময়সূচি |
| Date not set | তারিখ পরে জানানো হবে | তারিখ ঘোষণা হবে |
| Start the exam | পরীক্ষা শুরু করুন | |
| Resume | পরীক্ষা চালিয়ে যান | পরীক্ষায় ফিরুন |
| Later MCQ attempt | অনুশীলন হিসেবে দিন; note "মেধাতালিকায় আসবে না" | পরে একবার দিন, রিটেক |
| Question paper | প্রশ্নপত্র | |
| Answer script | খাতা (the client's word); "খাতার পিডিএফ" | উত্তরপত্র (use only in long help text) |
| Submit | জমা দিন; done: "জমা দেওয়া হয়েছে" | সাবমিট |
| Not submitted (written, deadline passed) | খাতা জমা হয়নি | |
| Save (sitting autosave) | "সেভ হচ্ছে…", "সেভ হয়েছে", "সেভ হয়নি — আবার চেষ্টা করুন" | সংরক্ষিত, সংরক্ষণ হচ্ছে |
| Selected option / your answer / correct answer | বেছে নেওয়া হয়েছে / আপনার উত্তর / সঠিক উত্তর | নির্বাচিত |
| Explanation | ব্যাখ্যা | |
| Flag for review | পরে দেখব | |
| Time left | সময় বাকি | |
| Result | রেজাল্ট in chips and the tab bar (the client's word); ফলাফল in headings and sentences | |
| Marks obtained / full marks / pass mark | প্রাপ্ত নম্বর / পূর্ণমান / পাস নম্বর | |
| Passed / failed | উত্তীর্ণ / অনুত্তীর্ণ | পাস / ফেল (too blunt in a result pill) |
| Rank / leaderboard | মেধাক্রম / মেধাতালিকা | লিডারবোর্ড |
| Being marked | মূল্যায়ন চলছে | |
| Marked script | মূল্যায়ন করা খাতা | মূল্যায়িত খাতা |
| Teacher's comment | শিক্ষকের মন্তব্য (never shows the teacher's name) | ফিডব্যাক (the client's "ফলাফল ও ফিডব্যাক" heading is fine) |
| Buy exam / buy module | পরীক্ষাটি কিনুন / পুরো মডিউল কিনুন | |
| Go to payment | পেমেন্ট করুন | পেমেন্টে যান |
| Price not set | মূল্য এখনো ঠিক হয়নি | দাম এখনো নির্ধারণ হয়নি |
| Student has access (bought or added) | চালু আছে | অ্যাক্সেস আছে |
| Access through the staff list (roster) | সরাসরি ভর্তি; action "সরাসরি ভর্তি করুন"; list "সরাসরি ভর্তি তালিকা" | রোস্টার |
| Reasons on a student's access list | সরাসরি ভর্তি / মডিউল কেনা / পরীক্ষা কেনা | |
| Payment statuses | অপেক্ষমাণ / সফল / ব্যর্থ / বাতিল | পরিশোধিত |
| Payment pending explanation | পেমেন্ট নিশ্চিত হলে পরীক্ষাটি চালু হবে | নিশ্চিত হলে অ্যাক্সেস খুলবে |
| Module purchase covers later exams | পরে যোগ হওয়া পরীক্ষাও পাবেন | পরের পরীক্ষাও খুলবে |
| No money taken | আপনার কোনো টাকা কাটা হয়নি | |
| Gateway down | পেমেন্ট এখন করা যাচ্ছে না। কিছুক্ষণ পরে আবার চেষ্টা করুন। | পেমেন্ট গেটওয়ে এখন পাওয়া যাচ্ছে না |
| Transaction id | লেনদেন নম্বর (the value stays Latin) | ট্রানজেকশন আইডি |
| Payment method | পেমেন্ট মাধ্যম: বিকাশ, নগদ, রকেট, কার্ড | |
| Notices | নোটিশ; "সবগুলো পড়া হয়েছে" | সব পঠিত, বিজ্ঞপ্তি |
| Draft / final / published / archived (staff) | খসড়া / চূড়ান্ত / প্রকাশিত / আর্কাইভ করা | ড্রাফট, ফাইনাল |
| Final save (staff) | চূড়ান্ত করুন | ফাইনাল সেভ |
| Publish (catalog) / publish a result | প্রকাশ করুন / ফলাফল প্রকাশ করুন | |
| Save draft mark | খসড়া নম্বর সেভ করুন | ড্রাফট সেভ |
| Awaiting marking / awaiting publish | মূল্যায়ন বাকি / প্রকাশ বাকি | নম্বরের অপেক্ষায়, প্রকাশের অপেক্ষায় |
| Participation | অংশগ্রহণ | |
| Audit log | কার্যক্রম লগ | অডিট লগ |
| Quick actions | শর্টকাট | দ্রুত কাজ |
| Coming soon | শীঘ্রই আসছে | |
| Retry | আবার চেষ্টা করুন | আবার চেষ্টা |
| Nothing found | কিছু পাওয়া যায়নি | |
| Staff-only / student-only page | এই পাতাটি শুধু অ্যাডমিন ও শিক্ষকদের জন্য / এই পাতাটি শুধু শিক্ষার্থীদের জন্য | এই অংশ কর্মীদের জন্য |
| Help | সাহায্য ও প্রশ্নোত্তর | |
| Policy pages | শর্তাবলি / গোপনীয়তা নীতি / রিফান্ড নীতি / আমাদের সম্পর্কে / যোগাযোগ | ফেরত নীতি |
| Menu / open / close | মেনু / খুলুন / বন্ধ করুন | |
| Switch states | চালু / বন্ধ | |
| Go to the admin panel | অ্যাডমিন প্যানেলে যান | |
| Log in on the other site (split hosts) | অ্যাডমিন প্যানেলে লগইন করুন / শিক্ষার্থীদের সাইটে লগইন করুন | |
| Time over / minutes left warning | সময় শেষ / আর {n} মিনিট বাকি | |
| Choose or replace a file | পিডিএফ ফাইল বেছে নিন / ফাইল বদলান | |
| Secure payment | নিরাপদ পেমেন্ট | |
| Refund policy (link text) | রিফান্ড নীতি | |
| Placeholder banner on policy pages | নমুনা লেখা — চূড়ান্ত লেখা শীঘ্রই যোগ হবে | নমুনা লেখা — ক্লায়েন্ট বদলাবেন |

## Phrases from the briefs, rewritten

| In the briefs or prompts | Write instead |
|---|---|
| প্রবেশ করুন | লগইন করুন |
| প্রবেশ হয়েছে | লগইন সফল হয়েছে |
| ইমেইল, ফোন, বা পাসওয়ার্ড মিলছে না। | ইমেইল/ফোন অথবা পাসওয়ার্ড সঠিক নয়। |
| একটু পরে আবার চেষ্টা করুন। | অনেকবার ভুল চেষ্টা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন। |
| এই ইমেইলে অ্যাকাউন্ট থাকলে একটি বার্তা পাঠানো হয়েছে। | এই ইমেইলে কোনো অ্যাকাউন্ট থাকলে সেখানে একটি কোড পাঠানো হয়েছে। |
| কোডটি আর ব্যবহার করা যাবে না। | কোডটি আর কাজ করবে না। নতুন কোড নিন। |
| এখন নতুন পাসওয়ার্ড দিয়ে প্রবেশ করুন। | পাসওয়ার্ড বদলানো হয়েছে। এখন নতুন পাসওয়ার্ড দিয়ে লগইন করুন। |
| এই নম্বরটি একবারই লাগবে। পরে বদলানো যাবে না। | পরীক্ষা কিনতে ও দিতে একটি ফোন নম্বর লাগবে। (The phone can be changed now; drop the "cannot change" line.) |
| প্রশ্ন কেনার আগে দেখা যাবে না। | প্রশ্ন দেখা যাবে পরীক্ষা শুরু হলে। |
| শুধু এই পরীক্ষাটি খুলবে। | শুধু এই পরীক্ষাটি চালু হবে। |
| এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে। | এই মডিউলের সব পরীক্ষা চালু হবে, পরে যোগ হওয়া পরীক্ষাসহ। এমসিকিউ ও রিটেন দুটোই। |
| ১২ নভেম্বর, দুপুর ১২:০০ এর আগে শুরু হবে না। | পরীক্ষা শুরু হবে ১২ নভেম্বর, দুপুর ১২:০০-এ। |
| সময় শেষ হওয়া পর্যন্ত উত্তর বদলানো যাবে। | সময় শেষ হওয়ার আগ পর্যন্ত উত্তর বদলাতে পারবেন। |
| পরে একবার দেওয়া যাবে · মেধাতালিকায় উঠবে না | অনুশীলন হিসেবে একবার দিতে পারবেন · মেধাতালিকায় আসবে না |
| এটি পরে দেওয়া চেষ্টা। মেধাতালিকায় উঠবে না। | এটি অনুশীলন। এর নম্বর মেধাতালিকায় আসবে না। |
| প্রশ্নপত্র পরীক্ষার সময় আনলক হবে | প্রশ্নপত্র দেখা যাবে পরীক্ষা শুরু হলে |
| উত্তর একটি পিডিএফ। ছবি গ্রহণযোগ্য নয়। | খাতা একটি পিডিএফ ফাইল হিসেবে জমা দিন। ছবি জমা দেওয়া যাবে না। |
| শুধু পিডিএফ। | শুধু পিডিএফ ফাইল জমা দেওয়া যাবে। |
| মেধাতালিকা দুপুর ১২:৪৫ এ খুলবে। | মেধাতালিকা প্রকাশ হবে দুপুর ১২:৪৫-এ। |
| পেমেন্ট যাচাই হচ্ছে | পেমেন্ট নিশ্চিত করা হচ্ছে… |
| টাকা কাটা হয়নি | পেমেন্ট সম্পন্ন হয়নি। আপনার কোনো টাকা কাটা হয়নি। |
| ১৯তম বিজেএস খুলেছে | ১৯তম বিজেএস এখন আপনার জন্য চালু |
| এখন কোনো নোটিশ নেই। | এখনো কোনো নোটিশ নেই। |
| এই মডিউলে কেউ নেই। | এই মডিউলে এখনো কাউকে সরাসরি ভর্তি করা হয়নি। |
| এখনো কোনো মডিউল দেওয়া হয়নি। | আপনাকে এখনো কোনো মডিউল দেওয়া হয়নি। |
| জমা হয়নি · নম্বর দেওয়া যাবে না | খাতা জমা হয়নি, তাই নম্বর দেওয়া যাবে না। |
| প্রকাশিত থাকলে সেভ করলে আবার ড্রাফট হবে। | প্রকাশিত অবস্থায় সেভ করলে এটি আবার খসড়া হয়ে যাবে। |
| আবার সেভ করলে প্রকাশ বাতিল হবে। | আবার সেভ করলে প্রকাশিত ফলাফল লুকিয়ে যাবে, আবার প্রকাশ করতে হবে। |
| পেমেন্ট ছাড়া দিতে রোস্টার খুলুন | পেমেন্ট ছাড়া চালু করতে সরাসরি ভর্তি করুন |
| এই মডিউলে ইতিমধ্যে একজন আছেন। | এই মডিউলে আগেই একজন শিক্ষক দেওয়া আছে। আগে তাকে সরান। |

## English copy

English is written for an English reader, not translated from the Bangla: "Log in", "Create account", "Start exam", "Continue exam", "Submit", "Results", "Merit list" (the term Bangladeshi students use; not "Leaderboard"), "Being marked", "Practice attempt (not on the merit list)", "Date to be announced", "Price not set yet", "Pay now", "Unlocked". Sentence case for buttons and headings.

## Review

- Each workstream reads its own Bangla aloud once before reporting done: if a sentence sounds like a translation, rewrite it.
- The integrator does one Bangla pass over the merged site before the client sees it, using this file.
- When the client corrects a word, change it here and in the message files; nothing else needs to change, because all copy lives in the message catalogs.
