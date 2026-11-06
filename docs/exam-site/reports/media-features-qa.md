# Device QA: media features and review pictures

Branch `integ/media-features`, commit `d78525e`. API on `http://localhost:8092`, website on `http://localhost:3000`, against the
copy `coaching_center_qa_media` (Flyway 78). The sample database is not touched.

Mark each line pass or fail. For a fail, write what you saw and the device, browser and width.

## Before you start

- [ ] Open `http://localhost:3000`. The home page loads.
- [ ] Have a staff account (the Master Admin from the team's notes), and a student account from the sample seed.
- [ ] Have three test files ready: a phone photo (JPEG, portrait, over 2 MB), a PNG under 1 MB, and a PDF under 5 MB. Also a
      text file renamed to `.pdf`, and a PDF over 20 MB (or any file over 20 MB renamed to `.pdf`).

## 1. Notices: photos and PDF (F1)

Public (`/notice-board`, linked as "Notices" in the landing menu)

- [ ] A notice with no files looks exactly as before: no empty space and no placeholder image.
- [ ] A notice with photos shows the first photo as a wide thumbnail on the list.
- [ ] A notice with a PDF shows a "PDF" tag on the list.
- [ ] Open a notice with photos. The photos sit under the text, and the PDF card under the photos.
- [ ] One photo shows full width. Two to six photos show in a grid.
- [ ] Tap a photo. It opens full size, with previous and next buttons, and Esc (or the close button) closes it.
- [ ] The PDF card: "View" opens the PDF in a dialog, and "Download" saves the file with its own name.
- [ ] Bangla file names download intact.

Staff (`/staff/notice-board`, sign in as the Master Admin)

- [ ] Create a draft. Before saving, the attachments area says to save the draft first.
- [ ] Save the draft. Add a photo. It appears with its order number.
- [ ] Add six photos. The "Add photo" button is hidden at six and shows "6 of 6".
- [ ] Try a seventh photo: it is refused with a clear message.
- [ ] Move a photo up and down with the buttons (keyboard works too). Reload: the order is kept.
- [ ] Remove a photo: a confirmation appears first, then the photo goes and the order closes up.
- [ ] Upload a PDF. Its name and size show. Replace it with another PDF. Remove it.
- [ ] Try a text file renamed `.pdf`: refused. Try a PDF over 20 MB: refused.
- [ ] Publish. Open the public page in a signed-out window: the photos and the PDF show.
- [ ] Unpublish. The notice disappears from the public list.

## 2. Student profile picture (F2)

- [ ] Sign in as a student. `/account` shows an avatar with initials and an "Upload" button.
- [ ] Upload the phone photo (JPEG over 2 MB). The preview appears, then Save. The picture shows on the account page.
- [ ] The header avatar (top right) now shows the picture.
- [ ] Change the picture (PNG). It replaces the old one at once, on the account page and in the header.
- [ ] Remove the picture. Confirmation first, then initials return in both places.
- [ ] Upload a text file or a file over 8 MB: refused with a message.
- [ ] Sign out and sign in as a different student: their own picture or initials show, never the first student's.
- [ ] Staff accounts: the header keeps the usual person icon. No student picture is shown on staff screens except in review
      rows (section 4).

## 3. Package routine PDF (F3)

Public (`/packages`, open a package, the "Routine" tab)

- [ ] A package with no routine PDF: the Routine tab is exactly as before (the table or the "no exams" line).
- [ ] A package with a routine PDF: the PDF card appears at the top of the tab, titled "Routine (PDF)".
- [ ] The card's "View" opens the PDF. "Download" saves it.
- [ ] A package with a routine PDF and no exams: only the card shows, not the "no exams" line.
- [ ] Phone (360 px wide): the card fits, with no sideways scrolling.

Staff (package details screen, sign in as an account with the cover permission)

- [ ] Upload a routine PDF. Its name and size show, and the Routine tab shows it at once.
- [ ] Replace it, then remove it (with confirmation).
- [ ] An account without the cover permission sees the file read-only, with no upload or remove buttons.

## 4. Reviewer pictures (review photo change)

Setup: a student with a profile picture has a review approved on an exam (see section 2). Mark it featured as staff.

- [ ] The exam's reviews list (`/exam/[id]/reviews`) and the reviews section on the package and exam pages: the reviewer's
      picture shows beside the name, and initials show for a reviewer with no picture.
- [ ] The landing page "Reviews" section (the featured reviews): the picture shows on each card that has one. Cards without
      a picture show initials, two letters for a full name (for example "FA" for "Farhana A.").
- [ ] A pending or hidden review does not show its picture to visitors (a signed-out window).
- [ ] Staff reviews page (`/staff/reviews`): each row shows the picture, or initials, before the student's name.
- [ ] The picture image loads from the picture route only. The student's id does not appear in the picture address.

## 5. Admin: student's sign-up date (`/staff/students/[id]`)

- [ ] Open a student as the Master Admin. A "Signed up" line shows the date, with the Bangla label in Bangla mode.
- [ ] Open the same student as the student themselves (own account): the date is shown there too.

## 6. Package discount tags (earlier change)

- [ ] `/packages`: a discounted package shows the struck-through list price, the green "% off" tag, and the end date.
- [ ] Landing page "Featured packages": the discounted package shows the same tag and the charged price, not the list price.
- [ ] The package detail page shows the same tag as before.
- [ ] A package with no discount shows its price only.

## 7. Dark mode (earlier fix)

- [ ] The theme button (top right) switches to dark on the student site. The shell, cards and text all change, in both
      directions.
- [ ] Reload: the chosen theme is kept.
- [ ] Repeat on a computer whose system setting is light, and on one whose system setting is dark.
- [ ] Notice photos, the PDF card and the avatar look right in dark mode (no white box that clashes).

## 8. Downloads and links

- [ ] Every "Download" in sections 1, 3 and 4 saves the file. A "View" opens it. Neither shows a broken page.
- [ ] Sign-in still works after moving between pages (a page refresh does not log the student out).

## Device and width matrix

Run sections 1 to 4 at least once on each:

- [ ] Phone, 360 px wide (portrait).
- [ ] Phone, landscape.
- [ ] Tablet or a narrow desktop window (about 800 px).
- [ ] Desktop (1280 px or wider).

## Failing or unclear results

For each fail, note: the section and line, the account used, the device and browser, and the exact text or screenshot.
Send these to me and I will fix them and rerun the checks you name.
