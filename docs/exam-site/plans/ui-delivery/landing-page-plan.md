# Landing page plan: port the old SYMAM LAW homepage, improved

Status: built on `catalog-acquisition` (commits `a4ced39` to `10274c1`), then the hero doors below. Written for Shahriar and the agent that builds the page.
**Decision, 26 Oct 2026:** the old site governs this landing page only; the client mockup governs every other student page. The mockup's two doors are the hero's first action. See `../../decisions.md`, "Landing page and the client mockup".
Checked against: `catalog-acquisition` at `18727c6`. The old homepage was read on 26 Oct 2026 from `client-vps` in `/root/symamlaw/symamlaw-frontend`, git `355ad45`. Access was read only: the source was copied out without `.env*`, `node_modules`, `.next` or `.git`, and the old server was not changed. Its live content came from the public endpoint `https://backoffice.symamlaw.com/api/settings`. Nothing was run in a browser.

Ask: the client wants the exam site's landing page to look like the old site, with design improvements.

## 1. What the old homepage is

The old site runs Next.js 14 with Tailwind and daisyUI, in a Bangla-first layout with the Rubik and Kalpurush fonts. This is the page from top to bottom, with the live data from 26 Oct.

| # | Old section | What it shows | Verdict |
|---|---|---|---|
| 1 | Top bar (`TopNav`) | Navy strip. Hotline, a second hotline where the email should be (a bug), a scrolling marquee reading "অ্যাপ/ওয়েবসাইট নির্মাণাধীন…", and Facebook, WhatsApp, LinkedIn and YouTube icons. | **Keep the idea** (contact and socials up front). Drop the marquee. |
| 2 | Navbar | Logo, a search box, ভর্তি ফরম, ফিচার্স, প্যাকেজ, নোটিশ, প্রকাশনা, ব্লগ, মিডিয়া and a রিসোর্স mega-menu, a লগিন \| সাইন-আপ pill, a cart, and a settings cog that only toggles dark mode. | **Rebuild lean.** Most of those pages do not exist on the exam site. |
| 3 | Banner | **One image** on a purple-to-fuchsia background, with slider arrows and dots although there is only one slide. The text is baked into the image: logo, "SYMAM LAW", OFFLINE / ONLINE, six batches (BJS Foundation, BJS MCQ Exam, BJS Written Exam, Bar Council Special, Bar MCQ Exam, Bar Written Exam), CALL NOW 01601-561225 / 01968-322239, and a classroom photo in a navy chevron frame. | **Keep the content and the look. Rebuild it as real HTML.** This is the client's identity. |
| 4 | Prep CTA | A single button, "ভর্তি হতে রেজিস্ট্রেশন করুন" (or "অফলাইন কোর্সে ভর্তি হন" when signed in). The cards around it are commented out. | **Keep as the primary CTA** in the hero. |
| 5 | Stats | 764+ শিক্ষার্থী and 2+ অভিজ্ঞ মেন্টর from the API, then 200+ অ্যাপ ডাউনলোড and ১০০০+ লার্নিং ম্যাটেরিয়াল, **hard-coded**. | **Keep the band. Show only numbers the client confirms** (Q3). |
| 6 | কোর্স ক্যাটাগরি | Image cards for the LMS course categories: "19th BJS MCQ Exam" and "10th Bar Council Preparation". | **Merge** with section 7, fed from the exam catalog. |
| 7 | এক্সাম ক্যাটাগরি | The first 4 exam categories, each a glass card with a generic exam icon and an এক্সপ্লোর করুন button. Live: "19th BJS MCQ Exam" and "19th BJS Special Model Test (MCQ)". The question count never shows because the API returns `examCount: null`. | **Keep, fix:** real counts and prices from the new API. |
| 8 | Our Packages | Three cards of fixed height (40 rem) with a poster image, name, duration, price and struck-through price, a clipped HTML description, "Read More…" and "Subscribe Now". All three carry a "Popular" ribbon. Live: 20th BJS Foundation Batch, 15000৳ (was 20000৳); 19th BJS MCQ Exam Batch (Special), 780৳; 19th BJS Special Model Test (MCQ), 399৳. | **Keep the card idea, fix the card** (section 3, item 6). |
| 9 | Campus / Seminar tabs | **Placeholder text on the live site:** "Campus Program 1", "Seminar Event 1", with English lorem copy. | **Drop.** |
| 10 | Top Reviews | An autoplay carousel of five **hard-coded** testimonials with names, all using the same generic avatar. | **Drop unless the client sends real ones** (Q4). |
| 11 | Footer | Navy. Logo; contact (+8801601561225, info@symamlaw.com, 10:00 AM – 6:00 PM, Binodpur Bazar, Motihar, Rajshahi); Terms, Refund, Privacy and About; app buttons; a 9561 px-wide SSLCommerz image; socials; copyright; "Developed By Fleeting Trails". | **Keep the structure, fix the details.** The App Store link goes to Apple's own "Apple Store" app (id375380948) and the Play link is an Expo build URL, so both are dropped. |

