# Coaching Center Platform

A course-based platform for a coaching center. Students use an Android app. Staff run the center from a web admin panel. Both talk to one API.

The center teaches online, in person, and in hybrid batches. One system covers all three.

## Applications

| App | Who uses it | Role |
|---|---|---|
| `mobile` | Students | Discover and buy courses, watch lectures, read notes, take exams, see results, mark attendance, read notices, and order books. |
| `web` | Master Admin and System Admin | Manage students, courses, exams, attendance, payments, the library, notices, and admin accounts. |
| `api` | Both apps | Business rules, authentication, and data. The clients do not enforce permissions on their own. |

## Accounts

- **Student.** Uses the Android app. A student can register themselves, or an admin can create the account for an in-person admission and hand over a one-time activation code. The student sets their own password. No admin can see or reset it.
- **Master Admin.** One account. Full operational and financial authority, including pricing, payments, analytics, admin accounts, and deletes.
- **System Admin.** Day-to-day operations. Can create and edit what they are allowed to. Cannot change prices, payments, or financial reports, and cannot delete records.

## What the product includes

**Students**

- Sign in, profile, and password recovery by email
- Course catalog, purchase, and enrollment, including access that expires
- Lectures (unlisted YouTube videos played in the app), PDF notes, and other materials
- MCQ exams and written exams submitted as photos
- Results, after an admin publishes them
- Attendance: scan a QR code for a physical class, or tap a button for an online class
- Notices and push notifications
- Orders for physical books, for pickup or delivery

**Staff**

- Students, batches, and enrollments, including admissions taken at the center
- Courses as a flexible outline of sections and lectures
- Exams, a question bank, written-exam review, and publishing results
- Attendance sessions and corrections
- Payments for courses and book orders
- Book catalog and order handling
- Notices
- Admin accounts, analytics, and an audit log

The admin panel is in Bangla and English. Course content is entered once and is not translated.

Payments start as a manual bKash or Nagad transaction ID that the Master Admin verifies. A payment gateway can be connected later without changing the rest of the system.

## Not in this product yet

- An iOS app, or a student website
- Live classes, in-app chat, and a parent portal
- Digital books, delivery tracking, coupons, and automatic refunds
- SMS login, Facebook login, and teacher or evaluator accounts
- Certificates and advanced reporting

## Repository

```text
api/       Spring Boot API (Java 21)
web/       Next.js admin panel
mobile/    Flutter app (Android)
docs/      Product specification
```

The database is PostgreSQL.

## Local development

Each app is run from its own directory. Fill in the steps below as they are settled.

### API

Requires Java 21, Maven (or the included wrapper), and PostgreSQL. Configuration comes from environment variables, including `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.

```bash
cd api
./mvnw spring-boot:run
```

### Web

```bash
cd web
npm install
npm run dev
```

### Mobile

Requires the Flutter SDK. Android is the target.

```bash
cd mobile
flutter pub get
flutter run
```

## Documentation

The product specification is in [docs/prd/PROJECT_PRD.md](docs/prd/PROJECT_PRD.md).
