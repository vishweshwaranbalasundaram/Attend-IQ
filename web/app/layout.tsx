import type { Metadata } from "next";
import CampusBackdrop from "./campus-backdrop";
import "./globals.css";

export const metadata: Metadata = {
  title: "Attend-IQ | Attendance, clearly",
  description: "Secure institutional attendance for students and staff.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body><CampusBackdrop />{children}</body>
    </html>
  );
}
