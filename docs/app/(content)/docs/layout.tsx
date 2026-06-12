import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import type { ReactNode } from 'react';
import {
  docsLayoutContainerClassName,
  sidebarOptions,
} from '@/lib/layout.shared';
import { source } from '@/lib/source';

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <DocsLayout
      tree={source.pageTree}
      {...sidebarOptions()}
      containerProps={{
        className: docsLayoutContainerClassName,
      }}
      sidebar={{
        collapsible: false,
        defaultOpenLevel: 1,
      }}
      searchToggle={{ components: { lg: false } }}
    >
      {children}
    </DocsLayout>
  );
}
