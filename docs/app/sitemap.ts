import type { MetadataRoute } from 'next';
import { absoluteUrl } from '@/lib/site-config';
import { source } from '@/lib/source';

export default function sitemap(): MetadataRoute.Sitemap {
  const docsPages = source.getPages().map((page) => ({
    url: absoluteUrl(page.url),
    lastModified: new Date(),
    changeFrequency: 'weekly' as const,
    priority: page.url === '/docs' ? 0.9 : 0.7,
  }));

  return [
    {
      url: absoluteUrl('/'),
      lastModified: new Date(),
      changeFrequency: 'monthly',
      priority: 1.0,
    },
    ...docsPages,
  ];
}
