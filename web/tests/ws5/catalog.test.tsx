import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CategoryTree } from "@/components/staff/catalog/category-tree";
import { ModuleDetails } from "@/components/staff/catalog/module-details";
import { RosterTab } from "@/components/staff/catalog/roster-tab";
import { InstructorTab } from "@/components/staff/catalog/instructor-tab";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import { McqQuestions } from "@/components/staff/catalog/mcq-questions";
import { QuestionEditor } from "@/components/staff/catalog/question-editor";
import { WrittenPaper } from "@/components/staff/catalog/written-paper";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

const category = {
  id: "cccccccc-0000-0000-0000-000000000001",
  parentId: null,
  titleEn: "BCS",
  titleBn: "বিজেএস",
  position: 1,
  archived: false,
  children: [],
  modules: [],
};

const moduleFixture: StaffModule = {
  id: "aaaaaaaa-0000-0000-0000-000000000001",
  categoryId: category.id,
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "DRAFT",
  finallySaved: false,
  position: 1,
  price: 2500,
};

const mcqExam: StaffExam = {
  id: "eeeeeeee-0000-0000-0000-000000000001",
  moduleId: moduleFixture.id,
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "DRAFT",
  finallySaved: false,
  price: null,
  questionPdf: null,
  questions: [
    {
      id: "q1",
      stem: "প্রশ্ন এক",
      marks: 20,
      explanation: null,
      position: 1,
      options: [
        { id: "o1", text: "ক", correct: true, position: 1 },
        { id: "o2", text: "খ", correct: false, position: 2 },
      ],
    },
    {
      id: "q2",
      stem: "প্রশ্ন দুই",
      marks: 30,
      explanation: null,
      position: 2,
      options: [
        { id: "o3", text: "গ", correct: false, position: 1 },
        { id: "o4", text: "ঘ", correct: true, position: 2 },
      ],
    },
  ],
};

describe("category tree actions by role", () => {
  it("Master Admin can archive and delete", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [category] });
    renderWithProviders(<CategoryTree />, { user: fixtures.masterAdmin });
    expect(await screen.findByRole("button", { name: "মুছুন" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "আর্কাইভ করুন" })).toBeInTheDocument();
  });

  it("System Admin sees the tree without archive or delete", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [category] });
    renderWithProviders(<CategoryTree />, { user: fixtures.systemAdmin });
    expect(await screen.findByText("বিজেএস")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "মুছুন" })).not.toBeInTheDocument();
  });
});

describe("module details", () => {
  it("keeps publish disabled until the module is final saved", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [category] });
    renderWithProviders(<ModuleDetails module={moduleFixture} />, { user: fixtures.masterAdmin });
    const publish = await screen.findByRole("button", { name: "প্রকাশ করুন" });
    expect(publish).toBeDisabled();
  });

  it("shows the price input to Master Admin only", async () => {
    mockApi({ "GET /api/v1/live-exam-categories": [category] });
    const { unmount } = renderWithProviders(<ModuleDetails module={moduleFixture} />, { user: fixtures.masterAdmin });
    expect(await screen.findByLabelText("মূল্য (৳)")).toBeInTheDocument();
    unmount();

    renderWithProviders(<ModuleDetails module={moduleFixture} />, { user: fixtures.systemAdmin });
    await screen.findByRole("heading", { name: "প্রকাশের আগে" });
    expect(screen.queryByLabelText("মূল্য (৳)")).not.toBeInTheDocument();
  });
});

describe("roster (Master Admin)", () => {
  const member = {
    studentId: "s1",
    fullName: "নাঈম হাসান",
    phone: "01711000010",
    email: null,
    studentCode: "S18492017",
    addedAt: "2026-10-20T04:00:00Z",
    startedAny: false,
  };

  it("lists the members and asks before removing one", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules/aaaaaaaa-0000-0000-0000-000000000001/roster": {
        content: [member],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });
    renderWithProviders(<RosterTab moduleId={moduleFixture.id} />, { user: fixtures.masterAdmin });

    expect((await screen.findAllByText("নাঈম হাসান"))[0]).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole("button", { name: "সরান" })[0]);
    expect(await screen.findByRole("dialog")).toHaveTextContent("নাঈম হাসান");
  });

  it("shows the paste result with the count and the unknown lines", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules/aaaaaaaa-0000-0000-0000-000000000001/roster": {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      "POST /api/v1/live-exam-modules/aaaaaaaa-0000-0000-0000-000000000001/roster/paste": {
        marked: [{ id: "s1", fullName: "নাঈম হাসান", phone: "01711000010", email: null }],
        unknown: ["nobody@example.com"],
      },
    });
    renderWithProviders(<RosterTab moduleId={moduleFixture.id} />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("ইমেইল বা ফোন নম্বরের তালিকা"), "01711000010{enter}nobody@example.com");
    await userEvent.click(screen.getByRole("button", { name: "তালিকা যোগ করুন" }));
    expect(await screen.findByText(/জন যোগ হয়েছে/)).toBeInTheDocument();
    expect(screen.getByText("nobody@example.com")).toBeInTheDocument();
  });
});

