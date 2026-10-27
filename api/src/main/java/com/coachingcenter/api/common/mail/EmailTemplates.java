package com.coachingcenter.api.common.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.coachingcenter.api.auth.Role;

/**
 * Every mail the system sends, in the school's look: the SYMAM LAW mark as a header image (linked from the site, never attached),
 * a short Bangla text with its English twin, a code box with a copy button, and a plain-text version for mail programs that do
 * not show HTML. The layout is tables with inline styles, which is what mail programs support; there is no script, because mail
 * programs remove it. A code is selectable with one tap, and the copy button opens the site's {@code /code} page, which has a
 * real clipboard button.
 */
public final class EmailTemplates {

	private static final String NAVY = "#14213d";

	private static final String MUTED = "#5d6578";

	private static final String GOLD = "#c8962e";

	private static final String GOLD_TEXT = "#8a5f12";

	private static final String CREAM = "#f6f4ee";

	private static final String BORDER = "#e3dfd3";

	private static final String FONT = "'Noto Sans Bengali','Kalpurush','Nirmala UI','Segoe UI',Helvetica,Arial,sans-serif";

	private EmailTemplates() {
	}

	// ----- account mail -----

	public static RenderedEmail activation(MailContext ctx, MailRecipient who, String code) {
		String first = who.firstName();
		String roleBn = roleBn(who.role());
		String roleEn = roleEn(who.role());
		String link = ctx.forRole(who.role(), "/activate#code=" + enc(code));
		String copy = copyLink(ctx, code, "activate");
		StringBuilder html = new StringBuilder();
		html.append(hero(first == null ? "স্বাগতম!" : "স্বাগতম, " + esc(first) + "!", "Welcome on board"));
		html.append(para("এক্সামপোর্টালে আপনাকে " + roleBn + " হিসেবে যুক্ত করা হয়েছে। অ্যাকাউন্টটি চালু করতে নিচের কোডটি ব্যবহার করুন এবং নিজের পছন্দের পাসওয়ার্ড দিন।",
				"You have been added to the exam portal as " + roleEn + ". Use the code below to activate your account and choose your own password."));
		html.append(codeBox(ctx, code, copy, "আপনার অ্যাকাউন্ট চালু করার কোড", "Your activation code"));
		html.append(steps(List.of(
				new String[] { "নিচের বোতামে ক্লিক করে অ্যাকাউন্ট চালু করার পাতা খুলুন।", "Open the activation page with the button below." },
				new String[] { "আপনার ফোন নম্বর, এই কোড এবং নতুন পাসওয়ার্ড দিন।", "Enter your phone number, this code and a new password." },
				new String[] { "লগইন করে শুরু করুন।", "Sign in and get started." })));
		html.append(button("অ্যাকাউন্ট চালু করুন", "Activate account", link));
		html.append(note("কোডটি " + bn(ctx.activationDays()) + " দিন পর্যন্ত কার্যকর থাকবে এবং একবারই ব্যবহার করা যাবে। সাইমাম ল'-এর কেউ কখনো এই কোড চাইবেন না।",
				"This code works for " + ctx.activationDays() + " days and can be used once. Nobody from SYMAM LAW will ever ask you for it."));
		String text = text(first == null ? "স্বাগতম! / Welcome on board" : "স্বাগতম, " + first + "! / Welcome on board",
				"এক্সামপোর্টালে আপনাকে " + roleBn + " হিসেবে যুক্ত করা হয়েছে। / You have been added to the exam portal as " + roleEn + ".",
				"কোড / Code: " + code, "অ্যাকাউন্ট চালু করুন / Activate your account: " + link,
				"কোডটি " + bn(ctx.activationDays()) + " দিন কার্যকর। / The code works for " + ctx.activationDays() + " days.");
		return new RenderedEmail("স্বাগতম! আপনার অ্যাকাউন্ট চালু করুন · Welcome on board",
				frame(ctx, "আপনার অ্যাকাউন্ট তৈরি হয়েছে। কোডটি দিয়ে চালু করুন।", html.toString()), text);
	}

