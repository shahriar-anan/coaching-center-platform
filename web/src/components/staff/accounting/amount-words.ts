import type { Locale } from "@/i18n/locale-context";

/*
 * An amount in words for a voucher (accounting-plan 4.6, B10): "Twelve thousand five hundred taka only", "বারো হাজার পাঁচশত টাকা মাত্র".
 * Indian grouping: lakh (1,00,000) and crore (1,00,00,000). Paisa are written after the taka. The amount is a string with at most
 * two decimals, as the API answers money.
 */

const EN_ONES = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
const EN_TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

/** Bangla words for 0 to 99, one word each (the usual written forms). */
const BN_UNDER_100 = [
  "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
  "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোল", "সতেরো", "আঠারো", "উনিশ",
  "বিশ", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আঠাশ", "উনত্রিশ",
  "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "উনচল্লিশ",
  "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "উনপঞ্চাশ",
  "পঞ্চাশ", "একান্ন", "বায়ান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "উনষাট",
  "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "উনসত্তর",
  "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "উনআশি",
  "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "উননব্বই",
  "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই",
];

/** Hundreds as Bangla words: 1 is "একশত", 5 is "পাঁচশত". */
const BN_HUNDREDS = ["", "একশত", "দুইশত", "তিনশত", "চারশত", "পাঁচশত", "ছয়শত", "সাতশত", "আটশত", "নয়শত"];

function enUnder1000(n: number): string {
  const parts: string[] = [];
  const hundreds = Math.floor(n / 100);
  const rest = n % 100;
  if (hundreds > 0) {
    parts.push(`${EN_ONES[hundreds]} hundred`);
  }
  if (rest > 0) {
    if (rest < 20) {
      parts.push(EN_ONES[rest]);
    } else {
      const tens = EN_TENS[Math.floor(rest / 10)];
      const ones = rest % 10;
      parts.push(ones > 0 ? `${tens}-${EN_ONES[ones].toLowerCase()}` : tens);
    }
  }
  return parts.join(" ");
}

/** English words for a whole number, Indian grouping: crore, lakh, thousand, then the hundreds. Zero is "Zero". */
export function englishWords(n: number): string {
  if (n === 0) {
    return "Zero";
  }
  const crore = Math.floor(n / 10000000);
  const lakh = Math.floor((n % 10000000) / 100000);
  const thousand = Math.floor((n % 100000) / 1000);
  const rest = n % 1000;
  const parts: string[] = [];
  if (crore > 0) {
    parts.push(`${enUnder1000(crore)} crore`);
  }
  if (lakh > 0) {
    parts.push(`${enUnder1000(lakh)} lakh`);
  }
  if (thousand > 0) {
    parts.push(`${enUnder1000(thousand)} thousand`);
  }
  if (rest > 0) {
    parts.push(enUnder1000(rest));
  }
  const text = parts.join(" ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function bnUnder1000(n: number): string {
  const hundreds = Math.floor(n / 100);
  const rest = n % 100;
  const head = hundreds > 0 ? BN_HUNDREDS[hundreds] : "";
  const tail = rest > 0 ? BN_UNDER_100[rest] : "";
  return [head, tail].filter(Boolean).join(" ");
}

/** Bangla words for a whole number, Indian grouping: কোটি, লক্ষ, হাজার, then the hundreds. Zero is "শূন্য". */
export function banglaWords(n: number): string {
  if (n === 0) {
    return BN_UNDER_100[0];
  }
  const crore = Math.floor(n / 10000000);
  const lakh = Math.floor((n % 10000000) / 100000);
  const thousand = Math.floor((n % 100000) / 1000);
  const rest = n % 1000;
  const parts: string[] = [];
  if (crore > 0) {
    parts.push(`${bnUnder1000(crore)} কোটি`);
  }
  if (lakh > 0) {
    parts.push(`${bnUnder1000(lakh)} লক্ষ`);
  }
  if (thousand > 0) {
    parts.push(`${bnUnder1000(thousand)} হাজার`);
  }
  if (rest > 0) {
    parts.push(bnUnder1000(rest));
  }
  return parts.join(" ");
}

/** The amount in words for a voucher: the taka, then the paisa when there are any, and "only" (or "মাত্র"). */
export function amountInWords(amount: string, locale: Locale): string {
  const [whole, fraction = ""] = amount.split(".");
  const taka = Number(whole || "0");
  const paisa = Number((fraction + "00").slice(0, 2));
  if (locale === "bn") {
    const takaText = `${banglaWords(taka)} টাকা`;
    return paisa > 0 ? `${takaText} ${banglaWords(paisa)} পয়সা মাত্র` : `${takaText} মাত্র`;
  }
  const takaText = `${englishWords(taka)} taka`;
  return paisa > 0 ? `${takaText} and ${englishWords(paisa).toLowerCase()} paisa only` : `${takaText} only`;
}
