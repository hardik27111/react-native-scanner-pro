import type { ReactNode } from 'react';

type DemoVideoProps = {
  src: string;
  caption?: string;
  children?: ReactNode;
};

function videoType(src: string) {
  if (src.endsWith('.mov')) return 'video/quicktime';
  if (src.endsWith('.webm')) return 'video/webm';
  return 'video/mp4';
}

function VideoFigure({ src, caption }: { src: string; caption?: string }) {
  return (
    <figure className="not-prose m-0">
      <div className="overflow-hidden rounded-xl border border-fd-border bg-fd-muted/40">
        <video
          autoPlay
          loop
          muted
          playsInline
          preload="metadata"
          className="block w-full"
        >
          <source src={src} type={videoType(src)} />
        </video>
      </div>
      {caption ? (
        <figcaption className="mt-2 text-center text-sm text-fd-muted-foreground">
          {caption}
        </figcaption>
      ) : null}
    </figure>
  );
}

export function DemoVideo({ src, caption, children }: DemoVideoProps) {
  if (!children) {
    return (
      <div className="not-prose my-6">
        <VideoFigure src={src} caption={caption} />
      </div>
    );
  }

  return (
    <div className="my-8 grid grid-cols-1 items-start gap-6 md:grid-cols-[minmax(0,1fr)_min(280px,38%)] md:gap-8 lg:gap-10">
      <div className="min-w-0 [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
        {children}
      </div>
      <div className="not-prose mx-auto w-full max-w-[280px] md:mx-0 md:sticky md:top-[calc(var(--fd-banner-height,3.5rem)+1.5rem)]">
        <VideoFigure src={src} caption={caption} />
      </div>
    </div>
  );
}
