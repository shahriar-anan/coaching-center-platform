import { Batches } from "@/components/student/landing/batches";
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
 * it full width (see StudentShell). Each section hides itself when it has no data.
 */
export function LandingPage() {
  return (
    <div className="flex flex-col">
      <Hero />
      <Batches />
      <Numbers />
      <Categories />
      <Upcoming />
      <Featured />
      <Steps />
      <Contact />
      <Closing />
    </div>
  );
}
