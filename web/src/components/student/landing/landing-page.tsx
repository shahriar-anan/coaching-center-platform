import { Categories } from "@/components/student/landing/categories";
import { Closing } from "@/components/student/landing/closing";
import { Contact } from "@/components/student/landing/contact";
import { Featured } from "@/components/student/landing/featured";
import { Hero } from "@/components/student/landing/hero";
import { Numbers } from "@/components/student/landing/numbers";
import { Steps } from "@/components/student/landing/steps";
import { Upcoming } from "@/components/student/landing/upcoming";

/**
 * The home page for a visitor who is not a signed-in student. The shell draws
 * it full width (see StudentShell). The order follows the old site's home page:
 * banner, numbers, exam categories, packages. The old site's course categories
 * are left out; this site sells exams only. Each section hides itself when it has no data.
 */
export function LandingPage() {
  return (
    <div className="flex flex-col">
      <Hero />
      <Numbers />
      <Categories />
      <Featured />
      <Upcoming />
      <Steps />
      <Contact />
      <Closing />
    </div>
  );
}
