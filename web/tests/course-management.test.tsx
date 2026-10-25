import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LocaleProvider } from "@/i18n/locale-context";
import en from "@/i18n/messages/en";
import bn from "@/i18n/messages/bn";
import type { MessageTree } from "@/i18n/messages/en";
import { CourseContentPage } from "@/components/course-content-page";
import { CourseOverviewPage } from "@/components/course-overview-page";
import { CourseRecordPage } from "@/components/course-record-page";
import NewCoursePage from "@/app/(admin)/courses/new/page";
import { setAccessToken } from "@/lib/auth/token-store";
import type { CategoryTreeNode, CourseNodeView, CourseView, MaterialView } from "@/lib/api/types";

const nav = vi.hoisted(() => ({ push: vi.fn() }));
const session = vi.hoisted(() => ({ role: "MASTER_ADMIN" }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: vi.fn() }),
  useParams: () => ({ id: "course-1" }),
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: ReactNode;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/lib/auth/session-context", () => ({
  useUser: () => ({
    id: "user-1",
    phone: "+8801700000000",
    email: "admin@example.com",
    emailVerifiedAt: null,
    role: session.role,
    status: "ACTIVE",
    fullName: "Admin",
    studentCode: null,
  }),
}));

type Call = { url: string; method: string; body: unknown; form: FormData | null };

const calls: Call[] = [];

