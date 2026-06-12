import { absoluteUrl, siteConfig } from './site-config';

export function getSiteStructuredData() {
  const siteUrl = absoluteUrl('/');
  const logoUrl = absoluteUrl('/favicon.svg');

  const websiteId = `${siteUrl}#website`;
  const organizationId = `${siteUrl}#organization`;
  const softwareId = `${siteUrl}#software`;

  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'WebSite',
        '@id': websiteId,
        url: siteUrl,
        name: siteConfig.name,
        description: siteConfig.description,
        inLanguage: 'en',
        publisher: { '@id': organizationId },
        about: { '@id': softwareId },
      },
      {
        '@type': 'Organization',
        '@id': organizationId,
        name: siteConfig.name,
        url: siteUrl,
        logo: {
          '@type': 'ImageObject',
          url: logoUrl,
        },
        sameAs: [siteConfig.repositoryUrl, siteConfig.npmUrl],
      },
      {
        '@type': 'SoftwareSourceCode',
        '@id': softwareId,
        name: siteConfig.name,
        description: siteConfig.description,
        codeRepository: siteConfig.repositoryUrl,
        downloadUrl: siteConfig.npmUrl,
        programmingLanguage: ['TypeScript', 'Swift', 'Kotlin'],
        runtimePlatform: 'React Native',
        license: 'https://opensource.org/licenses/MIT',
        url: siteUrl,
        publisher: { '@id': organizationId },
      },
    ],
  };
}

export function serializeJsonLd(data: object): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}
