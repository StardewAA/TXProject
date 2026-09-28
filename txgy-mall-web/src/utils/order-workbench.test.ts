import { describe, expect, it } from 'vitest'
import type { TodoEntry } from '@/api/order-workbench'
import { orderTabs, workbenchDestination, workbenchRowEntries } from './order-workbench'
const entry = (patch: Partial<TodoEntry> = {}): TodoEntry => ({ eventId: '9007199254740995', entityType: 'ORDER', entityId: 3, orderId: 3, no: 'O3', action: 'DELIVERY', reason: '可发货', unread: true, createdTime: '', ...patch })

describe('待办行定位与状态布局', () => {
  it('主状态独立，辅助待评价不新建主订单状态', () => {
    expect(orderTabs.map(tab=>tab.value)).toEqual(['all','0','10','20','30','40'])
  })
  it('精确匹配服务端行事件，已读和其他订单不显示新标记', () => {
    const list = [entry(), entry({eventId:'2',orderId:4}), entry({eventId:'3',unread:false})]
    expect(workbenchRowEntries(list, '3').map(item=>item.eventId)).toEqual(['9007199254740995'])
  })
  it('同订单不同售后阶段按售后单标记，不能借订单ID读掉其他售后', () => {
    const list = [entry({afterSaleId:5}),entry({eventId:'2',afterSaleId:6})]
    expect(workbenchRowEntries(list, 5, true).map(item=>item.eventId)).toEqual(['9007199254740995'])
  })
  it('买家两种中心定位到同一真实详情；待评价直达评价页', () => {
    expect(workbenchDestination(entry({action:'COMMENT'}),'BUYER','/purchase')).toEqual({path:'/purchase/orders/3/comment'})
    expect(workbenchDestination(entry({action:'BUYER_RETURN',afterSaleId:9}),'BUYER','/member')).toEqual({path:'/member/after-sales',query:{openAfter:'9'}})
  })
  it('供应商仅在当前授权店铺中打开后端提供的真实单号', () => {
    expect(workbenchDestination(entry(),'SUPPLIER','/member',1)).toEqual({path:'/sales/shops/1/orders',query:{openOrder:'3'}})
    expect(workbenchDestination(entry({afterSaleId:9}),'SUPPLIER','/member',1)).toEqual({path:'/sales/shops/1/after-sales',query:{openAfter:'9'}})
  })
})