function pathOf(url: string): string {
  const parsed = new URL(url);
  return parsed.pathname + parsed.search;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function error(code: string, status: number): Response {
  return json(
    {
      code,
      message: code,
      timestamp: "2026-10-04T00:00:00Z",
      requestId: "req-1",
    },
    status,
  );
}

function course(overrides: Partial<CourseView> = {}): CourseView {
  return {
    id: "course-1",
    titleEn: "Course One",
    titleBn: "কোর্স এক",
    contentLanguage: "EN",
    description: "A course",
    price: 500,
    deliveryMode: "ONLINE",
    accessType: "FREEMIUM",
    status: "DRAFT",
    closed: false,
    openingNumber: 0,
    coverFileId: "cover-1",
    categoryId: "main",
    categoryPosition: 1,
    ...overrides,
  };
}

function tree(): CategoryTreeNode[] {
  return [
    {
      kind: "CATEGORY",
      id: "main",
      parentId: null,
      titleEn: "Main Category",
      titleBn: "মূল",
      position: 0,
      children: [
        {
          kind: "CATEGORY",
          id: "sub-1",
          parentId: "main",
          titleEn: "Sub Category-1",
          titleBn: "উপ ১",
          position: 0,
          children: [],
        },
        {
          kind: "COURSE",
          id: "course-1",
          parentId: null,
          titleEn: "Course One",
          titleBn: "কোর্স এক",
          position: 1,
          children: null,
        },
        {
          kind: "CATEGORY",
          id: "sub-2",
          parentId: "main",
          titleEn: "Sub Category-2",
          titleBn: "উপ ২",
          position: 2,
          children: [],
        },
        {
          kind: "COURSE",
          id: "course-2",
          parentId: null,
          titleEn: "Course Two",
          titleBn: "কোর্স দুই",
          position: 3,
          children: null,
        },
      ],
    },
  ];
}

function nodes(): CourseNodeView[] {
  return [
    {
      id: "lec-root",
      parentId: null,
      nodeType: "LECTURE",
      title: "Welcome",
      position: 0,
      free: false,
      videoProvider: null,
      videoRef: null,
      notes: "hello",
    },
    {
      id: "sec-1",
      parentId: null,
      nodeType: "SECTION",
      title: "Week 1",
      position: 1,
      free: false,
      videoProvider: null,
      videoRef: null,
      notes: null,
    },
    {
      id: "lec-nested",
      parentId: "sec-1",
      nodeType: "LECTURE",
      title: "Class 1",
      position: 0,
      free: false,
      videoProvider: "YOUTUBE",
      videoRef: "abc123",
      notes: "class notes",
    },
  ];
}

function materials(): MaterialView[] {
  return [
    {
      id: "mat-lec",
      courseId: "course-1",
      lectureNodeId: "lec-nested",
      originalFileName: "notes.pdf",
      byteSize: 12,
      contentType: "application/pdf",
      free: false,
    },
    {
      id: "mat-course",
      courseId: "course-1",
      lectureNodeId: null,
      originalFileName: "syllabus.pdf",
      byteSize: 12,
      contentType: "application/pdf",
      free: false,
    },
    {
      id: "cover-1",
      courseId: "course-1",
      lectureNodeId: null,
      originalFileName: "cover.png",
      byteSize: 12,
      contentType: "image/png",
      free: false,
    },
  ];
}

let currentCourse = course();
let currentNodes = nodes();
let currentMaterials = materials();
let currentTree = tree();
let nodeSerial = 1;

function installFetch() {
  calls.length = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      const form = init?.body instanceof FormData ? init.body : null;
      const body = typeof init?.body === "string" ? JSON.parse(init.body) : null;
      const call = { url, method, body, form };
      calls.push(call);
      const path = pathOf(url);

      if (path.startsWith("/api/v1/files/")) {
        return new Response(new Blob(["img"], { type: "image/png" }));
      }
      if (path === "/api/v1/courses" && method === "POST") {
        return json(course({ ...body, id: "course-1", status: "DRAFT", coverFileId: null }));
      }
      if (path === "/api/v1/courses/course-1" && method === "GET") {
        return json(currentCourse);
      }
      if (path === "/api/v1/courses/course-1" && method === "PATCH") {
        currentCourse = { ...currentCourse, ...body };
        return json(currentCourse);
      }
      if (path === "/api/v1/courses/course-1/cover" && method === "POST") {
        currentCourse = { ...currentCourse, coverFileId: "cover-1" };
        return json({ id: "cover-1" }, 201);
      }
      if (path === "/api/v1/course-categories" && method === "GET") {
        return json(currentTree);
      }
      if (path === "/api/v1/course-categories" && method === "POST") {
        return json({ id: "cat-new", parentId: body.parentId ?? null, ...body, position: 0 }, 201);
      }
      if (path.startsWith("/api/v1/course-categories/") && method === "DELETE") {
        if (path.endsWith("/empty")) {
          return error("CATEGORY_NOT_EMPTY", 409);
        }
        return new Response(null, { status: 204 });
      }
      if (path === "/api/v1/courses/course-1/nodes" && method === "GET") {
        return json(
          currentNodes.map((node) =>
            node.parentId == null ? { ...node, parentId: undefined } : node,
          ),
        );
      }
      if (path === "/api/v1/courses/course-1/nodes" && method === "POST") {
        const created = {
          id: `node-new-${nodeSerial++}`,
          parentId: body.parentId ?? null,
          nodeType: body.nodeType,
          title: body.title,
          position: currentNodes.length,
          free: false,
          videoProvider: null,
          videoRef: null,
          notes: null,
        };
        currentNodes = [...currentNodes, created];
        return json(created, 201);
      }
      if (path.includes("/nodes/") && method === "PATCH") {
        const id = path.split("/").pop();
        currentNodes = currentNodes.map((node) => (node.id === id ? { ...node, ...body } : node));
        return json(currentNodes.find((node) => node.id === id));
      }
      if (path.endsWith("/nodes/reorder") && method === "POST") {
        return new Response(null, { status: 204 });
      }
      if (path.includes("/nodes/") && method === "DELETE") {
        if (path.endsWith("/sec-1")) {
          return error("SECTION_NOT_EMPTY", 409);
        }
        currentNodes = currentNodes.filter((node) => !path.endsWith(`/${node.id}`));
        currentMaterials = currentMaterials.filter((material) => !path.endsWith(`/${material.lectureNodeId}`));
        return new Response(null, { status: 204 });
      }
      if (path === "/api/v1/courses/course-1/materials" && method === "GET") {
        return json(currentMaterials);
      }
      if (path.startsWith("/api/v1/courses/course-1/materials") && method === "POST") {
        return json({ id: "mat-new", originalFileName: "upload.bin", lectureNodeId: null }, 201);
      }
      if (path.includes("/materials/") && method === "PUT") {
        return json({ id: path.split("/").pop(), originalFileName: "replaced.bin" });
      }
      if (path.includes("/materials/") && method === "DELETE") {
        currentMaterials = currentMaterials.filter((material) => !path.endsWith(`/${material.id}`));
        return new Response(null, { status: 204 });
      }
      if (path.includes("/materials/") && method === "PATCH") {
        return json({ id: path.split("/").pop(), free: true });
      }
      return json({ code: "RESOURCE_NOT_FOUND", message: path, timestamp: "", requestId: "req-x" }, 404);
    }),
  );
}

