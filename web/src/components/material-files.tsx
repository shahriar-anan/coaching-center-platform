"use client";

import { AuthorizedFile } from "@/components/authorized-file";
import { useLocale } from "@/i18n/locale-context";
import { getAccessToken } from "@/lib/auth/token-store";
import { apiBaseUrl } from "@/lib/config";
import type { CourseView, MaterialView } from "@/lib/api/types";

export function FilePicker({
  id,
  label,
  accessibleName,
  accept,
  compact,
  onFile,
}: {
  id?: string;
  label: string;
  accessibleName?: string;
  accept?: string;
  compact?: boolean;
  onFile: (file: File) => void;
}) {
  const className = compact
    ? "inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-zinc-300 bg-white px-2.5 py-1.5 text-xs font-medium text-zinc-800 hover:bg-zinc-50"
    : "inline-flex cursor-pointer items-center gap-2 rounded-md border border-dashed border-zinc-400 bg-zinc-50 px-3 py-2 text-sm font-medium text-zinc-900 hover:border-zinc-900 hover:bg-white";
  return (
    <label className={className}>
      <UploadIcon />
      {label}
      <input
        id={id}
        type="file"
        accept={accept}
        aria-label={accessibleName ?? label}
        className="sr-only"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) {
            onFile(file);
          }
        }}
      />
    </label>
  );
}

export function FilePreview({
  fileId,
  name,
  contentType,
  size = "thumb",
}: {
  fileId: string;
  name: string;
  contentType: string;
  size?: "thumb" | "tile";
}) {
  const image = contentType.startsWith("image/");
  const tile = size === "tile";
  return (
    <div
      className={
        tile
          ? "flex h-32 w-full items-center justify-center overflow-hidden bg-zinc-50"
          : "flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md border border-zinc-200 bg-zinc-50"
      }
    >
      {image ? (
        <AuthorizedFile fileId={fileId} alt={name} className="h-full w-full object-cover" />
      ) : contentType === "application/pdf" ? (
        <PdfMark large={tile} />
      ) : (
        <FileMark large={tile} />
      )}
    </div>
  );
}

export function MaterialViewCard({ material }: { material: MaterialView }) {
  const { t } = useLocale();
  return (
    <li className="overflow-hidden rounded-lg border border-zinc-200 bg-white">
      <FilePreview fileId={material.id} name={material.originalFileName} contentType={material.contentType} size="tile" />
      <div className="border-t border-zinc-100 px-3 py-3">
        <p className="truncate text-sm font-medium text-zinc-900">{material.originalFileName}</p>
        <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-zinc-500">
          <span>{fileKind(material)}</span>
          <span aria-hidden="true">·</span>
          <span>{formatBytes(material.byteSize)}</span>
          {material.free ? (
            <span className="rounded-full bg-green-50 px-2 py-0.5 font-medium text-green-700">{t("courses.free")}</span>
          ) : null}
        </p>
        <OpenFileButton material={material} className="mt-3" />
      </div>
    </li>
  );
}

export function MaterialFileCard({
  material,
  course,
  isMaster,
  onReplace,
  onDelete,
  onMarkFree,
}: {
  material: MaterialView;
  course: CourseView;
  isMaster: boolean;
  onReplace: (materialId: string, file: File) => void;
  onDelete: (material: MaterialView) => void;
  onMarkFree: (materialId: string) => void;
}) {
  const { t } = useLocale();
  return (
    <li className="flex gap-3 rounded-md border border-zinc-200 bg-white p-3">
      <FilePreview fileId={material.id} name={material.originalFileName} contentType={material.contentType} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-zinc-900">
          {material.originalFileName}
          {material.free ? <span className="ml-2 text-xs font-normal text-green-700">{t("courses.free")}</span> : null}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <OpenFileButton material={material} />
          <FilePicker
            compact
            label={t("courses.replaceFile")}
            accessibleName={`${t("courses.replaceFile")} ${material.originalFileName}`}
            onFile={(file) => onReplace(material.id, file)}
          />
          {isMaster ? (
            <button
              type="button"
              className="rounded-md border border-red-200 px-2.5 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50"
              aria-label={`${t("courses.deleteFile")} ${material.originalFileName}`}
              onClick={() => onDelete(material)}
            >
              {t("courses.deleteFile")}
            </button>
          ) : null}
          {course.accessType === "FREEMIUM" && !material.free ? (
            <button
              type="button"
              className="rounded-md border border-zinc-300 px-2.5 py-1.5 text-xs font-medium text-zinc-800 hover:bg-zinc-50"
              onClick={() => void onMarkFree(material.id)}
            >
              {t("courses.markMaterialFree")}
            </button>
          ) : null}
        </div>
      </div>
    </li>
  );
}

export function OpenFileButton({ material, className }: { material: MaterialView; className?: string }) {
  const { t } = useLocale();
  return (
    <button
      type="button"
      className={`rounded-md border border-zinc-300 px-2.5 py-1.5 text-xs font-medium text-zinc-800 hover:bg-zinc-50 ${className ?? ""}`}
      aria-label={`${t("courses.openFile")} ${material.originalFileName}`}
      onClick={() => openMaterial(material.id, material.contentType)}
    >
      {t("courses.openFile")}
    </button>
  );
}

function openMaterial(fileId: string, contentType: string) {
  const token = getAccessToken();
  const popup = window.open("about:blank", "_blank");
  if (!popup) {
    return;
  }
  if (!token) {
    popup.close();
    return;
  }
  void fetch(`${apiBaseUrl}/api/v1/files/${fileId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
    .then(async (response) => {
      if (!response.ok) {
        popup.close();
        return;
      }
      const raw = await response.blob();
      const blob = raw.type ? raw : new Blob([raw], { type: contentType });
      const url = URL.createObjectURL(blob);
      popup.location.href = url;
      popup.opener = null;
    })
    .catch(() => {
      popup.close();
    });
}

function UploadIcon() {
  return (
    <svg viewBox="0 0 20 20" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <path d="M10 13V4m0 0L6.5 7.5M10 4l3.5 3.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 13.5V15a1 1 0 001 1h10a1 1 0 001-1v-1.5" strokeLinecap="round" />
    </svg>
  );
}

function PdfMark({ large }: { large?: boolean }) {
  return (
    <div className={`flex flex-col items-center text-red-700 ${large ? "gap-1" : ""}`}>
      <FileMark large={large} />
      <span className={large ? "text-xs font-semibold tracking-wide" : "text-[10px] font-semibold tracking-wide"}>PDF</span>
    </div>
  );
}

function FileMark({ large }: { large?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={large ? "h-10 w-10 text-zinc-500" : "h-7 w-7 text-zinc-500"}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      aria-hidden="true"
    >
      <path d="M7 3.5h6.5L19 9v11.5a1 1 0 01-1 1H7a1 1 0 01-1-1v-16a1 1 0 011-1z" strokeLinejoin="round" />
      <path d="M13.5 3.5V9H19" strokeLinejoin="round" />
    </svg>
  );
}

function fileKind(material: MaterialView) {
  const ext = material.originalFileName.split(".").pop();
  if (ext && ext !== material.originalFileName) {
    return ext.toUpperCase();
  }
  return "FILE";
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
