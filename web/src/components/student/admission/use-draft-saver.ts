"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { AdmissionForm } from "@/lib/api/exam/offline-admission";

/** At most one draft save every this many milliseconds (plan C9). */
export const SAVE_GAP_MS = 2000;

export type DraftStatus = "idle" | "saving" | "saved" | "failed";

/**
 * Saves the form as the student types, without ever holding up typing. A save starts at most once per `SAVE_GAP_MS`, and
 * never overlaps the one before it: the saves run one after another, and each sends the newest form when it starts. A
 * failed save keeps the form dirty, so the next change or `flush` sends it again.
 */
export function useDraftSaver(save: (form: AdmissionForm) => Promise<unknown>) {
  const [status, setStatus] = useState<DraftStatus>("idle");
  const saveRef = useRef(save);
  const latest = useRef<AdmissionForm | null>(null);
  const dirty = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const lastStart = useRef(0);
  const chain = useRef<Promise<boolean>>(Promise.resolve(true));

  useEffect(() => {
    saveRef.current = save;
  }, [save]);

  const runSave = useCallback((): Promise<boolean> => {
    const job = chain.current.then(async () => {
      const snapshot = latest.current;
      if (!dirty.current || !snapshot) {
        return true;
      }
      dirty.current = false;
      lastStart.current = Date.now();
      setStatus("saving");
      try {
        await saveRef.current(snapshot);
        setStatus(dirty.current ? "saving" : "saved");
        return true;
      } catch {
        dirty.current = true;
        setStatus("failed");
        return false;
      }
    });
    chain.current = job;
    return job;
  }, []);

  /** Records the newest form and makes sure a save follows, no sooner than the gap allows. */
  const schedule = useCallback(
    (form: AdmissionForm) => {
      latest.current = form;
      dirty.current = true;
      setStatus("saving");
      if (timer.current !== undefined) {
        return;
      }
      const wait = Math.max(0, lastStart.current + SAVE_GAP_MS - Date.now());
      timer.current = window.setTimeout(() => {
        timer.current = undefined;
        void runSave();
      }, wait);
    },
    [runSave],
  );

  /**
   * Sends whatever is unsaved now, and waits for the saves in progress. Resolves to false when the draft could not be
   * saved, so the caller does not submit a form the server does not have.
   */
  const flush = useCallback(async (): Promise<boolean> => {
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    return dirty.current ? runSave() : chain.current;
  }, [runSave]);

  // Leaving the screen: send what is unsaved. The save runs on after the screen is gone.
  useEffect(
    () => () => {
      if (timer.current !== undefined) {
        window.clearTimeout(timer.current);
        timer.current = undefined;
      }
      if (dirty.current) {
        void runSave();
      }
    },
    [runSave],
  );

  return { status, schedule, flush };
}