function posted(path: string, method = "POST"): Call[] {
  return calls.filter((call) => pathOf(call.url) === path && call.method === method);
}

function renderPage(ui: ReactNode) {
  return render(<LocaleProvider initialLocale="en">{ui}</LocaleProvider>);
}

beforeEach(() => {
  session.role = "MASTER_ADMIN";
  nav.push.mockReset();
  currentCourse = course();
  currentNodes = nodes();
  currentMaterials = materials();
  currentTree = tree();
  nodeSerial = 1;
  setAccessToken("token");
  vi.spyOn(window, "confirm").mockReturnValue(true);
  installFetch();
});

describe("catalogs", () => {
  it("keeps the Bangla catalog aligned with English", () => {
    expect(collectKeys(bn)).toEqual(collectKeys(en));
  });
});

describe("course details", () => {
  it("creates a draft, uploads the cover, and opens the course view", async () => {
    const user = userEvent.setup();
    renderPage(<NewCoursePage />);
    expect(screen.getByRole("navigation", { name: "Course steps" }).textContent).toContain("Details");
    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.queryByLabelText("Subcategory 1")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Done" })).not.toBeInTheDocument();
    expect(screen.getByText("Content")).toBeInTheDocument();

    await user.type(screen.getByLabelText("English title"), "Algebra");
    await user.type(screen.getByLabelText("Bangla title"), "বীজগণিত");
    await user.type(screen.getByLabelText("Description"), "First term");
    await user.type(screen.getByLabelText("Price"), "1200");
    const cover = new File(["img"], "cover.png", { type: "image/png" });
    await user.upload(screen.getByLabelText("Upload cover"), cover);
    await user.click(screen.getByRole("button", { name: "Save and continue" }));

    const create = posted("/api/v1/courses")[0];
    expect(create.body).toMatchObject({
      titleEn: "Algebra",
      titleBn: "বীজগণিত",
      contentLanguage: "EN",
      description: "First term",
      deliveryMode: "ONLINE",
      accessType: "FREEMIUM",
      price: 1200,
    });
    expect(create.body).not.toHaveProperty("descriptionBn");
    expect(posted("/api/v1/courses/course-1/cover")[0].form?.get("file")).toBeInstanceOf(File);
    expect(nav.push).toHaveBeenCalledWith("/courses/course-1");
  });

  it("omits price for a system admin and for a full-free course", async () => {
    const user = userEvent.setup();
    session.role = "SYSTEM_ADMIN";
    const { unmount } = renderPage(<NewCoursePage />);
    expect(screen.queryByLabelText("Price")).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("English title"), "Algebra");
    await user.type(screen.getByLabelText("Bangla title"), "বীজগণিত");
    await user.type(screen.getByLabelText("Description"), "First term");
    await user.click(screen.getByRole("button", { name: "Save and continue" }));
    expect(posted("/api/v1/courses")[0].body).not.toHaveProperty("price");
    unmount();

    session.role = "MASTER_ADMIN";
    calls.length = 0;
    renderPage(<NewCoursePage />);
    await user.selectOptions(screen.getByLabelText("Access type"), "FULL_FREE");
    expect(screen.queryByLabelText("Price")).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("English title"), "Algebra");
    await user.type(screen.getByLabelText("Bangla title"), "বীজগণিত");
    await user.type(screen.getByLabelText("Description"), "First term");
    await user.click(screen.getByRole("button", { name: "Save and continue" }));
    expect(posted("/api/v1/courses")[0].body).toMatchObject({ accessType: "FULL_FREE" });
    expect(posted("/api/v1/courses")[0].body).not.toHaveProperty("price");
  });

  it("shows a saved course on details without an access-type control", async () => {
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByRole("link", { name: "View course" })).toHaveAttribute(
      "href",
      "/courses/course-1",
    );
    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Content" })).toHaveAttribute("href", "/courses/course-1/content");
    expect(screen.queryByRole("combobox", { name: "Access type" })).not.toBeInTheDocument();
    expect(screen.getByText(/Freemium/)).toBeInTheDocument();
    expect(await screen.findAllByAltText("Course One")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Archive" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: (name) => name === "Delete" })).toBeInTheDocument();
  });

  it("hides price, archive, and delete from a system admin", async () => {
    session.role = "SYSTEM_ADMIN";
    const user = userEvent.setup();
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByLabelText("English title")).toBeInTheDocument();
    expect(screen.queryByLabelText("Price")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText("Description"));
    await user.type(screen.getByLabelText("Description"), "Updated");
    await user.click(screen.getByRole("button", { name: "Save and continue" }));
    expect(posted("/api/v1/courses/course-1", "PATCH")[0].body).not.toHaveProperty("price");
  });
});

