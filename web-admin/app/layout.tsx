import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Workshop ERP | Aakruti",
  description: "Furniture manufacturing and project operations",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
