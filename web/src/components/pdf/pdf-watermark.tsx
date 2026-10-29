import { memo } from "react";
import Image from "next/image";

/** Height of the strip above and below a student's page that carries the name and ID line. */
export const STUDENT_STRIP_PX = 24;

/**
 * The space a page reserves above and below its paper for the name and ID line. Staff pages reserve
 * none. The strips are part of the page, not drawn over it, so the line never crosses the question text.
 */
export function pageStrip(studentLine: string | undefined): number {
  return studentLine ? STUDENT_STRIP_PX : 0;
}

/**
 * Drawn over every page, never baked into the file. The school mark sits once in the middle of the
 * page, faded and not rotated. The paper is white in both themes, so the light mark is used in both.
 * A student's view also prints `studentLine` (their name and ID) as one small line in a strip at the
 * top and at the bottom of the page. The ink is a fixed colour, not a theme token.
 */
export const PdfWatermark = memo(function PdfWatermark({ studentLine }: { studentLine?: string }) {
  const strip = pageStrip(studentLine);
  return (
    <div
      aria-hidden="true"
      data-testid="pdf-watermark"
      className="pointer-events-none absolute inset-0 select-none overflow-hidden"
    >
      <div className="absolute inset-x-0 flex items-center justify-center" style={{ top: strip, bottom: strip }}>
        <Image
          src="/brand/logo-light.png"
          alt=""
          width={390}
          height={451}
          className="h-auto w-[55%] max-w-none opacity-[0.06] mix-blend-multiply"
        />
      </div>
      {studentLine ? (
        <>
          <StudentLine text={studentLine} position="top" height={strip} />
          <StudentLine text={studentLine} position="bottom" height={strip} />
        </>
      ) : null}
    </div>
  );
});

function StudentLine({ text, position, height }: { text: string; position: "top" | "bottom"; height: number }) {
  return (
    <div
      className="absolute inset-x-0 flex items-center justify-center px-3 font-site text-[11px] whitespace-nowrap text-[#14213d] opacity-60"
      style={{ height, [position]: 0 }}
    >
      <span className="truncate">{text}</span>
    </div>
  );
}