	public static RenderedEmail emailVerification(MailContext ctx, MailRecipient who, String code) {
		String first = who.firstName();
		String link = ctx.forRole(who.role(), who.role() == null || who.role() == Role.STUDENT ? "/account" : "/staff/account");
		String copy = copyLink(ctx, code, "verify");
		StringBuilder html = new StringBuilder();
		html.append(hero(first == null ? "স্বাগতম!" : "স্বাগতম, " + esc(first) + "!", "Welcome on board"));
		html.append(para("এক্সামপোর্টালে আপনার অ্যাকাউন্ট তৈরি হয়েছে। ইমেইলটি যাচাই করতে অ্যাকাউন্ট পাতায় নিচের কোডটি দিন।",
				"Your exam portal account is ready. To verify your email, enter the code below on your account page."));
		html.append(codeBox(ctx, code, copy, "ইমেইল যাচাইয়ের কোড", "Your verification code"));
		html.append(button("অ্যাকাউন্ট পাতা খুলুন", "Open my account", link));
		html.append(note("কোডটি " + bn(ctx.emailMinutes()) + " মিনিট পর্যন্ত কার্যকর। আপনি অ্যাকাউন্ট না খুলে থাকলে এই ইমেইলটি উপেক্ষা করুন।",
				"This code works for " + ctx.emailMinutes() + " minutes. If you did not create an account, you can ignore this email."));
		String text = text(first == null ? "স্বাগতম! / Welcome on board" : "স্বাগতম, " + first + "! / Welcome on board",
				"ইমেইল যাচাইয়ের কোড / Your verification code: " + code, "অ্যাকাউন্ট পাতা / Your account page: " + link,
				"কোডটি " + bn(ctx.emailMinutes()) + " মিনিট কার্যকর। / The code works for " + ctx.emailMinutes() + " minutes.");
		return new RenderedEmail("ইমেইল যাচাইয়ের কোড · Verify your email",
				frame(ctx, "আপনার ইমেইল যাচাই করার কোড।", html.toString()), text);
	}

	public static RenderedEmail passwordReset(MailContext ctx, MailRecipient who, String code) {
		String first = who.firstName();
		String link = ctx.forRole(who.role(), "/reset-password#code=" + enc(code));
		String copy = copyLink(ctx, code, "reset");
		StringBuilder html = new StringBuilder();
		html.append(hero("পাসওয়ার্ড রিসেট করুন", "Reset your password"));
		html.append(para((first == null ? "হ্যালো," : "হ্যালো " + esc(first) + ",") + " আপনার অ্যাকাউন্টের পাসওয়ার্ড রিসেটের অনুরোধ পেয়েছি। নিচের কোডটি দিয়ে নতুন পাসওয়ার্ড দিন।",
				(first == null ? "Hello," : "Hello " + esc(first) + ",") + " we received a request to reset your password. Use the code below to choose a new one."));
		html.append(codeBox(ctx, code, copy, "পাসওয়ার্ড রিসেটের কোড", "Your reset code"));
		html.append(button("নতুন পাসওয়ার্ড দিন", "Choose a new password", link));
		html.append(note("কোডটি " + bn(ctx.resetMinutes()) + " মিনিট পর্যন্ত কার্যকর। আপনি অনুরোধ না করে থাকলে এই ইমেইলটি উপেক্ষা করুন; আপনার পাসওয়ার্ড বদলাবে না।",
				"This code works for " + ctx.resetMinutes() + " minutes. If you did not ask for this, ignore this email; your password will not change."));
		String text = text("পাসওয়ার্ড রিসেট করুন / Reset your password", "কোড / Code: " + code,
				"নতুন পাসওয়ার্ড দিন / Choose a new password: " + link,
				"কোডটি " + bn(ctx.resetMinutes()) + " মিনিট কার্যকর। / The code works for " + ctx.resetMinutes() + " minutes.");
		return new RenderedEmail("পাসওয়ার্ড রিসেটের কোড · Reset your password",
				frame(ctx, "পাসওয়ার্ড রিসেটের কোড, " + bn(ctx.resetMinutes()) + " মিনিট কার্যকর।", html.toString()), text);
	}

	public static RenderedEmail passwordChanged(MailContext ctx, MailRecipient who) {
		return changed(ctx, who, "আপনার পাসওয়ার্ড বদলানো হয়েছে", "Your password was changed",
				"আপনার অ্যাকাউন্টের পাসওয়ার্ড এইমাত্র বদলানো হয়েছে।", "The password on your account was just changed.");
	}

