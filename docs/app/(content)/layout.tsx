import { HomeLayout } from 'fumadocs-ui/layouts/home';
import type { ReactNode } from 'react';
import {
  docsShellClassName,
  docsShellHeaderClassName,
  headerOptions,
} from '@/lib/layout.shared';

export default function ContentLayout({ children }: { children: ReactNode }) {
  return (
    <div className={docsShellClassName}>
      <HomeLayout {...headerOptions()} className={docsShellHeaderClassName} />
      {children}
    </div>
  );
}
