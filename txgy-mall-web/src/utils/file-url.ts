export function normalizeFileUrl(value = ''): string {
  const source = String(value || '').trim()
  const match = source.match(/^(?:https?:\/\/(?:localhost|127\.0\.0\.1|\[::1\])(?::\d+)?)?(\/(?:admin|app)-api\/infra\/file\/\d+\/(?:get|preview)\/[^?#]+)([?#].*)?$/i)
  return match ? match[1]!.replace(/^\/admin-api\//, '/app-api/') + (match[2] || '') : source
}