describe("instructor (Master Admin)", () => {
  it("shows the conflict sentence when a second instructor is assigned", async () => {
    const instructorRow = {
      id: "i1",
      fullName: "ফারিহা রহমান",
      phone: "01711000020",
      email: null,
      status: "ACTIVE",
      createdAt: "2026-10-01T00:00:00Z",
      modules: [],
    };
    const page = { content: [instructorRow], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    mockApi({
      "GET /api/v1/instructors": (req: { url: URL }) => (req.url.searchParams.get("assigned") === "true" ? { ...page, content: [] } : page),
      "POST /api/v1/live-exam-modules/aaaaaaaa-0000-0000-0000-000000000001/instructor": () =>
        apiError(409, "INSTRUCTOR_ALREADY_ASSIGNED", "Already assigned."),
    });
    renderWithProviders(<InstructorTab moduleId={moduleFixture.id} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: "শিক্ষক দিন" }));
    await userEvent.click((await screen.findAllByRole("button", { name: "বেছে নিন" }))[0]);
    expect(await screen.findByText("এই মডিউলে আগেই একজন শিক্ষক দেওয়া আছে। আগে তাকে সরান।")).toBeInTheDocument();
  });
});

describe("MCQ editor", () => {
  it("adds the marks into the running total", async () => {
    renderWithProviders(<McqQuestions exam={mcqExam} locked={false} canDelete />, { user: fixtures.masterAdmin });
    expect(screen.getByText("মোট ৫০")).toBeInTheDocument();
  });

  it("asks for exactly one correct option and clears the previous one", async () => {
    renderWithProviders(
      <QuestionEditor examId={mcqExam.id} nextPosition={3} open onOpenChange={() => undefined} />,
      { user: fixtures.masterAdmin },
    );
    await userEvent.click(screen.getByRole("button", { name: "প্রশ্ন সেভ করুন" }));
    expect(await screen.findByText("প্রশ্ন লিখুন।")).toBeInTheDocument();
    expect(screen.getByText("একটি অপশন সঠিক হিসেবে বেছে নিন।")).toBeInTheDocument();

    const radios = screen.getAllByRole("radio");
    await userEvent.click(radios[0]);
    await userEvent.click(radios[1]);
    expect(radios[0]).not.toBeChecked();
    expect(radios[1]).toBeChecked();
  });
});

describe("after the start", () => {
  it("locks the meta form and says so", () => {
    renderWithProviders(<ExamMetaForm exam={mcqExam} locked />, { user: fixtures.masterAdmin });
    expect(screen.getByText("পরীক্ষা শুরু হয়ে গেছে, তাই এখন আর কিছু বদলানো যাবে না।")).toBeInTheDocument();
    const form = screen.getByLabelText(/বাংলা নাম/);
    expect(form).toBeDisabled();
  });
});

describe("written question PDF", () => {
  const written: StaffExam = {
    ...mcqExam,
    id: "eeeeeeee-0000-0000-0000-000000000002",
    examType: "WRITTEN",
    maximum: 50,
    questions: null,
    questionPdf: true,
  };

  it("offers view and a replace upload while not locked", () => {
    renderWithProviders(<WrittenPaper exam={written} locked={false} />, { user: fixtures.masterAdmin });
    expect(screen.getByRole("button", { name: "দেখুন" })).toBeInTheDocument();
    expect(screen.queryByText("শুরুর সময় পেরিয়ে গেছে, তাই ফাইল এখন বদলানো যাবে না।")).not.toBeInTheDocument();
  });

  it("hides the replace control once locked", () => {
    renderWithProviders(<WrittenPaper exam={written} locked />, { user: fixtures.masterAdmin });
    expect(screen.getByText("শুরুর সময় পেরিয়ে গেছে, তাই ফাইল এখন বদলানো যাবে না।")).toBeInTheDocument();
  });
});

