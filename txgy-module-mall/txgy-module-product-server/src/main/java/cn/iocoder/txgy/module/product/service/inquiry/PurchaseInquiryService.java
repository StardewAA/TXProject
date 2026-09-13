package cn.iocoder.txgy.module.product.service.inquiry;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.*;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class PurchaseInquiryService {
 @Resource private InquiryMapper mapper;
 @Resource private InquiryItemMapper itemMapper;
 @Resource private EnterpriseMembershipService membership;
 @Resource private ProductSkuMapper skuMapper;
 @Resource private ProductSpuMapper spuMapper;
 public InquiryDO owned(Long memberId, Long id, boolean lock) {
  var q=new LambdaQueryWrapperX<InquiryDO>().eq(InquiryDO::getId,id).eq(InquiryDO::getMemberId,memberId);
  if(lock) q.last("FOR UPDATE");
  var row=mapper.selectOne(q);
  if(row==null) throw invalid("询价单不存在或无权访问");
  return row;
 }
 public Map<String,Object> detail(Long memberId, Long id) {
  membership.requireBuyer(memberId);
  var row=owned(memberId,id,false);
  row.setStatus(effectiveStatus(row));
  return Map.of("order",row,"items",itemMapper.selectList(InquiryItemDO::getInquiryId,id));
 }
 public static int effectiveStatus(InquiryDO row) {
  return row.getStatus()==1 && row.getDeadline()<=System.currentTimeMillis()?2:row.getStatus();
 }
 public PageResult<InquiryDO> page(Long memberId, PageParam page, String keyword, Integer status) {
  membership.requireBuyer(memberId);
  var q=new LambdaQueryWrapperX<InquiryDO>().eq(InquiryDO::getMemberId,memberId)
   .likeIfPresent(InquiryDO::getTitle,keyword).orderByDesc(InquiryDO::getId);
  if(status!=null) {
   if(status==2) q.eq(InquiryDO::getStatus,1).le(InquiryDO::getDeadline,System.currentTimeMillis());
   else if(status==1) q.eq(InquiryDO::getStatus,1).gt(InquiryDO::getDeadline,System.currentTimeMillis());
   else q.eq(InquiryDO::getStatus,status);
  }
  var result=mapper.selectPage(page,q);
  result.getList().forEach(r->r.setStatus(effectiveStatus(r)));
  return result;
 }
 @Transactional(rollbackFor=Exception.class)
 public Long save(Long memberId, InquirySaveReqVO req) {
  var company=membership.requireBuyer(memberId);
  if(req.getDeadline()<=System.currentTimeMillis()) throw invalid("报价截止时间须晚于当前时间");
  if(req.getExpectedDate()!=null && req.getExpectedDate()<req.getDeadline()) throw invalid("期望到货日期不能早于报价截止时间");
  InquiryDO row;
  if(req.getId()!=null) {
   row=owned(memberId,req.getId(),true);
   if(row.getStatus()!=0) throw invalid("仅草稿可以修改");
  } else {
   var previous=mapper.selectOne(new LambdaQueryWrapperX<InquiryDO>()
    .eq(InquiryDO::getMemberId,memberId).eq(InquiryDO::getRequestKey,req.getRequestKey()));
   if(previous!=null) return previous.getId();
   row=new InquiryDO(); row.setMemberId(memberId); row.setEnterpriseId(company.getEnterpriseId());
   row.setRequestKey(req.getRequestKey()); row.setStatus(0); row.setErpStatus("NOT_CONNECTED");
  }
  row.setTitle(req.getTitle()); row.setContactName(req.getContactName()); row.setContactMobile(req.getContactMobile());
  row.setAddress(req.getAddress()); row.setRemark(req.getRemark()); row.setDeadline(req.getDeadline()); row.setExpectedDate(req.getExpectedDate());
  if(row.getId()==null) mapper.insert(row); else mapper.updateById(row);
  itemMapper.delete(new LambdaQueryWrapperX<InquiryItemDO>().eq(InquiryItemDO::getInquiryId,row.getId()));
  for(var input:req.getItems()) {
   if(input.getPicUrl()!=null && !input.getPicUrl().isBlank()) {
    cn.iocoder.txgy.module.product.service.supplier.SupplierProductService.validateImageUrl(input.getPicUrl());
   }
   var item=BeanUtils.toBean(input,InquiryItemDO.class);
   if(input.getSkuId()!=null) {
    var sku=skuMapper.selectById(input.getSkuId());
    var spu=sku==null?null:spuMapper.selectById(sku.getSpuId());
    if(spu==null || !Integer.valueOf(1).equals(spu.getStatus())) throw invalid("所选商品已失效，请重新选取");
    item.setName(spu.getName()); item.setCode(sku.getBarCode());
   }
   item.setInquiryId(row.getId()); itemMapper.insert(item);
  }
  return row.getId();
 }
 @Transactional(rollbackFor=Exception.class)
 public void submit(Long memberId, Long id) {
  membership.requireBuyer(memberId); var row=owned(memberId,id,true);
  if(row.getStatus()==1) return;
  if(row.getStatus()!=0 || row.getDeadline()<=System.currentTimeMillis()) throw invalid("仅未过期草稿可以提交");
  if(itemMapper.selectList(InquiryItemDO::getInquiryId,id).isEmpty()) throw invalid("请至少添加一项物品");
  row.setStatus(1); row.setNo("XJ"+UUID.randomUUID().toString().replace("-","").toUpperCase());
  mapper.updateById(row);
 }
 @Transactional(rollbackFor=Exception.class)
 public void close(Long memberId, Long id) {
  membership.requireBuyer(memberId); var row=owned(memberId,id,true);
  if(row.getStatus()==3) return;
  row.setStatus(3); mapper.updateById(row);
 }
}
