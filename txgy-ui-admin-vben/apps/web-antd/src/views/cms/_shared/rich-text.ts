/** 将法规原文安全放入富文本编辑器，保留条号与段落。 */
export function plainTextToEditorHtml(value: string) {
  if (!value.trim()) return '';
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .split(/\r?\n/)
    .map((line) => `<p>${line || '<br>'}</p>`)
    .join('');
}

/** 审查与查重只接收可核验纯文本，不把富文本标签写入知识库。 */
export function editorHtmlToPlainText(value: string) {
  if (!value.trim()) return '';
  const container = document.createElement('div');
  container.innerHTML = value
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|h[1-6]|li|blockquote|pre|tr)>/gi, '\n');
  return (container.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}