	public static RenderedEmail phoneChanged(MailContext ctx, MailRecipient who) {
		return changed(ctx, who, "আপনার ফোন নম্বর বদলানো হয়েছে", "Your phone number was changed",
				"আপনার অ্যাকাউন্টের ফোন নম্বর এইমাত্র বদলানো হয়েছে।", "The phone number on your account was just changed.");
	}

	private static RenderedEmail changed(MailContext ctx, MailRecipient who, String titleBn, String titleEn, String bodyBn, String bodyEn) {
		String first = who.firstName();
		String link = ctx.forRole(who.role(), "/login");
		StringBuilder html = new StringBuilder();
		html.append(hero(titleBn, titleEn));
		html.append(para((first == null ? "" : "হ্যালো " + esc(first) + ", ") + bodyBn + " এটি আপনি করে থাকলে আর কিছু করার নেই।",
				(first == null ? "" : "Hello " + esc(first) + ", ") + bodyEn + " If this was you, there is nothing more to do."));
		html.append(warning("আপনি এটি না করে থাকলে এখনই আমাদের সাথে যোগাযোগ করুন এবং পাসওয়ার্ড বদলে নিন।",
				"If you did not do this, contact us right away and change your password."));
		html.append(button("লগইন করুন", "Sign in", link));
		html.append(note("সাইমাম ল' আপনার কাছে কখনো পাসওয়ার্ড বা কোড চাইবে না।", "SYMAM LAW will never ask you for your password or a code."));
		String text = text(titleBn + " / " + titleEn, bodyBn + " / " + bodyEn,
				"আপনি না করে থাকলে আমাদের সাথে যোগাযোগ করুন। / If this was not you, contact us: " + ctx.site("/contact"));
		return new RenderedEmail(titleBn + " · " + titleEn, frame(ctx, titleBn, html.toString()), text);
	}

	// ----- exam mail -----

	/**
	 * The receipt a buyer gets after a paid order: what they bought (a package or one exam), the amount, how they paid, the
	 * transaction number and the time, and a button to open what they bought. {@code openPath} is a path on the student site.
	 */
	public static RenderedEmail paymentReceipt(MailContext ctx, MailRecipient who, boolean module, String itemBn, String itemEn,
			String amount, String channel, String tranId, String when, String openPath) {
		String first = who == null ? null : who.firstName();
		String kindBn = module ? "প্যাকেজ" : "পরীক্ষা";
		String kindEn = module ? "Package" : "Exam";
		String money = money(amount);
		String how = channelLabel(channel);
		StringBuilder html = new StringBuilder();
		html.append(hero(first == null ? "ধন্যবাদ!" : "ধন্যবাদ, " + esc(first) + "!", "Thank you for your purchase"));
		// the paid mark and the amount, large and centred
		html.append("<table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:22px auto 4px;\"><tr><td align=\"center\">"
				+ "<div style=\"display:inline-block;background:#e9f5ec;color:#1f6b3a;border-radius:999px;padding:5px 14px;font-size:13px;font-weight:700;\">"
				+ "&#10003;&nbsp; পরিশোধিত · Paid</div>"
				+ "<div style=\"font-size:40px;line-height:1.2;font-weight:700;color:" + NAVY + ";padding:12px 0 0;\">" + money + "</div>"
				+ "<div style=\"font-size:12px;letter-spacing:2px;color:" + MUTED + ";padding-top:2px;\">BDT</div>"
				+ "</td></tr></table>");
		html.append(facts(List.of(
				new String[] { kindBn, kindEn, esc(itemBn) + "<br><span style=\"color:" + MUTED + ";font-size:13px\">" + esc(itemEn) + "</span>" },
				new String[] { "পেমেন্টের মাধ্যম", "Paid with", esc(how) },
				new String[] { "লেনদেন নম্বর", "Transaction",
						"<span style=\"font-family:'Courier New',Courier,monospace;font-size:14px;\">" + esc(tranId) + "</span>" },
				new String[] { "তারিখ", "Date", esc(when) + " <span style=\"color:" + MUTED + ";font-size:13px\">(ঢাকা)</span>" })));
		html.append(button(module ? "আমার পরীক্ষা দেখুন" : "পরীক্ষাটি খুলুন", module ? "See my exams" : "Open the exam", ctx.site(openPath)));
		html.append(note("আপনার অ্যাক্সেস এখন চালু। রসিদটি সংরক্ষণ করুন; কোনো সমস্যা হলে লেনদেন নম্বরসহ আমাদের জানান।",
				"Your access is open now. Keep this receipt, and quote the transaction number if you need help."));
		String text = text("পেমেন্ট রসিদ / Payment receipt", (first == null ? "" : first + ", ") + "ধন্যবাদ! / Thank you for your purchase.",
				kindBn + " / " + kindEn + ": " + itemBn + " / " + itemEn, "পরিমাণ / Amount: " + plainMoney(amount) + " BDT",
				"পেমেন্টের মাধ্যম / Paid with: " + how, "লেনদেন / Transaction: " + tranId, "তারিখ / Date: " + when + " (ঢাকা / Dhaka)",
				"খুলুন / Open: " + ctx.site(openPath));
		return new RenderedEmail("পেমেন্ট রসিদ · Payment receipt", frame(ctx, "৳" + plainMoney(amount) + " · " + itemBn, html.toString()), text);
	}

