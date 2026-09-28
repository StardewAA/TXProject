import { describe, expect, it } from 'vitest'
import type { InquiryItem, ImportRow } from '@/api/inquiry'
import { planInquiryImport } from './inquiry-import'
const item = (patch: Partial<InquiryItem> = {}): InquiryItem => ({ name:'保温板',code:'001',category:'保温材料',specification:'50mm',quantity:2,unit:'块',remark:'',...patch })
const row = (patch: Partial<InquiryItem> = {}, rowNumber = 2): ImportRow => {
  const value=item(patch)
  return { rowNumber,item:{...value,quantity:String(value.quantity)},skuId:value.skuId,matchStatus:'手工确认',errors:[],warnings:[] }
}
describe('询价导入防重计划',()=>{
 it('只有图片的行不是空白占位，导入不丢图',()=>{const photo=item({name:'',code:'',category:'',specification:'',quantity:1,unit:'',picUrl:'https://example.com/a.png'});expect(planInquiryImport([photo],[row()],'skip').existingCount).toBe(1);expect(photo.picUrl).toBe('https://example.com/a.png')})
 it('默认跳过已有重复项，不改变既有数量',()=>{const old=item();const result=planInquiryImport([old],[row()], 'skip');expect(result.additions).toEqual([]);expect(result.skippedCount).toBe(1);expect(old.quantity).toBe(2)})
 it('文件内重复仅保留第一项，标注源行',()=>{const result=planInquiryImport([],[row(),row({},4)],'skip');expect(result.additions).toHaveLength(1);expect(result.rows[1]!.duplicate).toBe('与Excel第2行重复')})
 it('保留全部不合并数量',()=>{const result=planInquiryImport([item()],[row({quantity:3})],'keep');expect(result.total).toBe(2);expect(result.additions[0]!.quantity).toBe(3);expect(result.rows[0]!.different).toBe(true)})
 it('同SKU名称变化仍能识别，不同规格单位分类不推断相同',()=>{expect(planInquiryImport([item({skuId:9})],[row({skuId:9,name:'新名称'})],'skip').skippedCount).toBe(1);for(const patch of [{unit:'平方米'},{specification:'80mm'},{category:'其他'}])expect(planInquiryImport([item()],[row(patch)],'skip').skippedCount).toBe(0)})
 it('首尾空白识别重复，不修改导入原值',()=>{const result=planInquiryImport([item()],[row({name:' 保温板 '})],'skip');expect(result.skippedCount).toBe(1);expect(result.rows[0]!.item.name).toBe(' 保温板 ')})
 it('分隔符不能造成错误碰撞',()=>{expect(planInquiryImport([item({code:'a|b',name:'c'})],[row({code:'a',name:'b|c'})],'skip').skippedCount).toBe(0)})
 it('错误行即使重复也不能静默丢弃，非法数值阻断',()=>{const invalid=row();invalid.errors=['分类无效'];expect(planInquiryImport([item()],[invalid],'skip').errorCount).toBe(1);const bad=row();bad.item.quantity='Infinity';expect(planInquiryImport([],[bad],'keep').errorCount).toBe(1)})
 it('忽略空白占位行且按策略计算200行上限',()=>{const blank=item({name:'',code:'',category:'',specification:'',quantity:1,unit:''});expect(planInquiryImport([blank],[row()],'skip').total).toBe(1);const existing=Array.from({length:200},(_,i)=>item({code:String(i)}));expect(planInquiryImport(existing,[row({code:'0'})],'skip').total).toBe(200);expect(planInquiryImport(existing,[row({code:'0'})],'keep').total).toBe(201)})
})
