/**
 * Stand-in for FND's `StickyActionBar` (`@/components/ui/sticky-action-bar`).
 * Delete this file and switch the import once FND is merged.
 *
 * A bottom bar on phones with safe-area padding; inline on desktop.
 */
export function StickyActionBar({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-card p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:static md:border-0 md:bg-transparent md:p-0">
      <div className="mx-auto max-w-[760px]">{children}</div>
    </div>
  );
}
