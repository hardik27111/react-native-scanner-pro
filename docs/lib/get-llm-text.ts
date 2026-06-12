import { source } from '@/lib/source';

type DocPage = NonNullable<ReturnType<typeof source.getPage>>;

export async function getLLMText(page: DocPage) {
  const processed = await page.data.getText('processed');

  return `# ${page.data.title} (${page.url})

${processed}`;
}