The brand assets are the logo (navy scales over an open book, "SYMAM LAW"), navy `#0A2647` with lighter blues, a teal "LAW" in the banner, and yellow-gold, red and royal blue in the batch posters. The tone is Bangla first, with phone numbers everywhere and online and offline batches.

## 2. What is wrong with the old design

These are the problems the port has to fix, not copy.

- **The hero is a picture of text.** The headline is not readable on phones, cannot be translated or searched, and fails screen readers. The image is `loading="lazy"`, which slows the largest paint. The page has no real `<h1>`.
- **There is no palette.** The page goes from a purple hero to a grey band, a green-to-red page wash, a red band and a navy footer. Every section uses a different gradient.
- **Placeholder and invented content is live:** the under-construction marquee, the Campus/Seminar lorem, the hard-coded reviews and stats, and the broken app links.
- **Headings switch language** ("Our Packages", "Top Reviews" and "Subscribe Now" on a Bangla page), and several headings are `<h1>`.
- **Package cards fight their content.** They have fixed heights, raw HTML clipped at 165 px, and a ribbon on every card, which then means nothing.
- **Motion has no purpose:** a marquee, an autoplay carousel, and hover scale and rotate on every card.
- **The page is heavy:** daisyUI, slick, framer-motion, Redux and GTM, and a 9561 px payment banner.

## 3. The new landing page, top to bottom

This page is what an anonymous visitor sees at `/`. Signed-in students keep today's dashboard (`SignedInHome`). It uses the existing site palette: cream `#f6f4ee`, navy `#14213d` and gold `#c8962e`. That fits the old brand well: the logo is navy and the posters use gold. The old purple and red are not brought over. Red stays for warnings.

1. **Header (wide variant).** Real logo, then এমসিকিউ, রিটেন, সাহায্য, যোগাযোগ, the language and theme pills, প্রবেশ, and a gold "অ্যাকাউন্ট খুলুন" button. On phones: logo, pills and a menu sheet. It is sticky and shrinks after scrolling, like the old navbar but without the layout jump.
2. **Optional notice strip.** One static line from config (for example "১৯শ বিজেএস মডেল টেস্ট চলছে"), with a link and a close button. It does not scroll and is hidden when empty. It replaces the marquee.
3. **Hero.** Two columns on desktop and stacked on phones.
   - Left: a small "SYMAM LAW · সাইমাম ল'" label; the `<h1>` (draft: "বিজেএস ও বার কাউন্সিল পরীক্ষার পূর্ণ প্রস্তুতি", Q1); one supporting line; **অনলাইন** and **অফলাইন** as two solid chips, echoing the red OFFLINE/ONLINE boxes in navy and gold; the primary button "অ্যাকাউন্ট খুলুন" and the secondary "এক্সাম দেখুন" (to `/mcq`).
   - Right: the classroom photo in the **navy chevron frame** from the banner, done in CSS `clip-path`, with a gold edge. A floating card on top reads "কল করুন · 01601-561225 · 01968-322239" with `tel:` links. That keeps the banner's CALL NOW panel.
   - The photo loads with `next/image` `priority` at proper `sizes`. That image is the page's largest paint.
