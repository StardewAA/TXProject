export function formatMoney(value?: number | string): string {
  const cents = Number(value || 0)
  return (cents / 100).toFixed(2)
}

export function formatDate(value?: string | number): string {
  if (!value) return '-'
  if (typeof value === 'number' || /^\d{10,13}$/.test(String(value))) {
    const timestamp = Number(value) * (String(value).length === 10 ? 1000 : 1)
    const date = new Date(timestamp)
    if (Number.isNaN(date.getTime())) return '-'
    const pad = (part: number) => String(part).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

export function normalizeImage(url?: string): string {
  if (!url) return ''
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:')) return url
  const base = import.meta.env.VITE_STATIC_BASE_URL || ''
  return `${base}${url.startsWith('/') ? '' : '/'}${url}`
}

/** ERP 导入商品使用随前端发布的图片，避免第三方图床防盗链或失效。 */
export function productImage(productId?: string | number, fallback?: string): string {
  const id = Number(productId)
  if (id >= 910001 && id <= 910030) return `/product-images/${id}.jpg`
  return normalizeImage(fallback)
}

export function propertyText(properties?: unknown): string {
  if (typeof properties === 'string') return properties.trim()
  if (!Array.isArray(properties)) return ''
  return properties
    .map((item) => [item.propertyName, item.valueName].filter(Boolean).join(':'))
    .filter(Boolean)
    .join('；')
}

export function sanitizeRichHtml(source?: string): string {
  if (!source || typeof DOMParser === 'undefined') return ''
  const documentNode = new DOMParser().parseFromString(source, 'text/html')
  const allowedTags = new Set([
    'A', 'B', 'BLOCKQUOTE', 'BR', 'DIV', 'EM', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
    'HR', 'I', 'IMG', 'LI', 'OL', 'P', 'SPAN', 'STRONG', 'TABLE', 'TBODY', 'TD',
    'TH', 'THEAD', 'TR', 'U', 'UL',
  ])
  const removableTags = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'EMBED', 'FORM', 'INPUT', 'BUTTON'])
  const safeUrl = (value: string, image = false) => {
    const normalized = value.trim().toLowerCase()
    return normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('/') || normalized.startsWith('./') || (image && normalized.startsWith('data:image/'))
  }

  for (const element of Array.from(documentNode.body.querySelectorAll('*'))) {
    if (removableTags.has(element.tagName)) {
      element.remove()
      continue
    }
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      continue
    }
    for (const attribute of Array.from(element.attributes)) {
      const name = attribute.name.toLowerCase()
      const allowed = ['alt', 'colspan', 'height', 'rowspan', 'title', 'width'].includes(name)
        || (element.tagName === 'A' && ['href', 'target'].includes(name))
        || (element.tagName === 'IMG' && name === 'src')
      if (!allowed) element.removeAttribute(attribute.name)
    }
    if (element.tagName === 'A') {
      const href = element.getAttribute('href') || ''
      if (!safeUrl(href)) element.removeAttribute('href')
      element.setAttribute('rel', 'noopener noreferrer')
    }
    if (element.tagName === 'IMG') {
      const src = element.getAttribute('src') || ''
      if (!safeUrl(src, true)) element.removeAttribute('src')
      element.setAttribute('loading', 'lazy')
    }
  }
  return documentNode.body.innerHTML
}
