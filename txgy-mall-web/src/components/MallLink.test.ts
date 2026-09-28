import { expect, it, vi } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter, type RouteLocationRaw } from 'vue-router'
import MallLink from './MallLink.vue'
vi.mock('@/utils/tenant-context', () => ({ entryTenantId: '9001' }))

async function render(to: RouteLocationRaw, attrs = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'platform', component: { template: '<div />' } },
    { path: '/tenants/:tenantSlug', name: 'tenant', component: { template: '<div />' } },
    { path: '/goods/:id', name: 'goods', component: { template: '<div />' } },
    { path: '/register', name: 'register', component: { template: '<div />' } },
  ] })
  await router.push('/goods/7?tenantId=9001')
  return renderToString(createSSRApp({ render: () => h(MallLink, { to, ...attrs }, () => '商品') }).use(router))
}
it('renders a real tenant-qualified href for native new-tab and copy-link actions', async () => {
  const html = await render('/goods/7', { target: '_blank', rel: 'noopener', class: 'product-card' })
  expect(html).toContain('href="/goods/7?tenantId=9001"')
  expect(html).toContain('target="_blank"');expect(html).toContain('product-card');expect(html).toContain('router-link-exact-active')
})
it('preserves named params, query and fragment', async () => {
  const html = await render({ name: 'goods', params: { id: 8 }, query: { sku: '9' }, hash: '#spec' })
  expect(html).toContain('href="/goods/8?sku=9&amp;tenantId=9001#spec"')
})
it('keeps the current tenant as the explicit registration target', async () => {
  const html = await render({ path: '/register', query: { registrationTenant: '9001' } })
  expect(html).toContain('href="/register?registrationTenant=9001&amp;tenantId=9001"')
})
it('keeps platform and tenant-slug routes stable without a tenant query', async () => {
  expect(await render('/')).toContain('href="/"')
  expect(await render({ name: 'tenant', params: { tenantSlug: 'tenant-2009' }, query: { q: '防腐' } }))
    .toContain('href="/tenants/tenant-2009?q=%E9%98%B2%E8%85%90"')
})
it.each(['/goods/7?tenantId=1', '/goods/7?tenantId=9001&tenantId=1', '//evil/goods/7', 'https://evil/goods/7', '/goods/7?tenant_id=oops'])('does not render a clickable wrong-scope href: %s', async path => {
  const html = await render(path)
  expect(html).toContain('aria-disabled="true"');expect(html).not.toContain('href=')
})
