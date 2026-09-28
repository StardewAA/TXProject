package cn.iocoder.txgy.module.product.service.inquiry;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryDO;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PurchaseInquiryServiceTest {
 @BeforeEach void setUpBuyer() {
  cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
  var buyer=new EnterpriseApplicationDO();buyer.setEnterpriseId(100L);buyer.setStatus(1);
  lenient().when(membership.requireBuyer(7L)).thenReturn(buyer);
 }
 @AfterEach void clearTenant() {
  cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear();
 }
 private cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO imageRequest(String url) {
  var row=new InquiryDO();row.setId(12L);row.setMemberId(7L);row.setEnterpriseId(100L);row.setStatus(0);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setId(12L);req.setDeadline(System.currentTimeMillis()+86400000);
  var item=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO.Item();item.setName("保温板");item.setPicUrl(url);
  req.setItems(java.util.List.of(item));return req;
 }
 @Test void imageIsSavedOnInquiryItem() {
  service.save(7L,imageRequest("https://example.com/material.png"));
  verify(itemMapper).insert(argThat((cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO item)->"https://example.com/material.png".equals(item.getPicUrl())&&item.getInquiryId()==12L));
 }
 @Test void unsafeImageUrlRejected() {
  assertThrows(ServiceException.class,()->service.save(7L,imageRequest("javascript:alert(1)")));
  verify(itemMapper,never()).insert(any(cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO.class));
 }
 @Test void emptyImageSupportsRemoval() {
  service.save(7L,imageRequest(""));
  verify(itemMapper).insert(argThat((cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO item)->"".equals(item.getPicUrl())));
 }
 @Test void platformPublicInquiryIsAcceptedWithoutTenantInvitations() {
  var req=imageRequest("");
  req.setVisibility(PurchaseInquiryService.VISIBILITY_PLATFORM_PUBLIC);
  req.setDeliveryAreaId(110105);
  req.setInvitedShopIds(java.util.List.of());

  service.save(7L,req);

  verify(mapper).updateById(argThat((InquiryDO row)->
   PurchaseInquiryService.VISIBILITY_PLATFORM_PUBLIC.equals(row.getVisibility())));
  verify(invitedShopMapper).deletePhysicallyByInquiryId(1L,12L);
  verify(invitedShopMapper,never()).insert(any(cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedShopDO.class));
 }
 @Mock InquiryMapper mapper;
 @Mock InquiryItemMapper itemMapper;
  @Mock InquiryQuoteMapper quoteMapper;
  @Mock InquiryInvitedShopMapper invitedShopMapper;
  @Mock InquiryInvitedScopeMapper invitedScopeMapper;
 @Mock EnterpriseMembershipService membership;
 @InjectMocks PurchaseInquiryService service;
 @Test void expiredSubmittedInquiryIsClosedForQuotes() {
  var row=new InquiryDO();row.setStatus(1);row.setDeadline(System.currentTimeMillis()-100);
  assertEquals(2,PurchaseInquiryService.effectiveStatus(row));
  row.setStatus(3);assertEquals(3,PurchaseInquiryService.effectiveStatus(row));
 }
 @Test void missingOrForeignInquiryCannotBeRead() {
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
  assertThrows(ServiceException.class,()->service.detail(7L,99L));
  verifyNoInteractions(itemMapper);
 }
 @Test void repeatSubmitDoesNotGenerateAnotherNumber() {
  var row=new InquiryDO();row.setEnterpriseId(100L);row.setStatus(1);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  service.submit(7L,1L);verify(mapper,never()).updateById(any(InquiryDO.class));
 }
 @Test void expiredDraftCannotSubmit() {
  var row=new InquiryDO();row.setEnterpriseId(100L);row.setStatus(0);row.setDeadline(System.currentTimeMillis()-100);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.submit(7L,1L));
  verify(mapper,never()).updateById(any(InquiryDO.class));
 }
 @Test void accountReboundToAnotherEnterpriseCannotReadOldInquiry() {
  var row=new InquiryDO();row.setId(12L);row.setMemberId(7L);row.setEnterpriseId(999L);row.setStatus(0);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.detail(7L,12L));
  verifyNoInteractions(itemMapper);
 }
 @Test void submitRejectsContactsEmbeddedInPublicFields() {
  var row=new InquiryDO();row.setId(12L);row.setEnterpriseId(100L);row.setStatus(0);
  row.setDeadline(System.currentTimeMillis()+86400000);row.setTitle("钢材采购");
  row.setRemark("补充说明 13800138000");
  var item=new cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO();
  item.setName("螺纹钢");item.setRemark("行备注 buyer@example.com");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  lenient().when(itemMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO,?>>any(),eq(12L)))
   .thenReturn(java.util.List.of(item));
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  row.setRemark(null);
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  item.setRemark("电话 １ ３ ８ ０ ０ １ ３ ８ ０ ０ ０");
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  item.setRemark("微信: buyer_123");
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  item.setRemark("QQ: 1 2 3 4 5 6 7 8");
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  item.setRemark("资料 https://attacker.example/contact");
  assertThrows(ServiceException.class,()->service.submit(7L,12L));
  verify(mapper,never()).updateById(any(InquiryDO.class));
 }
 @Test void newInquiryIdempotencyCannotReturnOldEnterpriseInquiry() {
  var old=new InquiryDO();old.setId(12L);old.setMemberId(7L);old.setEnterpriseId(999L);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(old);
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setDeadline(System.currentTimeMillis()+86400000);req.setRequestKey("old-enterprise-key");
  assertThrows(ServiceException.class,()->service.save(7L,req));
  verify(mapper,never()).insert(any(InquiryDO.class));
 }
 @Test void concurrentNewInquiryRetryReturnsWinningId() {
  var winner=new InquiryDO();winner.setId(12L);winner.setMemberId(7L);winner.setEnterpriseId(100L);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null,winner);
  doThrow(new org.springframework.dao.DuplicateKeyException("duplicate request key"))
   .when(mapper).insert(any(InquiryDO.class));
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setDeadline(System.currentTimeMillis()+86400000);req.setRequestKey("concurrent-key-0001");

  assertEquals(12L,service.save(7L,req));
  verifyNoInteractions(itemMapper);
 }
 @Test void concurrentNewInquiryRetryRejectsWinnerFromFormerEnterprise() {
  var winner=new InquiryDO();winner.setId(12L);winner.setMemberId(7L);winner.setEnterpriseId(999L);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null,winner);
  doThrow(new org.springframework.dao.DuplicateKeyException("duplicate request key"))
   .when(mapper).insert(any(InquiryDO.class));
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setDeadline(System.currentTimeMillis()+86400000);req.setRequestKey("concurrent-key-0002");

  assertThrows(ServiceException.class,()->service.save(7L,req));
  verifyNoInteractions(itemMapper);
 }
 @Test void newInquiryUsesWmsDirectCompatibilityStateInsteadOfErpPending() {
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
  doAnswer(invocation->{
   InquiryDO row=invocation.getArgument(0);row.setId(88L);return 1;
  }).when(mapper).insert(any(InquiryDO.class));
  var req=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquirySaveReqVO();
  req.setDeadline(System.currentTimeMillis()+86400000);req.setRequestKey("wms-direct-key-0001");
  req.setItems(java.util.List.of());

  assertEquals(88L,service.save(7L,req));
  verify(mapper).insert(argThat((InquiryDO row)->"WMS_DIRECT".equals(row.getErpStatus())
   && row.getErpNo()==null));
 }
  @Test void closeRejectsAllSubmittedQuotes() {
  var row=new InquiryDO();row.setId(12L);row.setEnterpriseId(100L);row.setStatus(1);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  service.close(7L,12L);
  assertEquals(3,row.getStatus());
  verify(mapper).updateById(row);
  verify(quoteMapper).update(isNull(),any(Wrapper.class));
  }
  @Test void crossTenantDirectedScopeIsValidatedAndPersisted() {
   var req=imageRequest("");
   req.setVisibility(PurchaseInquiryService.VISIBILITY_INVITED_SHOPS);
   req.setInvitedShopIds(java.util.List.of());
   var scope=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryInvitationScopeReqVO();
   scope.setTargetType(PurchaseInquiryService.INVITATION_TARGET_SHOP);
   scope.setTargetTenantId(2009L);scope.setTargetEnterpriseId(301L);scope.setTargetShopId(401L);
   req.setInvitedScopes(java.util.List.of(scope));
   var eligible=new InquiryInvitedScopeMapper.InvitationTargetRow();eligible.setTenantId(2009L);
   when(invitedScopeMapper.selectEligibleTarget(PurchaseInquiryService.INVITATION_TARGET_TENANT,1L,null,null))
    .thenReturn(new InquiryInvitedScopeMapper.InvitationTargetRow());
   when(invitedScopeMapper.selectEligibleTarget(PurchaseInquiryService.INVITATION_TARGET_SHOP,2009L,301L,401L))
    .thenReturn(eligible);

   service.save(7L,req);

   verify(invitedScopeMapper).deactivateByInquiryId(12L);
   verify(invitedScopeMapper).insert(argThat((cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedScopeDO row)->
    row.getInquiryId()==12L&&"SHOP".equals(row.getTargetType())&&row.getTargetTenantId()==2009L
     &&row.getTargetEnterpriseId()==301L&&row.getTargetShopId()==401L));
  }
  @Test void invitationDirectoryRequiresBuyerAndMapsOnlyPublicNamesAndIds() {
   var row=new InquiryInvitedScopeMapper.InvitationTargetRow();row.setTenantId(2009L);row.setTenantName("科盾");
   row.setEnterpriseId(301L);row.setEnterpriseName("保温企业");row.setShopId(401L);row.setShopName("材料店");
   when(invitedScopeMapper.selectEligibleShopOptions(1L,"保温",25)).thenReturn(java.util.List.of(row));

   var result=service.invitationOptions(7L,"  保温  ",25);

   verify(membership).requireBuyer(7L);
   assertEquals(1,result.size());assertEquals(2009L,result.get(0).getTenantId());
   assertEquals("科盾",result.get(0).getTenantName());assertEquals(301L,result.get(0).getEnterpriseId());
   assertEquals("保温企业",result.get(0).getEnterpriseName());assertEquals(401L,result.get(0).getShopId());
   assertEquals("材料店",result.get(0).getShopName());
  }
  @Test void invitationDirectoryRejectsUnboundedRequestsBeforeMapperAccess() {
   assertThrows(ServiceException.class,()->service.invitationOptions(7L,"x".repeat(51),50));
   assertThrows(ServiceException.class,()->service.invitationOptions(7L,null,101));
   verify(invitedScopeMapper,never()).selectEligibleShopOptions(anyLong(),any(),anyInt());
  }
  @Test void crossTenantScopeRejectsSameTenantAndDoesNotWriteScope() {
   var req=imageRequest("");req.setVisibility(PurchaseInquiryService.VISIBILITY_INVITED_SHOPS);
   var scope=new cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryInvitationScopeReqVO();
   scope.setTargetType(PurchaseInquiryService.INVITATION_TARGET_TENANT);scope.setTargetTenantId(1L);
   req.setInvitedScopes(java.util.List.of(scope));
   when(invitedScopeMapper.selectEligibleTarget(PurchaseInquiryService.INVITATION_TARGET_TENANT,1L,null,null))
    .thenReturn(new InquiryInvitedScopeMapper.InvitationTargetRow());

   assertThrows(ServiceException.class,()->service.save(7L,req));

   verify(invitedScopeMapper,never()).insert(any(cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedScopeDO.class));
   verify(invitedScopeMapper,never()).deactivateByInquiryId(anyLong());
  }
  @Test void submitRevalidatesCrossTenantScopeAndFailsClosed() {
   var row=new InquiryDO();row.setId(12L);row.setMemberId(7L);row.setEnterpriseId(100L);row.setStatus(0);
   row.setDeadline(System.currentTimeMillis()+86400000);row.setVisibility(PurchaseInquiryService.VISIBILITY_INVITED_SHOPS);
   when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
   var item=new cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO();item.setName("保温板");
   when(itemMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO,?>>any(),eq(12L)))
    .thenReturn(java.util.List.of(item));
   var stored=new cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedScopeDO();
   stored.setInquiryId(12L);stored.setTargetType(PurchaseInquiryService.INVITATION_TARGET_ENTERPRISE);
   stored.setTargetTenantId(2009L);stored.setTargetEnterpriseId(301L);
   when(invitedScopeMapper.selectActiveByInquiryId(12L)).thenReturn(java.util.List.of(stored));
   when(invitedScopeMapper.selectEligibleTarget(PurchaseInquiryService.INVITATION_TARGET_TENANT,1L,null,null))
    .thenReturn(new InquiryInvitedScopeMapper.InvitationTargetRow());

   assertThrows(ServiceException.class,()->service.submit(7L,12L));

   verify(mapper,never()).updateById(any(InquiryDO.class));
  }
 @Test void draftCannotBeClosedIntoPublicArchive() {
  var row=new InquiryDO();row.setId(12L);row.setEnterpriseId(100L);row.setStatus(0);row.setNo(null);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

  assertThrows(ServiceException.class,()->service.close(7L,12L));

  assertEquals(0,row.getStatus());
  assertNull(row.getNo());
  verify(mapper,never()).updateById(any(InquiryDO.class));
  verifyNoInteractions(quoteMapper);
 }
}