	/** The note the Master Admin gets for each paid order. */
	public static RenderedEmail purchaseNotice(MailContext ctx, String studentName, String phone, String email, String itemBn,
			String itemEn, String amount, String channel, String tranId, String when) {
		StringBuilder html = new StringBuilder();
		html.append(hero("নতুন পেমেন্ট পাওয়া গেছে", "New purchase received"));
		html.append(facts(List.of(
				new String[] { "শিক্ষার্থী", "Student", esc(studentName) },
				new String[] { "ফোন", "Phone", esc(phone) },
				new String[] { "ইমেইল", "Email", esc(email) },
				new String[] { "কী কিনেছেন", "Item", esc(itemBn) + "<br><span style=\"color:" + MUTED + ";font-size:13px\">" + esc(itemEn) + "</span>" },
				new String[] { "পরিমাণ", "Amount", "<strong>৳" + esc(amount) + "</strong> BDT" },
				new String[] { "মাধ্যম", "Paid with", esc(channel) },
				new String[] { "লেনদেন নম্বর", "Transaction", esc(tranId) },
				new String[] { "সময়", "Time", esc(when) + " (ঢাকা)" })));
		html.append(button("পেমেন্ট দেখুন", "View payments", ctx.forRole(Role.MASTER_ADMIN, "/staff/payments")));
		String text = text("নতুন পেমেন্ট / New purchase", "শিক্ষার্থী / Student: " + studentName, "ফোন / Phone: " + phone,
				"ইমেইল / Email: " + email, itemBn + " / " + itemEn, "পরিমাণ / Amount: " + amount + " BDT",
				"মাধ্যম / Paid with: " + channel, "লেনদেন / Transaction: " + tranId, "সময় / Time: " + when);
		return new RenderedEmail("নতুন পেমেন্ট · New purchase: " + itemEn, frame(ctx, studentName + " — ৳" + amount, html.toString()), text);
	}