4. **Batches.** The six batches from the banner as a 2×3 grid of check items, under two headings, **বিজেএস** and **বার কাউন্সিল**. MCQ and written items link to `/mcq` and `/written`. "Foundation" and "Special" follow Q2.
5. **Numbers band.** A navy band with three or four large Bangla-digit figures. Only confirmed figures (Q3). The default is figures the system can count itself: number of exam modules, upcoming exams, MCQ and written exams.
6. **Two doors, in the hero.** The mockup's MCQ and written cards (✅ এমসিকিউ · দ্রুত অনুশীলন, ✍️ রিটেন · লিখিত পরীক্ষা) sit in the hero as its first action, above "অ্যাকাউন্ট খুলুন". They replace the "এক্সাম দেখুন" button. They stay the product entrance (decision of 26 Oct).
7. **Exam categories and modules.** These replace both old category sections. The top-level categories come from the public catalog. Each card shows the title, the number of modules, MCQ and written counts, and "শুরু ৩৯৯৳ থেকে". Cards link into the door page. At most six, then "সব দেখুন".
8. **Upcoming exams.** New, and the strongest proof that the site is live. The next 3 to 5 exams, each showing date and time in Bangla, MCQ or written, module and price, linking to the exam page. Hidden when there are none.
9. **Featured packages.** This replaces "Our Packages". A card has a fixed 4:3 image area, title, a short plain-text description (two to three lines, no raw HTML), "মেয়াদ", price with a struck-through old price when discounted, and a "বিস্তারিত" button. A badge appears only on the one card the client picks. Exam modules come from the catalog. Offline batches such as Foundation (15000৳) are not exam-site products and follow Q2.
10. **How it works.** Three steps with icons: অ্যাকাউন্ট খুলুন → bKash, Nagad or card payment → join the live exam, then see results and the leaderboard. Under it, a payment-logo row (SSLCommerz, bKash, Nagad) sized correctly. That replaces the 9561 px banner and earns trust at the point of payment.
11. **Testimonials** (only if Q4 gives real ones). A static grid of three, with name, batch and year, and a photo or initials. No carousel, no autoplay.
12. **Visit or call.** Address card (বিনোদপুর বাজার, কৃষি ব্যাংকের পশ্চিম পার্শ্বে, ওয়ালটন শো-রুমের অপজিটে, মতিহার, রাজশাহী), phones, email, hours, WhatsApp and a "Google Maps-এ দেখুন" link. A link, not an embedded map, so the page stays fast.
13. **Closing CTA.** A navy band: "আজই প্রস্তুতি শুরু করুন" and "অ্যাকাউন্ট খুলুন".
14. **Footer (wide variant).** Navy, three columns: logo and one line; contact; links (Help, About, Contact, Terms, Privacy, Refund). Under it, social icons, payment logos and the copyright. The vendor credit follows Q6. No app buttons until real store links exist.

The whole page follows these rules:
- **One section rhythm.** Sections alternate cream and white, with one navy band (numbers) and the navy footer. Section titles are Bangla `<h2>` with an optional short subtitle. One container (`max-w-6xl`, 16 px gutters on phones).
- **Motion.** A subtle fade-up when a section enters the screen, card lift on hover, and nothing that moves by itself. Everything is off under `prefers-reduced-motion`.
- **Dark mode** comes from the existing tokens. The logo switches to `logo-dark`.
- **English.** The same page with `landing.*` keys in English. Prices and dates use `formatNumber` and `formatDate`, so Bangla shows Bangla digits.

## 4. How to build it

**Layout.** `StudentShell` locks every page to a 760 px column. Add a wide mode, used only when `pathname === "/"` and the visitor is not a signed-in student. In wide mode the header and footer containers become `max-w-6xl` and `<main>` drops its padding and width, so sections can run full-bleed. Every other page stays as it is.

**Files.**
- `web/src/components/student/landing/`: `landing-page.tsx` plus one file per section (`hero.tsx`, `batches.tsx`, `numbers.tsx`, `catalog-preview.tsx`, `upcoming.tsx`, `packages.tsx`, `how-it-works.tsx`, `contact.tsx`, `cta.tsx`).
- `web/src/content/site-info.ts`: phones, email, hours, address (bn/en), socials, batches, notice strip, featured package ids and testimonials. It is typed and edited in code for now. The new API has no site-settings endpoint, and building one before 29 Oct is not worth it. A Master Admin "site info" screen can come later.
- `web/src/i18n/messages/{bn,en}/landing.ts`, registered in `bn.ts` and `en.ts`.
- `web/public/brand/`: `logo.png`, `logo-dark.png`, `og.png` and `classroom.jpg`, copied from the backoffice CMS URLs listed in section 1, and the payment logos.
- `components/shell/logo.tsx` swaps the ⚖️ emoji for the logo image. The comment there already marks this as the place to do it.
- `components/student/home/home-page.tsx`: `SignedOutHome` renders `<LandingPage />`. Staff who are signed in see the landing page plus today's staff note.

