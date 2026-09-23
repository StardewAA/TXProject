package cn.iocoder.txgy.module.product.service.inquiry;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.ip.core.Area;
import cn.iocoder.txgy.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryInvitationScopeReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryInvitationScopeRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryInvitationOptionRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySupplierShopOptionRespVO;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalCatalogMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class PurchaseInquiryService {
 @Resource private InquiryMapper mapper;
 @Resource private InquiryItemMapper itemMapper;
 @Resource private InquiryQuoteMapper quoteMapper;
 @Resource private InquiryInvitedShopMapper invitedShopMapper;
 @Resource private InquiryInvitedScopeMapper invitedScopeMapper;
 @Resource private EnterpriseMembershipService membership;
 @Resource private ProductSkuMapper skuMapper;
 @Resource private ProductSpuMapper spuMapper;
 @Resource private MallPortalCatalogMapper portalCatalogMapper;
 @Resource private ProductShopMapper shopMapper;
 @Resource private ProductEnterpriseMapper enterpriseMapper;
 public static final String VISIBILITY_TENANT_PUBLIC="TENANT_PUBLIC";
 public static final String VISIBILITY_PLATFORM_PUBLIC="PLATFORM_PUBLIC";
 public static final String VISIBILITY_INVITED_SHOPS="INVITED_SHOPS";
 public static final String INVITATION_TARGET_TENANT="TENANT";
 public static final String INVITATION_TARGET_ENTERPRISE="ENTERPRISE";
 public static final String INVITATION_TARGET_SHOP="SHOP";
 public InquiryDO owned(Long memberId, Long id, boolean lock) {
  var company=membership.requireBuyer(memberId);
  return ownedByEnterprise(company.getEnterpriseId(),id,lock);
 }
 private InquiryDO ownedByEnterprise(Long enterpriseId, Long id, boolean lock) {
  var q=new LambdaQueryWrapperX<InquiryDO>().eq(InquiryDO::getId,id)
   .eq(InquiryDO::getEnterpriseId,enterpriseId);
  if(lock) q.last("FOR UPDATE");
  var row=mapper.selectOne(q);
  // 显式复核可防止自定义 Mapper/缓存绕过查询条件，也覆盖账号改绑企业的场景。
  if(row==null || !Objects.equals(row.getEnterpriseId(),enterpriseId))
   throw invalid("询价单不存在或企业归属已变化");
  return row;
 }
 public Detail detail(Long memberId, Long id) {
  var row=owned(memberId,id,false);
  row.setStatus(effectiveStatus(row));
  var invitedShopIds=invitedShopMapper.selectList(new LambdaQueryWrapperX<InquiryInvitedShopDO>()
    .eq(InquiryInvitedShopDO::getInquiryId,id).orderByAsc(InquiryInvitedShopDO::getShopId)).stream()
   .map(InquiryInvitedShopDO::getShopId).toList();
  var invitedScopes=invitedScopeMapper.selectActiveByInquiryId(id).stream()
   .map(scope->new InquiryInvitationScopeRespVO(scope.getTargetType(),scope.getTargetTenantId(),
    scope.getTargetEnterpriseId(),scope.getTargetShopId())).toList();
  return new Detail(row,itemMapper.selectList(InquiryItemDO::getInquiryId,id),invitedShopIds,invitedScopes);
 }
 public record Detail(InquiryDO order,List<InquiryItemDO> items,List<Long> invitedShopIds,
                      List<InquiryInvitationScopeRespVO> invitedScopes) {}
 public static int effectiveStatus(InquiryDO row) {
  return row.getStatus()==1 && row.getDeadline()<=System.currentTimeMillis()?2:row.getStatus();
 }
 public PageResult<InquiryDO> page(Long memberId, PageParam page, String keyword, Integer status) {
  var company=membership.requireBuyer(memberId);
  var q=new LambdaQueryWrapperX<InquiryDO>().eq(InquiryDO::getEnterpriseId,company.getEnterpriseId())
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
  requireWritableTenant();
  var company=membership.requireBuyer(memberId);
  String visibility=normalizeVisibility(req.getVisibility());
  DeliveryRegion deliveryRegion=deliveryRegion(req.getDeliveryAreaId(),visibility);
  if(req.getDeadline()<=System.currentTimeMillis()) throw invalid("报价截止时间须晚于当前时间");
  if(req.getExpectedDate()!=null && req.getExpectedDate()<req.getDeadline()) throw invalid("期望到货日期不能早于报价截止时间");
  InquiryDO row;
  if(req.getId()!=null) {
   row=ownedByEnterprise(company.getEnterpriseId(),req.getId(),true);
   if(row.getStatus()!=0) throw invalid("仅草稿可以修改");
   if(!Objects.equals(row.getMemberId(),memberId)) throw invalid("询价草稿仅创建人可以修改");
  } else {
    var previous=findByRequestKey(memberId,req.getRequestKey(),false);
    if(previous!=null) {
     if(!Objects.equals(previous.getEnterpriseId(),company.getEnterpriseId()))
      throw invalid("询价幂等键属于原企业，请使用新的请求标识");
    return previous.getId();
   }
   row=new InquiryDO(); row.setMemberId(memberId); row.setEnterpriseId(company.getEnterpriseId());
   // erpStatus 仅为历史库表兼容列。第 21 节新询价不再创建 ERP 待接入状态；
   // 报价成交后的真实库存及出库由 WMS 直连链路在下单/履约时复核。
   row.setRequestKey(req.getRequestKey()); row.setStatus(0); row.setErpStatus("WMS_DIRECT");
  }
  row.setTitle(req.getTitle()); row.setContactName(req.getContactName()); row.setContactMobile(req.getContactMobile());
  row.setDeliveryAreaId(deliveryRegion.areaId()); row.setDeliveryCityId(deliveryRegion.cityId());
  row.setDeliveryProvinceId(deliveryRegion.provinceId());
  row.setAddress(req.getAddress()); row.setRemark(req.getRemark()); row.setDeadline(req.getDeadline()); row.setExpectedDate(req.getExpectedDate());
  row.setVisibility(visibility);
   if(row.getId()==null) {
    try {
     mapper.insert(row);
    } catch (DuplicateKeyException ex) {
     // FOR UPDATE 使用当前读，可在 RR 隔离级别看到刚刚赢得唯一键竞争并已提交的记录。
     var winner=findByRequestKey(memberId,req.getRequestKey(),true);
     if(winner==null) throw ex;
     if(!Objects.equals(winner.getEnterpriseId(),company.getEnterpriseId()))
      throw invalid("询价幂等键属于原企业，请使用新的请求标识");
     return winner.getId();
    }
   } else mapper.updateById(row);
  saveInvitations(row.getId(),company.getEnterpriseId(),visibility,req.getInvitedShopIds(),req.getInvitedScopes());
  itemMapper.delete(new LambdaQueryWrapperX<InquiryItemDO>().eq(InquiryItemDO::getInquiryId,row.getId()));
  for(var input:req.getItems()) {
   if(input.getPicUrl()!=null && !input.getPicUrl().isBlank()) {
    cn.iocoder.txgy.module.product.service.supplier.SupplierProductService.validateImageUrl(input.getPicUrl());
   }
   var item=BeanUtils.toBean(input,InquiryItemDO.class);
   if(input.getSkuId()!=null) {
    var sku=skuMapper.selectById(input.getSkuId());
    var spu=sku==null?null:spuMapper.selectById(sku.getSpuId());
    if(spu!=null && Integer.valueOf(1).equals(spu.getStatus())) {
     item.setName(spu.getName()); item.setCode(sku.getBarCode());
    } else {
     // 平台询价商品可来自其他正式租户；仅通过跨租户公开目录 Mapper
     // 接受仍开放、审核通过、公开且有库存/售价的 SKU，绝不信任前端商品快照。
     var publicSku=portalCatalogMapper.selectPublicSkuById(input.getSkuId());
     if(publicSku==null) throw invalid("所选商品已失效，请重新选取");
     item.setName(publicSku.getSpuName()); item.setCode(publicSku.getBarCode());
    }
   }
   item.setInquiryId(row.getId()); itemMapper.insert(item);
  }
  return row.getId();
 }

 private static DeliveryRegion deliveryRegion(Integer areaId,String visibility) {
  if(areaId==null) {
   if(VISIBILITY_PLATFORM_PUBLIC.equals(visibility)) throw invalid("平台公开询价必须选择交付区县");
   return new DeliveryRegion(null,null,null);
  }
  Area area=AreaUtils.getArea(areaId);
  if(area==null || !Objects.equals(area.getType(),AreaTypeEnum.DISTRICT.getType()))
   throw invalid("请选择有效的交付区县");
  Integer cityId=AreaUtils.getParentIdByType(areaId,AreaTypeEnum.CITY);
  Integer provinceId=AreaUtils.getParentIdByType(areaId,AreaTypeEnum.PROVINCE);
  if(cityId==null || provinceId==null) throw invalid("交付区县不在可用地区目录中");
  return new DeliveryRegion(areaId,cityId,provinceId);
 }

 private record DeliveryRegion(Integer areaId,Integer cityId,Integer provinceId) {}
 @Transactional(rollbackFor=Exception.class)
 public void submit(Long memberId, Long id) {
  requireWritableTenant();
  var row=owned(memberId,id,true);
  if(row.getStatus()==1) return;
  if(row.getStatus()!=0 || row.getDeadline()<=System.currentTimeMillis()) throw invalid("仅未过期草稿可以提交");
  if(!Objects.equals(row.getMemberId(),memberId)) throw invalid("询价草稿仅创建人可以提交");
  if(VISIBILITY_PLATFORM_PUBLIC.equals(normalizeVisibility(row.getVisibility()))) {
   DeliveryRegion storedRegion=deliveryRegion(row.getDeliveryAreaId(),row.getVisibility());
   if(!Objects.equals(storedRegion.cityId(),row.getDeliveryCityId())
     || !Objects.equals(storedRegion.provinceId(),row.getDeliveryProvinceId()))
    throw invalid("平台公开询价的交付地区不完整，请重新保存草稿");
  }
  var items=itemMapper.selectList(InquiryItemDO::getInquiryId,id);
  if(items.isEmpty()) throw invalid("请至少添加一项物品");
  validateStoredInvitations(row);
  requireNoPublicContacts(row,items);
  row.setStatus(1); row.setNo("XJ"+UUID.randomUUID().toString().replace("-","").toUpperCase());
  mapper.updateById(row);
 }

 public List<InquirySupplierShopOptionRespVO> supplierShopOptions(Long memberId) {
  var buyer=membership.requireBuyer(memberId);
  var shops=shopMapper.selectList(new LambdaQueryWrapperX<ProductShopDO>()
   .eq(ProductShopDO::getStatus,0).ne(ProductShopDO::getEnterpriseId,buyer.getEnterpriseId())
   .orderByAsc(ProductShopDO::getName));
  if(shops.isEmpty()) return List.of();
  Set<Long> enterpriseIds=shops.stream().map(ProductShopDO::getEnterpriseId).filter(Objects::nonNull)
   .collect(java.util.stream.Collectors.toSet());
  Map<Long,ProductEnterpriseDO> enterprises=enterpriseIds.isEmpty()?Map.of():enterpriseMapper.selectBatchIds(enterpriseIds)
   .stream().filter(e->Integer.valueOf(1).equals(e.getAuditStatus()))
   .collect(java.util.stream.Collectors.toMap(ProductEnterpriseDO::getId,e->e));
  return shops.stream().filter(shop->enterprises.containsKey(shop.getEnterpriseId())).map(shop->{
   var vo=new InquirySupplierShopOptionRespVO(); var enterprise=enterprises.get(shop.getEnterpriseId());
   vo.setId(shop.getId()); vo.setEnterpriseId(shop.getEnterpriseId()); vo.setName(shop.getName());
   vo.setEnterpriseName(enterprise.getName()); vo.setLogoUrl(shop.getLogoUrl()); return vo;
  }).toList();
 }

 public List<InquiryInvitationOptionRespVO> invitationOptions(Long memberId,String keyword,Integer limit) {
  membership.requireBuyer(memberId);
  String normalizedKeyword=keyword==null?null:keyword.trim();
  if(normalizedKeyword!=null&&normalizedKeyword.isEmpty()) normalizedKeyword=null;
  if(normalizedKeyword!=null&&normalizedKeyword.length()>50) throw invalid("邀请目录关键词不能超过 50 个字符");
  int pageSize=limit==null?50:limit;
  if(pageSize<1||pageSize>100) throw invalid("邀请目录每次只能查询 1 至 100 条");
  return invitedScopeMapper.selectEligibleShopOptions(TenantContextHolder.getRequiredTenantId(),
   normalizedKeyword,pageSize).stream().map(row->{
    var option=new InquiryInvitationOptionRespVO(); option.setTenantId(row.getTenantId());
    option.setTenantName(row.getTenantName()); option.setEnterpriseId(row.getEnterpriseId());
    option.setEnterpriseName(row.getEnterpriseName()); option.setShopId(row.getShopId());
    option.setShopName(row.getShopName()); return option;
   }).toList();
 }

 private void saveInvitations(Long inquiryId,Long buyerEnterpriseId,String visibility,List<Long> requestedShopIds,
                              List<InquiryInvitationScopeReqVO> requestedScopes) {
  Long sourceTenantId=TenantContextHolder.getRequiredTenantId();
  if(Set.of(VISIBILITY_TENANT_PUBLIC,VISIBILITY_PLATFORM_PUBLIC).contains(visibility)) {
   if((requestedShopIds!=null&&!requestedShopIds.isEmpty())
     ||(requestedScopes!=null&&!requestedScopes.isEmpty())) throw invalid("公开询价不能同时指定邀请范围");
   invitedShopMapper.deletePhysicallyByInquiryId(sourceTenantId,inquiryId);
   invitedScopeMapper.deactivateByInquiryId(inquiryId);
   return;
  }
  LinkedHashSet<Long> ids=new LinkedHashSet<>(Objects.requireNonNullElse(requestedShopIds,List.of()));
  if(ids.size()>100) throw invalid("同租户邀请店铺不能超过 100 家");
  if(!ids.isEmpty()) {
   List<ProductShopDO> shops=shopMapper.selectBatchIds(ids);
   if(shops.size()!=ids.size()) throw invalid("邀请店铺不存在或不属于当前租户");
   Set<Long> enterpriseIds=new HashSet<>();
   for(var shop:shops) {
    if(!Integer.valueOf(0).equals(shop.getStatus())||Objects.equals(shop.getEnterpriseId(),buyerEnterpriseId))
     throw invalid("只能邀请其他企业的启用店铺");
    enterpriseIds.add(shop.getEnterpriseId());
   }
   long approved=enterpriseMapper.selectCount(new LambdaQueryWrapperX<ProductEnterpriseDO>()
    .in(ProductEnterpriseDO::getId,enterpriseIds).eq(ProductEnterpriseDO::getAuditStatus,1));
   if(approved!=enterpriseIds.size()) throw invalid("邀请店铺所属企业资质已失效");
  }
  List<InvitationScopeKey> scopes=validateInvitationScopes(sourceTenantId,requestedScopes);
  if(ids.isEmpty()&&scopes.isEmpty()) throw invalid("定向询价至少选择一家本租户店铺或一个跨租户邀请范围");
  invitedShopMapper.deletePhysicallyByInquiryId(sourceTenantId,inquiryId);
  for(Long shopId:ids) {
   var invitation=new InquiryInvitedShopDO(); invitation.setInquiryId(inquiryId); invitation.setShopId(shopId);
   invitedShopMapper.insert(invitation);
  }
  replaceInvitationScopes(inquiryId,scopes);
 }

 private void validateStoredInvitations(InquiryDO inquiry) {
  if(Set.of(VISIBILITY_TENANT_PUBLIC,VISIBILITY_PLATFORM_PUBLIC)
    .contains(normalizeVisibility(inquiry.getVisibility()))) return;
  List<Long> ids=invitedShopMapper.selectList(new LambdaQueryWrapperX<InquiryInvitedShopDO>()
    .eq(InquiryInvitedShopDO::getInquiryId,inquiry.getId())).stream().map(InquiryInvitedShopDO::getShopId).toList();
  if(!ids.isEmpty()) {
   List<ProductShopDO> shops=shopMapper.selectBatchIds(ids);
   if(shops.size()!=ids.size()||shops.stream().anyMatch(shop->!Integer.valueOf(0).equals(shop.getStatus())
    ||Objects.equals(shop.getEnterpriseId(),inquiry.getEnterpriseId()))) throw invalid("邀请店铺已失效，请修改询价后重试");
   Set<Long> enterpriseIds=shops.stream().map(ProductShopDO::getEnterpriseId).collect(java.util.stream.Collectors.toSet());
   long approved=enterpriseMapper.selectCount(new LambdaQueryWrapperX<ProductEnterpriseDO>()
    .in(ProductEnterpriseDO::getId,enterpriseIds).eq(ProductEnterpriseDO::getAuditStatus,1));
   if(approved!=enterpriseIds.size()) throw invalid("邀请店铺所属企业资质已失效");
  }
  List<InquiryInvitedScopeDO> storedScopes=invitedScopeMapper.selectActiveByInquiryId(inquiry.getId());
  List<InquiryInvitationScopeReqVO> requestedScopes=storedScopes.stream().map(scope->{
   var request=new InquiryInvitationScopeReqVO(); request.setTargetType(scope.getTargetType());
   request.setTargetTenantId(scope.getTargetTenantId()); request.setTargetEnterpriseId(scope.getTargetEnterpriseId());
   request.setTargetShopId(scope.getTargetShopId()); return request;
  }).toList();
  validateInvitationScopes(TenantContextHolder.getRequiredTenantId(),requestedScopes);
  if(ids.isEmpty()&&storedScopes.isEmpty())
   throw invalid("定向询价至少选择一家本租户店铺或一个跨租户邀请范围");
 }

 private List<InvitationScopeKey> validateInvitationScopes(Long sourceTenantId,
                                                            List<InquiryInvitationScopeReqVO> requestedScopes) {
  List<InquiryInvitationScopeReqVO> requests=Objects.requireNonNullElse(requestedScopes,List.of());
  if(requests.size()>100) throw invalid("跨租户邀请范围不能超过 100 个");
  if(requests.isEmpty()) return List.of();
  if(invitedScopeMapper.selectEligibleTarget(INVITATION_TARGET_TENANT,sourceTenantId,null,null)==null)
   throw invalid("当前租户未开放平台询价，不能发起跨租户定向邀请");
  LinkedHashSet<InvitationScopeKey> result=new LinkedHashSet<>();
  for(var request:requests) {
   if(request==null) throw invalid("邀请范围不能为空");
   String type=request.getTargetType(); Long tenantId=request.getTargetTenantId();
   Long enterpriseId=request.getTargetEnterpriseId(); Long shopId=request.getTargetShopId();
   if(tenantId==null||tenantId<=0||Objects.equals(tenantId,3000L)) throw invalid("邀请目标租户无效");
   if(Objects.equals(tenantId,sourceTenantId))
    throw invalid("本租户店铺请使用原有店铺邀请，不能重复建立跨租户范围");
   if(INVITATION_TARGET_TENANT.equals(type)) {
    if(enterpriseId!=null||shopId!=null) throw invalid("租户邀请不能附带企业或店铺编号");
   } else if(INVITATION_TARGET_ENTERPRISE.equals(type)) {
    if(enterpriseId==null||enterpriseId<=0||shopId!=null) throw invalid("企业邀请必须明确企业且不能附带店铺编号");
   } else if(INVITATION_TARGET_SHOP.equals(type)) {
    if(enterpriseId==null||enterpriseId<=0||shopId==null||shopId<=0)
     throw invalid("店铺邀请必须明确租户、企业和店铺");
   } else throw invalid("邀请目标类型不正确");
   if(invitedScopeMapper.selectEligibleTarget(type,tenantId,enterpriseId,shopId)==null)
    throw invalid("邀请目标未开放、已失效或归属不一致");
   result.add(new InvitationScopeKey(type,tenantId,enterpriseId,shopId));
  }
  return List.copyOf(result);
 }

 private void replaceInvitationScopes(Long inquiryId,List<InvitationScopeKey> scopes) {
  invitedScopeMapper.deactivateByInquiryId(inquiryId);
  for(var scope:scopes) {
   Long existingId=invitedScopeMapper.selectExistingId(inquiryId,scope.targetType(),scope.targetTenantId(),
    scope.targetEnterpriseId(),scope.targetShopId());
   if(existingId!=null&&existingId>0) {
    if(invitedScopeMapper.activateById(existingId,inquiryId)!=1) throw invalid("邀请范围保存失败，请重试");
    continue;
   }
   var row=new InquiryInvitedScopeDO(); row.setInquiryId(inquiryId); row.setTargetType(scope.targetType());
   row.setTargetTenantId(scope.targetTenantId()); row.setTargetEnterpriseId(scope.targetEnterpriseId());
   row.setTargetShopId(scope.targetShopId()); invitedScopeMapper.insert(row);
  }
 }

 private record InvitationScopeKey(String targetType,Long targetTenantId,Long targetEnterpriseId,Long targetShopId) {}

 public static String normalizeVisibility(String value) {
  if(value==null||value.isBlank()) return VISIBILITY_TENANT_PUBLIC;
  if(!Set.of(VISIBILITY_PLATFORM_PUBLIC,VISIBILITY_TENANT_PUBLIC,VISIBILITY_INVITED_SHOPS).contains(value))
   throw invalid("询价可见范围不正确");
  return value;
 }
 @Transactional(rollbackFor=Exception.class)
 public void close(Long memberId, Long id) {
  requireWritableTenant();
  var row=owned(memberId,id,true);
  if(row.getStatus()==3) return;
  if(row.getStatus()==0) throw invalid("询价草稿尚未发布，不能关闭");
  if(row.getStatus()==4) throw invalid("已成交询价单不能关闭");
  row.setStatus(3); mapper.updateById(row);
  quoteMapper.update(null,new UpdateWrapper<InquiryQuoteDO>()
   .eq("inquiry_id",id).eq("status",PurchaseInquiryMarketService.QUOTE_SUBMITTED)
   .set("status",PurchaseInquiryMarketService.QUOTE_REJECTED).setSql("version = version + 1"));
 }
 private static void requireNoPublicContacts(InquiryDO inquiry,List<InquiryItemDO> items) {
  if(containsPublicContact(inquiry.getTitle()) || containsPublicContact(inquiry.getRemark())
   || items.stream().anyMatch(item ->
   containsPublicContact(item.getName()) || containsPublicContact(item.getCode())
    || containsPublicContact(item.getCategory()) || containsPublicContact(item.getSpecification())
    || containsPublicContact(item.getUnit()) || containsPublicContact(item.getRemark()))) {
   throw invalid("公开询价字段不能填写电话、邮箱、即时通讯号或网址，请移至联系信息栏");
  }
 }
 private static boolean containsPublicContact(String value) {
  return !Objects.equals(value,PurchaseInquiryMarketService.sanitizePublicText(value));
 }
 private InquiryDO findByRequestKey(Long memberId,String requestKey,boolean lock) {
  var query=new LambdaQueryWrapperX<InquiryDO>().eq(InquiryDO::getMemberId,memberId)
   .eq(InquiryDO::getRequestKey,requestKey);
  if(lock) query.last("FOR UPDATE");
  return mapper.selectOne(query);
 }
 private static void requireWritableTenant() {
  MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
 }
}
