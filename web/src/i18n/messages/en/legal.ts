import type { MessageTree } from "../types";

const legal = {
  banner: "Sample text — the final text is coming soon",
  help: {
    title: "Help and FAQ",
    searchLabel: "Search questions",
    searchPlaceholder: "For example: payment, PDF",
    noResults: "No questions match this word.",
    sections: {
      buy: {
        title: "Buying and payment",
        q1: "What do I get when I buy a package?",
        a1: "Buying a package unlocks every exam in it, MCQ and written. Exams added to the package later are included too.",
        q2: "Can I buy a single exam?",
        a2: "Yes. Buying one exam unlocks only that exam.",
      },
      live: {
        title: "Live exams",
        q1: "When does an exam start?",
        a1: "The exam page shows the date and time. If the date is not set yet, it says the date will be announced.",
        q2: "Can the exam time be extended?",
        a2: "No. You must sit the exam within its set time.",
      },
      mcq: {
        title: "MCQ",
        q1: "Can I sit an MCQ exam twice?",
        a1: "The live attempt can be taken once. After that you can sit it once as practice, but that score is not on the merit list.",
        q2: "Can I change my answers?",
        a2: "Yes, until time runs out. Each answer is saved when you choose it.",
      },
      written: {
        title: "Uploading a written script",
        q1: "How do I submit my script?",
        a1: "Submit your script as one PDF file. Photos cannot be submitted.",
        q2: "What is the file size limit?",
        a2: "Only PDF files can be submitted, up to 80 MB.",
      },
      results: {
        title: "Results and merit list",
        q1: "When can I see my written result?",
        a1: "After the teacher marks the script and the result is published. Marks are not shown before then.",
        q2: "Where is the merit list?",
        a2: "The merit list appears on the exam page once it is published.",
      },
      account: {
        title: "Account",
        q1: "What if I forget my password?",
        a1: "Use Forgot password on the login page. Enter the code from the email with a new password.",
        q2: "Can I change my phone number?",
        a2: "Yes. On My account, enter your current password to change it.",
      },
    },
    contactTitle: "Still have a question?",
    contactBody: "Contact us. The contact page has our phone and email.",
    contactLink: "Contact",
  },
  about: {
    title: "About us",
    body1: "Exam Portal is a place for students to practise and sit MCQ and written exams, and to see their results and merit lists in one place.",
    body2: "More details will be added soon.",
  },
  contact: {
    title: "Contact",
    body: "If you have a question or a problem, contact us at the details below.",
    phoneLabel: "Phone",
    phoneValue: "01700-000000",
    emailLabel: "Email",
    emailValue: "info@example.com",
    addressLabel: "Address",
    addressValue: "Dhaka, Bangladesh",
    hours: "Hours: 9 am to 6 pm",
  },
  terms: {
    title: "Terms",
    body1: "By using this site you agree to its rules. Your account is personal; do not let anyone else use it.",
    body2: "The final terms will be added soon.",
  },
  privacy: {
    title: "Privacy policy",
    body1: "We use your name, phone and email only to run your exams and your account.",
    body2: "The final privacy policy will be added soon.",
  },
  refund: {
    title: "Refund policy",
    body1: "Purchased packages and exams are digital access. Refund requests are handled by the centre through the payment provider.",
    body2: "The final refund policy will be added soon.",
  },
} satisfies MessageTree;

export default legal;
