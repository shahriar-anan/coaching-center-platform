import type { ReactNode } from "react";

export type SectionNavItem = {
  id: string;
  label: string;
  state: "done" | "todo" | "error";
};

/** Jump links to the sections of a long form. Stub: a plain list of links. */
export function SectionNav({ sections }: { sections: SectionNavItem[] }): ReactNode {
  return (
    <nav>
      <ul>
        {sections.map((section) => (
          <li key={section.id} data-state={section.state}>
            <a href={`#${section.id}`}>{section.label}</a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
