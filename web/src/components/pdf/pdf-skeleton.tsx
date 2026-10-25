export function PdfSkeleton() {
  return (
    <div
      aria-hidden="true"
      className="h-[70vh] max-h-[880px] min-h-[360px] w-full animate-pulse rounded-card border border-border bg-card motion-reduce:animate-none"
    >
      <div className="mx-auto mt-6 h-[85%] w-[80%] rounded-control bg-bg" />
    </div>
  );
}
