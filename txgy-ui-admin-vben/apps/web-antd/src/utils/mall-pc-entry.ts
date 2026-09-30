/**
 * Validate the existing PC storefront URL before opening it from the admin.
 * The current deployment address (including its existing port/path) is kept as-is.
 */
export function resolveMallPcUrl(value: string | undefined): string | null {
  if (!value?.trim()) return null;
  try {
    const url = new URL(value.trim());
    const hasCredentialQuery = [...url.searchParams.keys()].some((key) =>
      /(token|secret|password|authorization|credential)/i.test(key),
    );
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || hasCredentialQuery || url.hash) {
      return null;
    }
    return url.href;
  } catch {
    return null;
  }
}