	/** An exam's time, or the one-hour reminder. */
	public static RenderedEmail examNotice(MailContext ctx, boolean hour, String titleBn, String titleEn, String when, String examPath) {
		StringBuilder html = new StringBuilder();
		html.append(hero(hour ? "আপনার পরীক্ষা শীঘ্রই শুরু হচ্ছে" : "পরীক্ষার সময় জানানো হয়েছে",
				hour ? "Your exam starts soon" : "An exam time has been announced"));
		html.append(para(hour ? "আপনার পরীক্ষা প্রায় এক ঘণ্টার মধ্যে শুরু হবে। প্রস্তুত থাকুন এবং শুরুর আগে লগইন করে নিন।"
				: "আপনি যে পরীক্ষার অ্যাক্সেস পেয়েছেন, তার সময় নির্ধারিত হয়েছে।",
				hour ? "Your exam starts in about one hour. Be ready, and sign in before it begins."
						: "The time is set for an exam you have access to."));
		html.append(facts(List.of(
				new String[] { "পরীক্ষা", "Exam", esc(titleBn) + "<br><span style=\"color:" + MUTED + ";font-size:13px\">" + esc(titleEn) + "</span>" },
				new String[] { "শুরু", "Starts", "<strong>" + esc(when) + "</strong> (ঢাকা)" })));
		html.append(button("পরীক্ষা খুলুন", "Open the exam", ctx.site(examPath)));
		String text = text(hour ? "পরীক্ষা শুরুর আগে / Exam starting soon" : "পরীক্ষার সময় / Exam time",
				hour ? "আপনার পরীক্ষা প্রায় এক ঘণ্টার মধ্যে শুরু হবে। / Your exam starts in about one hour."
						: "পরীক্ষার সময় জানানো হয়েছে। / An exam time has been announced.",
				titleBn + " / " + titleEn, "শুরু / Starts: " + when, "পরীক্ষা খুলুন / Open the exam: " + ctx.site(examPath));
		return new RenderedEmail(hour ? "পরীক্ষা শুরুর আগে · Exam starting soon" : "পরীক্ষার সময় · Exam time",
				frame(ctx, titleBn + " — " + when, html.toString()), text);
	}

	// ----- building blocks -----

	private static String frame(MailContext ctx, String preheader, String content) {
		return "<!doctype html>\n<html lang=\"bn\"><head><meta charset=\"utf-8\">"
				+ "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
				+ "<meta name=\"color-scheme\" content=\"light only\"><meta name=\"supported-color-schemes\" content=\"light only\">"
				+ "<title>SYMAM LAW · এক্সামপোর্টাল</title></head>"
				+ "<body style=\"margin:0;padding:0;background:" + CREAM + ";\">"
				+ "<div style=\"display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;color:" + CREAM + ";\">"
				+ esc(preheader) + "</div>"
				+ "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + CREAM + ";\">"
				+ "<tr><td align=\"center\" style=\"padding:28px 12px;\">"
				+ "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:600px;\">"
				// header: the school's mark as a heading
				+ "<tr><td align=\"center\" style=\"background:#ffffff;border-radius:16px 16px 0 0;border-top:5px solid " + GOLD
				+ ";border-left:1px solid " + BORDER + ";border-right:1px solid " + BORDER + ";padding:30px 32px 18px;\">"
				+ "<a href=\"" + attr(ctx.siteUrl()) + "\" style=\"text-decoration:none;\">"
				+ "<img src=\"" + attr(ctx.logoUrl()) + "\" width=\"72\" alt=\"SYMAM LAW\" style=\"display:block;margin:0 auto 12px;border:0;outline:none;height:auto;\"></a>"
				+ "<div style=\"font-family:" + FONT + ";font-size:24px;line-height:1.2;font-weight:700;color:" + NAVY + ";\">এক্সাম<span style=\"color:" + GOLD_TEXT + ";\">পোর্টাল</span></div>"
				+ "<div style=\"font-family:" + FONT + ";font-size:11px;letter-spacing:3px;color:" + MUTED + ";padding-top:6px;\">SYMAM LAW</div>"
				+ "</td></tr>"
				// body
				+ "<tr><td style=\"background:#ffffff;border-left:1px solid " + BORDER + ";border-right:1px solid " + BORDER + ";padding:4px 36px 34px;font-family:" + FONT + ";color:" + NAVY + ";\">"
				+ content
				+ "</td></tr>"
				// footer
				+ "<tr><td align=\"center\" style=\"background:#ffffff;border:1px solid " + BORDER + ";border-top:1px solid " + BORDER + ";border-radius:0 0 16px 16px;padding:22px 32px 26px;font-family:" + FONT + ";\">"
				+ "<div style=\"font-size:13px;line-height:1.6;color:" + MUTED + ";\">এটি একটি স্বয়ংক্রিয় বার্তা, উত্তর দেওয়ার প্রয়োজন নেই।<br>This is an automatic message; there is no need to reply.</div>"
				+ "<div style=\"font-size:13px;line-height:1.6;padding-top:10px;\"><a href=\"" + attr(ctx.site("/contact")) + "\" style=\"color:" + GOLD_TEXT + ";text-decoration:underline;\">যোগাযোগ · Contact</a>"
				+ "&nbsp;&nbsp;·&nbsp;&nbsp;<a href=\"" + attr(ctx.site("/help")) + "\" style=\"color:" + GOLD_TEXT + ";text-decoration:underline;\">সাহায্য · Help</a></div>"
				+ "<div style=\"font-size:12px;color:" + MUTED + ";padding-top:12px;\">© SYMAM LAW · সাইমাম ল'</div>"
				+ "</td></tr></table></td></tr></table></body></html>";
	}

