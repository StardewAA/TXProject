// Historical local deployment URLs must resolve through this installation's gateway.
// This changes only the origin, never the file identity or its server-side ACL.
export function normalizeFileUrl(value = '') {
  const source = String(value || '').trim();
  const match = source.match(/^(?:https?:\/\/(?:localhost|127\.0\.0\.1|\[::1\])(?::\d+)?)?(\/(?:admin|app)-api\/infra\/file\/\d+\/(?:get|preview)\/[^?#]+)([?#].*)?$/i);
  return match ? match[1].replace(/^\/admin-api\//, '/app-api/') + (match[2] || '') : source;
}

export function normalizeRichImageUrls(html = '') {
  return String(html || '').replace(/(<img\b[^>]*\bsrc\s*=\s*)(["'])(.*?)\2/gi,
    (whole, prefix, quote, url) => prefix + quote + normalizeFileUrl(url) + quote);
}