describe("course view", () => {
  it("shows the saved course, its place, and its lectures", async () => {
    const user = userEvent.setup();
    renderPage(<CourseOverviewPage courseId="course-1" />);
    expect(await screen.findByText("A course")).toBeInTheDocument();
    expect(screen.queryByText("syllabus.pdf")).not.toBeInTheDocument();
    expect(screen.getByText("Placed in")).toBeInTheDocument();
    expect(screen.getByText("Main Category")).toBeInTheDocument();
    expect(screen.queryByText("This course")).not.toBeInTheDocument();
    expect(screen.getByText("500")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Details" })).toHaveAttribute("href", "/courses/course-1/details");
    expect(screen.queryByLabelText("English title")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Category" }));
    const categoryList = screen.getByRole("list", { name: "Category" });
    expect(within(categoryList).getByText("Main Category")).toBeInTheDocument();
    expect(within(categoryList).getByText("This course")).toBeInTheDocument();
    expect(within(categoryList).getByText("Course One")).toBeInTheDocument();
    expect(within(categoryList).queryByText("মূল")).not.toBeInTheDocument();
    expect(categoryList.textContent).not.toContain("Sub Category-1");
    expect(categoryList.textContent).not.toContain("Course Two");
    await user.click(screen.getByRole("button", { name: "Bangla title" }));
    expect(within(categoryList).getByText("মূল")).toBeInTheDocument();
    expect(within(categoryList).getByText("কোর্স এক")).toBeInTheDocument();
    expect(within(categoryList).queryByText("Main Category")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Curriculum" }));
    expect(screen.getByRole("heading", { name: "1. Week 1" })).toBeInTheDocument();
    expect(screen.getByText("1. Welcome")).toBeInTheDocument();
    expect(screen.getByText("1. Class 1")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lecture Welcome" }));
    expect(screen.getByRole("region", { name: "Lecture details" })).toHaveTextContent("hello");
    await user.click(screen.getByRole("button", { name: "Lecture Class 1" }));
    expect(screen.getByRole("link", { name: "https://www.youtube.com/watch?v=abc123" })).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Materials" }));
    expect(screen.getByText("syllabus.pdf")).toBeInTheDocument();
    expect(screen.getByText("notes.pdf")).toBeInTheDocument();
    expect(screen.queryByText("cover.png")).not.toBeInTheDocument();
  });

  it("opens a material in a new browser tab", async () => {
    const user = userEvent.setup();
    const popup = { location: { href: "" }, opener: window, close: vi.fn() };
    const open = vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);
    renderPage(<CourseOverviewPage courseId="course-1" />);
    await user.click(await screen.findByRole("tab", { name: "Materials" }));
    await user.click(screen.getByRole("button", { name: "Open syllabus.pdf" }));
    expect(open).toHaveBeenCalledWith("about:blank", "_blank");
    await waitFor(() => expect(popup.location.href).toBe("blob:test"));
    open.mockRestore();
  });

  it("hides the price from a system admin", async () => {
    session.role = "SYSTEM_ADMIN";
    renderPage(<CourseOverviewPage courseId="course-1" />);
    expect(await screen.findByText("A course")).toBeInTheDocument();
    expect(screen.queryByText("Price")).not.toBeInTheDocument();
    expect(screen.queryByText("500")).not.toBeInTheDocument();
  });
});

describe("category", () => {
  it("shows this course on the main category when Done is pressed", async () => {
    const user = userEvent.setup();
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByLabelText("Subcategory 1")).toHaveValue("");
    await user.click(screen.getByRole("button", { name: "Done" }));
    expect(screen.getByText("Placed in Main Category")).toBeInTheDocument();
    const text = screen.getByRole("list", { name: "Category" }).textContent ?? "";
    expect(text).toContain("Main Category");
    expect(text).toContain("Course One");
    expect(text).not.toContain("Course Two");
    expect(text).not.toContain("Sub Category-1");
    expect(screen.getByText("This course")).toBeInTheDocument();
  });

  it("places this course under a new subcategory from details", async () => {
    const user = userEvent.setup();
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByRole("option", { name: "Main Category মূল" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Category"), "main");
    await user.selectOptions(screen.getByLabelText("Subcategory 1"), "__new__");
    const subForm = screen.getByRole("group", { name: "Add subcategory" });
    await user.type(within(subForm).getByLabelText("English title"), "Sub Category-3");
    await user.type(within(subForm).getByLabelText("Bangla title"), "উপ ৩");
    await user.click(screen.getByRole("button", { name: "Save and continue" }));

    expect(posted("/api/v1/course-categories")[0].body).toEqual({
      titleEn: "Sub Category-3",
      titleBn: "উপ ৩",
      parentId: "main",
    });
    const place = posted("/api/v1/courses/course-1", "PATCH")[0];
    expect(place.body).toMatchObject({ categoryId: "cat-new" });
    expect(place.body).not.toHaveProperty("nodeType");
    expect(nav.push).toHaveBeenCalledWith("/courses/course-1");
  });

  it("previews a course nested under several new subcategories", async () => {
    const user = userEvent.setup();
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByRole("option", { name: "Main Category মূল" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Subcategory 1"), "__new__");
    const first = screen.getByRole("group", { name: "Add subcategory" });
    await user.type(within(first).getByLabelText("English title"), "Sub Category-3");
    await user.type(within(first).getByLabelText("Bangla title"), "উপ ৩");
    await user.selectOptions(screen.getByLabelText("Subcategory 2"), "__new__");
    const second = screen.getAllByRole("group", { name: "Add subcategory" })[1];
    await user.type(within(second).getByLabelText("English title"), "Sub Category-4");
    await user.type(within(second).getByLabelText("Bangla title"), "উপ ৪");
    expect(screen.getByLabelText("Subcategory 3")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect(screen.getByText("Placed in Main Category / Sub Category-3 / Sub Category-4")).toBeInTheDocument();
    const list = screen.getByRole("list", { name: "Category" });
    const text = list.textContent ?? "";
    expect(text.indexOf("Sub Category-3")).toBeLessThan(text.indexOf("Sub Category-4"));
    expect(text.indexOf("Sub Category-4")).toBeLessThan(text.indexOf("Course One"));
    expect(text).not.toContain("Course Two");
    expect(text).not.toContain("Sub Category-1");
    expect(within(list).getByText("This course")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save and continue" }));
    const created = posted("/api/v1/course-categories");
    expect(created[0].body).toEqual({
      titleEn: "Sub Category-3",
      titleBn: "উপ ৩",
      parentId: "main",
    });
    expect(created[1].body).toEqual({
      titleEn: "Sub Category-4",
      titleBn: "উপ ৪",
      parentId: "cat-new",
    });
    expect(posted("/api/v1/courses/course-1", "PATCH")[0].body).toMatchObject({ categoryId: "cat-new" });
  });

  it("shows an empty tree and keeps a category when delete is rejected", async () => {
    currentTree = [];
    currentCourse = course({ categoryId: null, categoryPosition: null });
    const user = userEvent.setup();
    const { unmount } = renderPage(<CourseOverviewPage courseId="course-1" />);
    expect(await screen.findByRole("tab", { name: "Category" })).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "Category" }));
    expect(screen.getByText("No categories yet.")).toBeInTheDocument();
    expect(screen.getByText("This course is not in a category yet.")).toBeInTheDocument();
    unmount();

    currentTree = [
      {
        kind: "CATEGORY",
        id: "empty",
        parentId: null,
        titleEn: "Empty Category",
        titleBn: "খালি",
        position: 0,
        children: [],
      },
    ];
    renderPage(<CourseRecordPage courseId="course-1" />);
    expect(await screen.findByRole("option", { name: "Empty Category খালি" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Category"), "empty");
    await user.click(screen.getByRole("button", { name: "Delete Empty Category" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This category still has a subcategory or a course, so it was not deleted.",
    );
    expect(screen.getByRole("option", { name: "Empty Category খালি" })).toBeInTheDocument();
  });
});

describe("content", () => {
  it("keeps a new section and its lecture on the outline", async () => {
    const user = userEvent.setup();
    renderPage(<CourseContentPage courseId="course-1" />);
    expect(await screen.findByRole("heading", { name: "1. Week 1" })).toBeInTheDocument();
    expect(screen.getByText("1. Class 1")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Lectures on the course" })).toBeInTheDocument();
    expect(screen.getByText("1. Welcome")).toBeInTheDocument();
    expect(screen.getByLabelText("Add course file")).toBeInTheDocument();
    expect(screen.getByText("syllabus.pdf")).toBeInTheDocument();
    expect(screen.queryByText("cover.png")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete file syllabus.pdf" }));
    expect(posted("/api/v1/courses/course-1/materials/mat-course", "DELETE")).toHaveLength(1);
    expect(screen.queryByText("syllabus.pdf")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to details" })).toHaveAttribute(
      "href",
      "/courses/course-1/details",
    );
    const week = screen.getByRole("region", { name: "1. Week 1" });
    expect(week.querySelector("input[type='file']")).toBeNull();

    await user.click(screen.getByRole("button", { name: "Add section" }));
    const sectionForm = screen.getByRole("form", { name: "Add section" });
    await user.type(within(sectionForm).getByLabelText("Section title"), "Week 2");
    await user.click(within(sectionForm).getByRole("button", { name: "Add section" }));
    expect(posted("/api/v1/courses/course-1/nodes")[0].body).toEqual({
      nodeType: "SECTION",
      title: "Week 2",
    });
    expect(screen.getByRole("heading", { name: "2. Week 2" })).toBeInTheDocument();

    const lectureForm = screen.getByRole("form", { name: "Add lecture Week 2" });
    await user.type(within(lectureForm).getByLabelText("Lecture title"), "Nested class");
    await user.click(within(lectureForm).getByRole("button", { name: "Add lecture" }));
    expect(posted("/api/v1/courses/course-1/nodes")[1].body).toEqual({
      nodeType: "LECTURE",
      title: "Nested class",
      parentId: "node-new-1",
    });
    expect(screen.getByText("1. Nested class")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add lecture Week 2" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save and view" }));
    expect(nav.push).toHaveBeenCalledWith("/courses/course-1");
  });

  it("saves an open lecture before opening the course view", async () => {
    const user = userEvent.setup();
    renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Welcome" }));
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.clear(screen.getByLabelText("Notes"));
    await user.type(screen.getByLabelText("Notes"), "Saved from the footer");
    await user.click(screen.getByRole("button", { name: "Save and view" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-root", "PATCH")[0].body).toMatchObject({
      notes: "Saved from the footer",
    });
    expect(nav.push).toHaveBeenCalledWith("/courses/course-1");
  });

  it("saves notes, clears YouTube, replaces a file, and moves a lecture", async () => {
    const user = userEvent.setup();
    renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Welcome" }));
    expect(screen.getByRole("region", { name: "Lecture details" })).toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Lecture" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.clear(screen.getByLabelText("Notes"));
    await user.type(screen.getByLabelText("Notes"), "Updated notes");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-root", "PATCH")[0].body).toMatchObject({
      title: "Welcome",
      notes: "Updated notes",
    });
    expect(screen.getByRole("region", { name: "Lecture details" })).toHaveTextContent("Updated notes");
    expect(screen.queryByLabelText("Notes")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Section" }), { target: { value: "sec-1" } });
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-root", "PATCH").at(-1)?.body).toMatchObject({
      parentId: "sec-1",
    });

    await user.click(screen.getByRole("button", { name: "Lecture Class 1" }));
    expect(screen.getByRole("region", { name: "Lecture details" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Clear YouTube URL" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-nested", "PATCH")[0].body).toEqual({ videoUrl: null });

    const file = new File(["pdf"], "notes.pdf", { type: "application/pdf" });
    await user.upload(screen.getByLabelText("Replace file notes.pdf"), file);
    expect(posted("/api/v1/courses/course-1/materials/mat-lec", "PUT")).toHaveLength(1);

    const lectureFile = new File(["a"], "class.pdf", { type: "application/pdf" });
    await user.upload(screen.getByLabelText("Add file Class 1"), lectureFile);
    expect(pathOf(calls.find((call) => call.method === "POST" && call.url.includes("/materials"))!.url)).toContain(
      "lectureNodeId=lec-nested",
    );

    const courseFile = new File(["b"], "syllabus.pdf", { type: "application/pdf" });
    await user.upload(screen.getByLabelText("Add course file"), courseFile);
    const courseUpload = calls.filter((call) => call.method === "POST" && call.url.includes("/materials")).at(-1)!;
    expect(pathOf(courseUpload.url)).not.toContain("lectureNodeId");

    fireEvent.change(screen.getByRole("combobox", { name: "Section" }), { target: { value: "" } });
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-nested", "PATCH").at(-1)?.body).toMatchObject({
      parentId: null,
    });
  });

  it("leaves a section in place when it still has a lecture", async () => {
    const user = userEvent.setup();
    renderPage(<CourseContentPage courseId="course-1" />);
    await screen.findByRole("heading", { name: "1. Week 1" });
    await user.click(screen.getByRole("button", { name: "Actions Week 1" }));
    await user.click(screen.getByRole("button", { name: "Delete Week 1" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This section still has a lecture, so it was not deleted.",
    );
    expect(screen.getByRole("heading", { name: "1. Week 1" })).toBeInTheDocument();
    expect(screen.getByText("1. Class 1")).toBeInTheDocument();
  });

  it("offers file and node delete only to a master admin", async () => {
    const user = userEvent.setup();
    const { unmount } = renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Class 1" }));
    expect(screen.queryByRole("button", { name: "Delete file notes.pdf" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByRole("button", { name: "Delete file notes.pdf" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Actions Class 1" }));
    expect(screen.getByRole("button", { name: "Delete Class 1" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Clear YouTube URL" })).toBeInTheDocument();
    unmount();

    session.role = "SYSTEM_ADMIN";
    renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Class 1" }));
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Actions Class 1" }));
    await user.click(screen.getByRole("button", { name: "Actions Week 1" }));
    expect(screen.queryByRole("button", { name: "Delete file notes.pdf" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete Class 1" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete Week 1" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Clear YouTube URL" })).toBeInTheDocument();
    expect(screen.getByLabelText("Replace file notes.pdf")).toBeInTheDocument();
  });

  it("marks free content once and hides that control on a full-free course", async () => {
    const user = userEvent.setup();
    renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Welcome" }));
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByText("This cannot be undone.")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Mark free" })).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "Mark free" }));
    await user.click(within(screen.getByRole("form", { name: "Lecture" })).getByRole("button", { name: "Save" }));
    expect(posted("/api/v1/courses/course-1/nodes/lec-root", "PATCH").at(-1)?.body).toMatchObject({ free: true });

    await user.click(screen.getByRole("button", { name: "Mark material free" }));
    expect(posted("/api/v1/courses/course-1/materials/mat-course", "PATCH")[0].body).toEqual({ free: true });
  });

  it("hides free-flag controls on a full-free course", async () => {
    const user = userEvent.setup();
    currentCourse = course({ accessType: "FULL_FREE", price: null });
    renderPage(<CourseContentPage courseId="course-1" />);
    await user.click(await screen.findByRole("button", { name: "Lecture Welcome" }));
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.queryByRole("checkbox", { name: "Mark free" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark material free" })).not.toBeInTheDocument();
  });
});

function collectKeys(tree: MessageTree, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const next = prefix ? `${prefix}.${key}` : key;
    return typeof value === "string" ? [next] : collectKeys(value as MessageTree, next);
  });
}
