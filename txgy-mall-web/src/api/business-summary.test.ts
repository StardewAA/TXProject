import { describe,it,expect,vi } from 'vitest'
vi.mock('./inquiry',()=>({inquiryApi:{page:vi.fn()}}))
vi.mock('./mall',()=>({tradeApi:{getOrders:vi.fn()}}))
vi.mock('./supplier',()=>({supplierApi:{page:vi.fn()}}))
vi.mock('./supplier-trade',()=>({supplierTradeApi:{page:vi.fn(),afterSales:vi.fn()}}))
import {collectCounts,loadBusinessSummary} from './business-summary'
import {supplierApi} from './supplier'
import {supplierTradeApi} from './supplier-trade'
import {inquiryApi} from './inquiry'
import {tradeApi} from './mall'
describe('工作台真实统计',()=>{
 it('区分真实零与失败，不影响其他指标',async()=>{
  const result=await collectCounts([['zero','零',async()=>({total:0})],['ok','成功',async()=>({total:8})],['bad','失败',async()=>{throw Error('offline')}],['invalid','异常',async()=>({total:NaN})]])
  expect(result.map(x=>x.value)).toEqual([0,8,null,null])
 })
 it('销售请求固定授权店铺、对应状态与单条分页',async()=>{
  vi.mocked(supplierApi.page).mockResolvedValue({list:[],total:3});vi.mocked(supplierTradeApi.page).mockResolvedValue({list:[],total:2});vi.mocked(supplierTradeApi.afterSales).mockResolvedValue({list:[],total:1})
  expect((await loadBusinessSummary(123)).map(x=>x.value)).toEqual([3,3,2,2,1,1])
  expect(supplierApi.page).toHaveBeenCalledWith({shopId:123,pageNo:1,pageSize:1,status:1})
  expect(supplierTradeApi.afterSales).toHaveBeenCalledWith({shopId:123,pageNo:1,pageSize:1,status:30})
 })
 it('采购请求不接受客户端会员编号',async()=>{
  vi.mocked(inquiryApi.page).mockResolvedValue({list:[],total:0});vi.mocked(tradeApi.getOrders).mockResolvedValue({list:[],total:0})
  expect(await loadBusinessSummary()).toHaveLength(6)
  expect(inquiryApi.page).toHaveBeenCalledWith({pageNo:1,pageSize:1,status:2})
  expect(tradeApi.getOrders).toHaveBeenCalledWith({pageNo:1,pageSize:1,status:20})
 })
 it('非法店铺编号不查询',async()=>{await expect(loadBusinessSummary(0)).rejects.toThrow('店铺编号无效')})
})
