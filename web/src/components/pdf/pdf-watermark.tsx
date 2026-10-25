import { memo } from "react";

const ROWS = 9;
const COLUMNS = 3;

/**
 * A repeating diagonal label drawn over the pages, never baked into the file. Low contrast so the
 * paper stays readable, and enough copies that a crop of any page still carries the name.
 */
export const PdfWatermark = memo(function PdfWatermark({ text }: { text: string }) {
  return (
    <div
      aria-hidden="true"
      data-testid="pdf-watermark"
      className="pointer-events-none absolute inset-0 select-none overflow-hidden"
    >
      <div className="absolute -inset-1/4 flex -rotate-[30deg] flex-col justify-around">
        {Array.from({ length: ROWS }, (_, row) => (
          <div key={row} className="flex justify-around whitespace-nowrap">
            {Array.from({ length: COLUMNS }, (_, column) => (
              <span
                key={column}
                className="font-site text-lg font-semibold text-text opacity-[0.09]"
              >
                {text}
              </span>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
});