	private static String hero(String titleBn, String titleEn) {
		return "<div style=\"text-align:center;padding:14px 0 6px;\">"
				+ "<h1 style=\"margin:0;font-size:28px;line-height:1.3;font-weight:700;color:" + NAVY + ";\">" + titleBn + "</h1>"
				+ "<div style=\"margin-top:4px;font-size:16px;line-height:1.4;color:" + GOLD_TEXT + ";font-weight:600;\">" + titleEn + "</div></div>";
	}

	private static String para(String bn, String en) {
		return "<p style=\"margin:18px 0 6px;font-size:16px;line-height:1.7;color:" + NAVY + ";\">" + bn + "</p>"
				+ "<p style=\"margin:0 0 6px;font-size:14px;line-height:1.65;color:" + MUTED + ";\">" + en + "</p>";
	}

	private static String codeBox(MailContext ctx, String code, String copyUrl, String labelBn, String labelEn) {
		return "<table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:22px auto 4px;\"><tr>"
				+ "<td align=\"center\" style=\"background:#fbf6ea;border:2px dashed " + GOLD + ";border-radius:14px;padding:16px 30px 18px;\">"
				+ "<div style=\"font-size:12px;line-height:1.4;color:" + GOLD_TEXT + ";font-weight:600;\">" + labelBn + " · " + labelEn + "</div>"
				+ "<div style=\"font-family:'Courier New',Courier,monospace;font-size:34px;line-height:1.3;font-weight:700;letter-spacing:6px;color:" + NAVY
				+ ";padding:8px 0 12px;-webkit-user-select:all;user-select:all;\">" + esc(code) + "</div>"
				+ "<a href=\"" + attr(copyUrl) + "\" style=\"display:inline-block;background:#ffffff;border:1px solid " + GOLD + ";border-radius:999px;padding:8px 16px;"
				+ "font-size:13px;font-weight:600;color:" + NAVY + ";text-decoration:none;\">"
				+ "<img src=\"" + attr(ctx.copyIconUrl()) + "\" width=\"16\" height=\"16\" alt=\"\" style=\"vertical-align:-3px;border:0;margin-right:6px;\">"
				+ "কোড কপি করুন · Copy code</a>"
				+ "</td></tr></table>"
				+ "<div style=\"text-align:center;font-size:12px;color:" + MUTED + ";padding-bottom:8px;\">কোডে ট্যাপ করে ধরে রেখেও কপি করতে পারেন · You can also tap and hold the code to copy it</div>";
	}

