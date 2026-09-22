import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const editor = readFileSync(new URL('./InquiryEditView.vue', import.meta.url), 'utf8')
const api = readFileSync(new URL('../api/inquiry.ts', import.meta.url), 'utf8')

describe('跨租户定向邀请编辑闭环', () => {
  it('通过受限目录搜索跨租户店铺并保存 SHOP 范围', () => {
    expect(api).toContain("url:path+'/invitation-options'")
    expect(api).toContain('invitedScopes?: InquiryInvitationScope[]')
    expect(editor).toContain('inquiryApi.invitationOptions({keyword:value||undefined,limit:100})')
    expect(editor).toContain("targetType:'SHOP'")
    expect(editor).toContain("invitedScopes:form.visibility==='INVITED_SHOPS'?form.invitedScopes:[]")
  })

  it('详情回显邀请范围且本租户邀请能力保持可用', () => {
    expect(editor).toContain('data.invitedScopes??data.order.invitedScopes??[]')
    expect(editor).toContain('v-model="form.invitedShopIds"')
    expect(editor).toContain('v-model="crossTenantShopKeys"')
    expect(editor).toContain('目录不展示联系方式、资质附件和库存')
  })
})
