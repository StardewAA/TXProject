import { describe, expect, it } from 'vitest'
import { inquiryDraftSnapshot } from './inquiry-draft'
import type { Inquiry, InquiryItem } from '@/api/inquiry'
describe('询价未保存修改识别', () => {
  const form: Inquiry = {requestKey:'qa',title:'需求',contactName:'QA',contactMobile:'123',address:'地址',remark:'',deadline:123}
  const item: InquiryItem = {code:'',name:'板材',category:'保温',specification:'50mm',quantity:1,unit:'块',remark:''}
  it('服务器元数据不触发修改', () => {
    expect(inquiryDraftSnapshot({...form,id:99,no:'XJ1',status:0},[item])).toBe(inquiryDraftSnapshot(form,[item]))
  })
  it('表单、数量、图片与行增删均触发修改', () => {
    const before=inquiryDraftSnapshot(form,[item])
    expect(inquiryDraftSnapshot({...form,title:'新需求'},[item])).not.toBe(before)
    expect(inquiryDraftSnapshot(form,[{...item,quantity:2}])).not.toBe(before)
    expect(inquiryDraftSnapshot(form,[{...item,picUrl:'https://example.com/a.png'}])).not.toBe(before)
    expect(inquiryDraftSnapshot(form,[])).not.toBe(before)
    expect(inquiryDraftSnapshot(form,[item,item])).not.toBe(before)
    expect(inquiryDraftSnapshot({...form,visibility:'INVITED_SHOPS',invitedShopIds:[2]},[item])).not.toBe(before)
    expect(inquiryDraftSnapshot({...form,visibility:'INVITED_SHOPS',invitedScopes:[{targetType:'SHOP',targetTenantId:2009,targetEnterpriseId:301,targetShopId:401}]},[item])).not.toBe(before)
    expect(inquiryDraftSnapshot({...form,deliveryAreaId:110105},[item])).not.toBe(before)
  })
  it('改回原值不再视为修改，空图片表示一致', () => {
    expect(inquiryDraftSnapshot(form,[{...item,picUrl:null}])).toBe(inquiryDraftSnapshot(form,[{...item,picUrl:''}]))
    expect(inquiryDraftSnapshot({...form,title:form.title},[{...item}])).toBe(inquiryDraftSnapshot(form,[item]))
    expect(inquiryDraftSnapshot({...form,invitedShopIds:[2,1]},[item])).toBe(inquiryDraftSnapshot({...form,invitedShopIds:[1,2]},[item]))
    const a={targetType:'SHOP' as const,targetTenantId:2009,targetEnterpriseId:301,targetShopId:401}
    const b={targetType:'SHOP' as const,targetTenantId:2010,targetEnterpriseId:302,targetShopId:402}
    expect(inquiryDraftSnapshot({...form,invitedScopes:[b,a]},[item])).toBe(inquiryDraftSnapshot({...form,invitedScopes:[a,b]},[item]))
  })
})