**Data.** Use only public endpoints that already exist. No API change.
- `GET /api/v1/public/live-exam-categories` for the category cards and counts.
- `GET /api/v1/public/live-exam-modules?size=…` for featured packages (price, MCQ and written counts).
- `GET /api/v1/public/live-exams?upcoming=true&size=5` for upcoming exams.
- The hooks in `lib/api/exam/catalog.ts` already wrap the first and third. Add a small one for featured modules.
- **Gap:** modules have no image field. Cards use a generated cover (gold and navy pattern with the title) unless Q5 says to add images.

**Rendering and speed.** The README target is a largest paint under 2.5 s on a mid-range phone over 4G.
- `app/(site)/page.tsx` stays a server component. It exports `metadata` (title, description, Open Graph image from `og.png`) and renders the landing shell straight away. Today the visitor waits for the session refresh and sees a skeleton first.
- For signed-in users, avoid a flash of the landing page with a non-secret hint cookie (`exam-portal.signed-in=1`, not httpOnly, holds no token). The session code sets it on login and refresh and clears it on logout and session loss. With the hint, the page shows the dashboard skeleton. Without it, the page renders the landing page.
- Catalog sections fetch on the client with skeletons. Server prefetch for search engines stays a later step, as the README already says.
- No new dependencies: no slick, no framer-motion. Motion is CSS plus one `IntersectionObserver` hook.

**Tests.** Vitest under `web/tests/landing/`:
- An anonymous visitor sees the landing page, with the `<h1>` and the hero CTAs.
- A signed-in student sees the dashboard, not the landing page.
- Empty catalog and empty upcoming list hide those sections, with no empty boxes.
- Every landing key exists in both `bn` and `en`.
- Bangla digits appear in prices.

Then a manual pass at 360 px, 768 px and 1280 px, in light and dark.

**Not part of this work:** old LMS pages (blogs, library, media, resources, admission form), search, cart, and the mobile app. The old VPS is not touched.

## 5. Questions for the client

| # | Question | Default if no answer by 27 Oct |
|---|---|---|
| Q1 | Hero headline and supporting line. | "বিজেএস ও বার কাউন্সিল পরীক্ষার পূর্ণ প্রস্তুতি" / "অনলাইন লাইভ এক্সাম, ফলাফল আর মেধাতালিকা — এক জায়গায়।" |
| Q2 | Offline batches (BJS Foundation 15000৳, Bar Council Special) are not sold on the exam site. Show them as "ভর্তি চলছে, কল করুন" cards, link to the old site, or leave them out? | Show as cards with a call button, no checkout. |
| Q3 | Which numbers can be shown publicly? (The old site shows 764+ students and 2 mentors; 200+ downloads and 1000+ materials were hard-coded.) | Only counts from the exam catalog. |
| Q4 | Real student testimonials, with written consent: name, batch, year, optional photo. | Leave the section out. |
| Q5 | Original, high-resolution classroom photo, plus poster images for featured modules. The banner photo is only about 490 px wide once cropped. | Use the crop for now, and use generated covers on module cards. |
| Q6 | Keep the "Developed By Fleeting Trails" credit? Keep Google Tag Manager (`GTM-T2HTRFMZ`) on the new site? | No credit, no GTM. |
| Q7 | Is the official Facebook page `facebook.com/symamlaw` (printed on the poster) or the `profile.php?id=100065554386812` URL in the old settings? | Use the poster URL. |

## 6. Order of work and size

1. The shell's wide mode, the logo swap, `site-info.ts` and the `landing` messages.
2. Hero, batches, contact, closing CTA and footer. These are static and can ship even if the catalog is empty.
3. Numbers, the two doors, catalog preview, upcoming exams and packages. These use live data.
4. How it works with payment logos, the testimonials slot, metadata and Open Graph.
5. Tests, the manual responsive and dark pass, and a Lighthouse run on staging (`https://srv2089893.hstgr.cloud`).

The size is about one working day for the build and half a day for review and fixes. It fits before the 29 Oct cutover if Q1 to Q3 are answered by 27 Oct. The rest have safe defaults.
