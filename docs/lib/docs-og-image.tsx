import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { notFound } from 'next/navigation';
import { ImageResponse } from 'next/og';
import { siteConfig } from '@/lib/site-config';
import { source } from '@/lib/source';

const logoSvg = readFileSync(join(process.cwd(), 'public', 'logo.svg'), 'utf-8')
  .replaceAll('#0a0a0a', '#ffffff')
  .replaceAll('fill="#0a0a0a"', 'fill="#ffffff"')
  .replaceAll('stroke="#0a0a0a"', 'stroke="#ffffff"');
const logoBase64 = `data:image/svg+xml;base64,${Buffer.from(logoSvg).toString('base64')}`;

export const ogImageAlt = `${siteConfig.name} documentation`;
export const ogImageSize = {
  width: siteConfig.og.width,
  height: siteConfig.og.height,
} as const;
export const ogImageContentType = 'image/png';

const ACCENT = '#22c55e';

function ScannerOgImage({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  const hasDescription = description != null && description.length > 0;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        width: '100%',
        height: '100%',
        backgroundColor: '#0a0a0a',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          width: '100%',
          height: '100%',
          padding: 48,
        }}
      >
        <img
          src={logoBase64}
          alt={siteConfig.shortName}
          height={32}
          style={{ height: 32, opacity: 0.85 }}
        />

        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <p
            style={{
              display: '-webkit-box',
              WebkitLineClamp: hasDescription ? 2 : 3,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              color: '#ffffff',
              fontSize: 72,
              fontWeight: 700,
              lineHeight: 1.15,
              wordBreak: 'break-word',
              margin: 0,
            }}
          >
            {title}
          </p>
          {hasDescription ? (
            <p
              style={{
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                color: 'rgba(255,255,255,0.55)',
                fontSize: 36,
                fontWeight: 400,
                marginTop: 16,
                marginBottom: 0,
                lineHeight: 1.4,
              }}
            >
              {description}
            </p>
          ) : null}
          <div
            style={{
              display: 'flex',
              marginTop: 40,
              width: '100%',
              height: 4,
              borderRadius: 999,
              backgroundColor: ACCENT,
              opacity: 0.9,
            }}
          />
        </div>
      </div>
    </div>
  );
}

function renderOgImageResponse(title: string, description?: string) {
  return new ImageResponse(
    <ScannerOgImage title={title} description={description} />,
    ogImageSize,
  );
}

export function renderOpenGraphImage(slug?: string[]) {
  if (slug == null || slug.length === 0) {
    return renderOgImageResponse(siteConfig.shortName, siteConfig.description);
  }

  const [section, ...rest] = slug;
  if (section !== 'docs') notFound();

  const page = source.getPage(rest.length > 0 ? rest : undefined);
  if (!page) notFound();

  return renderOgImageResponse(
    String(page.data.title),
    page.data.description ?? undefined,
  );
}
