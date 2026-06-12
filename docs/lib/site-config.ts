const DEFAULT_SITE_URL = 'https://hardik27111.github.io/react-native-scanner-pro';

export const siteConfig = {
  name: 'react-native-scanner-pro',
  shortName: 'Scanner Pro',
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? DEFAULT_SITE_URL),
  repositoryUrl: 'https://github.com/hardik27111/react-native-scanner-pro',
  npmUrl: 'https://www.npmjs.com/package/react-native-scanner-pro',
  description:
    'High-performance QR and barcode scanner for React Native with scan region, bounding box, and freeze frame support.',
  keywords: [
    'react-native',
    'qr-code',
    'barcode-scanner',
    'qr-scanner',
    'ml-kit',
    'apple-vision',
    'camerax',
    'on-device',
  ],
  og: {
    width: 1200,
    height: 630,
  },
} as const;

export function absoluteUrl(pathname: string): string {
  return new URL(pathname, siteConfig.metadataBase).toString();
}

export function getOgImageUrl(pageUrl: string): string {
  return pageUrl === '/' ? '/og' : `/og${pageUrl}`;
}

export function getOgImage(pageUrl: string) {
  return {
    url: getOgImageUrl(pageUrl),
    width: siteConfig.og.width,
    height: siteConfig.og.height,
    alt: `${siteConfig.name} documentation`,
  };
}