	private static String steps(List<String[]> items) {
		StringBuilder out = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:18px 0 4px;\">");
		int number = 1;
		for (String[] item : items) {
			out.append("<tr><td width=\"34\" valign=\"top\" style=\"padding:6px 0;\"><div style=\"width:26px;height:26px;line-height:26px;border-radius:13px;background:").append(GOLD)
				.append(";color:").append(NAVY).append(";font-size:13px;font-weight:700;text-align:center;\">").append(number++).append("</div></td>")
				.append("<td valign=\"top\" style=\"padding:5px 0;font-size:14px;line-height:1.55;color:").append(NAVY).append(";\">")
				.append(item[0]).append("<br><span style=\"color:").append(MUTED).append(";font-size:13px;\">").append(item[1]).append("</span></td></tr>");
		}
		return out.append("</table>").toString();
	}

	private static String button(String labelBn, String labelEn, String url) {
		return "<table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:22px auto 8px;\"><tr>"
				+ "<td align=\"center\" style=\"background:" + GOLD + ";border-radius:10px;\">"
				+ "<a href=\"" + attr(url) + "\" style=\"display:inline-block;padding:14px 34px;font-size:16px;font-weight:700;color:" + NAVY + ";text-decoration:none;\">"
				+ labelBn + " · " + labelEn + "</a></td></tr></table>";
	}

	private static String note(String bn, String en) {
		return "<div style=\"margin-top:20px;padding-top:16px;border-top:1px solid " + BORDER + ";font-size:13px;line-height:1.6;color:" + MUTED + ";\">"
				+ bn + "<br>" + en + "</div>";
	}

	private static String warning(String bn, String en) {
		return "<div style=\"margin:20px 0 4px;padding:14px 16px;background:#fdf3e1;border-left:4px solid " + GOLD + ";border-radius:8px;font-size:14px;line-height:1.6;color:"
				+ NAVY + ";\"><strong>" + bn + "</strong><br><span style=\"color:" + MUTED + ";\">" + en + "</span></div>";
	}

	private static String facts(List<String[]> rows) {
		StringBuilder out = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:20px 0 4px;border:1px solid ")
			.append(BORDER).append(";border-radius:12px;border-collapse:separate;\">");
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			String line = i == rows.size() - 1 ? "" : "border-bottom:1px solid " + BORDER + ";";
			out.append("<tr><td valign=\"top\" width=\"38%\" style=\"padding:12px 14px;").append(line).append("font-size:13px;line-height:1.5;color:").append(MUTED).append(";\">")
				.append(row[0]).append("<br><span style=\"font-size:12px;\">").append(row[1]).append("</span></td>")
				.append("<td valign=\"top\" style=\"padding:12px 14px;").append(line).append("font-size:15px;line-height:1.5;color:").append(NAVY).append(";\">")
				.append(row[2]).append("</td></tr>");
		}
		return out.append("</table>").toString();
	}

	/** "2500.00" becomes "৳2,500.00". */
	private static String money(String amount) {
		return "৳" + plainMoney(amount);
	}

	private static String plainMoney(String amount) {
		try {
			return new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT))
				.format(new java.math.BigDecimal(amount));
		}
		catch (NumberFormatException exception) {
			return amount;
		}
	}

	/** The gateway reports a channel such as "BKASH-BKash"; show the readable part. A missing channel is the gateway itself. */
	private static String channelLabel(String channel) {
		if (channel == null || channel.isBlank() || "-".equals(channel.trim())) {
			return "SSLCommerz";
		}
		String value = channel.trim();
		int dash = value.lastIndexOf('-');
		String name = dash > 0 && dash < value.length() - 1 ? value.substring(dash + 1) : value;
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "bkash" -> "bKash";
			case "nagad" -> "Nagad";
			case "rocket" -> "Rocket";
			default -> name;
		};
	}

	private static String copyLink(MailContext ctx, String code, String purpose) {
		return ctx.site("/code#c=" + enc(code) + "&for=" + purpose);
	}

	private static String text(String... lines) {
		StringBuilder out = new StringBuilder();
		for (String line : lines) {
			out.append(line).append("\n\n");
		}
		return out.append("— SYMAM LAW · এক্সামপোর্টাল\n").toString();
	}

	private static String roleBn(Role role) {
		if (role == null) {
			return "ব্যবহারকারী";
		}
		return switch (role) {
			case STUDENT -> "শিক্ষার্থী";
			case INSTRUCTOR -> "শিক্ষক";
			case SYSTEM_ADMIN -> "সিস্টেম অ্যাডমিন";
			case MASTER_ADMIN -> "মাস্টার অ্যাডমিন";
		};
	}

	private static String roleEn(Role role) {
		if (role == null) {
			return "a user";
		}
		return switch (role) {
			case STUDENT -> "a student";
			case INSTRUCTOR -> "an instructor";
			case SYSTEM_ADMIN -> "a system admin";
			case MASTER_ADMIN -> "the master admin";
		};
	}

	/** Bangla digits, for numbers inside Bangla sentences. */
	private static String bn(int value) {
		StringBuilder out = new StringBuilder();
		for (char digit : String.valueOf(value).toCharArray()) {
			out.append((char) ('০' + (digit - '0')));
		}
		return out.toString();
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	/** Escapes text that comes from a person or a record before it goes into HTML. */
	static String esc(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}

	private static String attr(String value) {
		return esc(value);
	}

}
