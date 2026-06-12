import type { HomeLayoutProps } from 'fumadocs-ui/layouts/home';
import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import Image from 'next/image';
import { siteConfig } from '@/lib/site-config';

export const docsShellClassName = 'scanner-docs-shell flex flex-col';
export const docsShellHeaderClassName = 'max-md:hidden';
export const docsLayoutContainerClassName = 'scanner-docs-layout';

const LOGO_SIZE = {
  width: 200,
  height: 40,
} as const;
const LOGO_WORDMARK_MARGIN_CLASS_NAME = 'ml-2';
const LOGO_WORDMARK_WIDTH_CLASS_NAME = 'w-[140px]';

function LogoWordmark({ className }: { className?: string }) {
  return (
    <div className={`dark:invert ${className ?? ''}`}>
      <Image
        src="/logo.svg"
        alt="Scanner Pro"
        width={LOGO_SIZE.width}
        height={LOGO_SIZE.height}
        className="block h-auto w-full"
        draggable={false}
        priority
      />
    </div>
  );
}

export function sidebarOptions(): BaseLayoutProps {
  return {
    themeSwitch: {
      enabled: false,
    },
    nav: {
      title: (
        <LogoWordmark
          className={`scanner-mobile-sidebar-logo ${LOGO_WORDMARK_MARGIN_CLASS_NAME} ${LOGO_WORDMARK_WIDTH_CLASS_NAME} md:hidden`}
        />
      ),
    },
  };
}

export function headerOptions(): HomeLayoutProps {
  return {
    githubUrl: siteConfig.repositoryUrl,
    nav: {
      title: (
        <LogoWordmark
          className={`${LOGO_WORDMARK_MARGIN_CLASS_NAME} ${LOGO_WORDMARK_WIDTH_CLASS_NAME}`}
        />
      ),
      url: '/docs',
    },
    themeSwitch: {
      enabled: true,
      mode: 'light-dark',
    },
    searchToggle: {
      enabled: true,
    },
  };
}
