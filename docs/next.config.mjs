import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
const config = {
  reactStrictMode: true,
  rewrites: async () => [
    {
      source: '/docs.md',
      destination: '/llms.mdx/docs',
    },
    {
      source: '/docs/:path*.md',
      destination: '/llms.mdx/docs/:path*',
    },
  ],
};

export default withMDX(config);
