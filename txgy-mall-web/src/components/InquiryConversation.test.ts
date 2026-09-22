import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import InquiryConversation from './InquiryConversation.vue'

const source = readFileSync(new URL('./InquiryConversation.vue', import.meta.url), 'utf8')

describe('询价独立沟通组件', () => {
  it('区分当前一方消息并展示可追踪时间', async () => {
    const app = createSSRApp({
      render: () => h(InquiryConversation, {
        side: 'BUYER',
        messages: [
          { id: 1, senderSide: 'BUYER', senderName: '采购方', content: '请确认交期', createTime: 1_700_000_000_000 },
          { id: 2, senderSide: 'SUPPLIER', senderName: '供应店铺', content: '预计七天', createTime: 1_700_000_100_000 },
        ],
      }),
    })
    const html = await renderToString(app)
    expect(html).toContain('class="own"')
    expect(html).toContain('请确认交期')
    expect(html).toContain('预计七天')
    expect(html).toContain('供应店铺')
  })

  it('禁用时不产生可提交的发送按钮', async () => {
    const html = await renderToString(createSSRApp({ render: () => h(InquiryConversation, { messages: [], disabled: true }) }))
    expect(html).toContain('暂无消息')
    expect(html).toMatch(/<button[^>]*type="submit"[^>]*disabled/)
  })

  it('只在父级确认服务端成功后清空草稿', () => {
    expect(source).toContain("send: [content: string, complete: (success: boolean) => void]")
    expect(source).toContain("if (success && draft.value.trim() === content) draft.value = ''")
    expect(source).not.toContain("emit('send', content)\n  draft.value = ''")
  })
})
