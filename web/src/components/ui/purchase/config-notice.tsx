/** Shown when checkout is off. Renders nothing when `show` is false. Stub until P-6 lands. */
export function ConfigNotice({ show }: { show: boolean }) {
  if (!show) return null;
  return <p role="status">পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।</p>;
}
