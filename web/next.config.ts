import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async redirects() {
    // My admission now lives inside Offline activities (plan P15). The old address keeps working for bookmarks and old links.
    return [{ source: "/my-admission", destination: "/offline-activities", permanent: false }];
  },
};

export default nextConfig;
