package cn.iocoder.txgy.module.product.service.enterprise;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.*;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class EnterpriseMembershipServiceTest {
 @Mock EnterpriseApplicationMapper mapper;
 @Mock ProductEnterpriseMapper enterpriseMapper;
 @Mock ProductShopService shopService;
 @Mock MemberUserApi memberApi;
 @Mock AdminUserApi adminUserApi;
 @Mock PermissionCommonApi permissionApi;
 @Mock ProductEnterpriseAdminMemberMapper enterpriseAdminMemberMapper;
 @Mock ProductShopAdminAuthorizationMapper shopAdminAuthorizationMapper;
 @Mock ProductBusinessOperationAuditService operationAuditService;
 @InjectMocks EnterpriseMembershipService service;
 @BeforeEach void setTenantContext() {
  TenantContextHolder.setTenantId(1L);
 }
 @AfterEach void clearTenantContext() {
  TenantContextHolder.clear();
 }
 private void activeMember(String identity) {
  var user=new MemberUserRespDTO();user.setId(7L);user.setStatus(0);user.setMemberIdentity(identity);
  when(memberApi.getUser(7L)).thenReturn(CommonResult.success(user));
 }
 private EnterpriseApplicationDO approved(String identity) {
  activeMember(identity);
  var row=new EnterpriseApplicationDO(); row.setId(1L); row.setMemberId(7L); row.setStatus(1);
  row.setEnterpriseId(9L); row.setIdentity(identity);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var company=new ProductEnterpriseDO(); company.setId(9L); company.setAuditStatus(1);
  when(enterpriseMapper.selectById(9L)).thenReturn(company);
  return row;
 }
 @Test void unapprovedCannotPurchase() {
  activeMember("buyer");
  var row=new EnterpriseApplicationDO();row.setStatus(0);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void customerWithoutApprovedApplicationCannotPurchaseOrSell() {
  activeMember("customer");
  // Customer ownership is intentionally not a dependency of enterprise permission evaluation.
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  assertThrows(ServiceException.class,()->service.requireShop(7L,3L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void buyerCannotSellEvenIfEnterpriseHasShops() {
  approved("buyer"); assertThrows(ServiceException.class,()->service.salesShops(7L));verifyNoInteractions(shopService);
 }
 @Test void supplierCanOnlyUseEnabledAssociatedShops() {
  approved("supplier").setShopId(3L);
  var mine=new ProductShopDO();mine.setId(3L);mine.setEnterpriseId(9L);mine.setStatus(0);
  var other=new ProductShopDO();other.setId(4L);other.setEnterpriseId(10L);other.setStatus(0);
  var disabled=new ProductShopDO();disabled.setId(5L);disabled.setEnterpriseId(9L);disabled.setStatus(1);
  when(shopService.getShopList()).thenReturn(List.of(mine,other,disabled));
  service.requireShop(7L,3L);
  assertThrows(ServiceException.class,()->service.requireShop(7L,4L));
  assertThrows(ServiceException.class,()->service.requireShop(7L,5L));
 }
 @Test void supplierHistoryRemainsReadableAfterEnterpriseOrShopIsClosed() {
  activeMember("supplier");
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(1);
  row.setEnterpriseId(9L);row.setShopId(3L);row.setIdentity("supplier");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

  assertSame(row,service.requireSupplierShopHistory(7L,9L,3L));
  verifyNoInteractions(enterpriseMapper,shopService);

  var revoked=new ProductEnterpriseDO();revoked.setId(9L);revoked.setAuditStatus(0);
  when(enterpriseMapper.selectById(9L)).thenReturn(revoked);
  assertThrows(ServiceException.class,()->service.requireSupplier(7L));
 }
 @Test void reviewedApplicationCannotBeApprovedTwice() {
  var row=new EnterpriseApplicationDO();row.setStatus(1);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.audit(1L,1,"通过",null,null,2L));
  verifyNoInteractions(enterpriseMapper);
 }
 @Test void supplierApprovalAutomaticallyBindsEnabledEnterpriseShop() {
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(0);
  row.setIdentity("supplier");row.setCreditCode("CREDIT-9");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var company=new ProductEnterpriseDO();company.setId(9L);company.setCreditCode("CREDIT-9");company.setAuditStatus(1);
  when(enterpriseMapper.selectById(9L)).thenReturn(company);
  var shop=new ProductShopDO();shop.setId(3L);shop.setEnterpriseId(9L);shop.setStatus(0);
  when(shopService.getShopByEnterpriseId(9L)).thenReturn(shop);
  validEnterpriseAdmin(88L);
  service.audit(1L,1,"资料核验通过",9L,88L,2L);
  assertEquals(1,row.getStatus());assertEquals(9L,row.getEnterpriseId());assertEquals(3L,row.getShopId());
  verify(mapper).updateById(row);
 }
 @Test void supplierApprovalCreatesAndBindsShopForNewEnterprise() {
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(0);
  row.setIdentity("supplier");row.setName("公共防腐材料有限公司");row.setCreditCode("PUBLIC-CREDIT-1");
  row.setContactName("王经理");row.setContactMobile("13800000001");row.setIntroduction("防腐材料供应商");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  when(enterpriseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
  doAnswer(invocation -> { ((ProductEnterpriseDO) invocation.getArgument(0)).setId(9L); return 1; })
          .when(enterpriseMapper).insert(any(ProductEnterpriseDO.class));
  when(shopService.getShopByEnterpriseId(9L)).thenReturn(null);
  when(shopService.createShop(any(ProductShopDO.class))).thenReturn(3L);
  validEnterpriseAdmin(88L);
  service.audit(1L,1,"资料核验通过",null,88L,2L);
  var shopCaptor=ArgumentCaptor.forClass(ProductShopDO.class);
  verify(shopService).createShop(shopCaptor.capture());
  assertEquals("公共防腐材料有限公司官方店",shopCaptor.getValue().getName());
  assertEquals(9L,shopCaptor.getValue().getEnterpriseId());
  assertEquals(3L,row.getShopId());assertEquals(9L,row.getEnterpriseId());assertEquals(1,row.getStatus());
 }
 @Test void buyerApprovalDoesNotCreateSalesShop() {
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(0);
  row.setIdentity("buyer");row.setCreditCode("CREDIT-9");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var company=new ProductEnterpriseDO();company.setId(9L);company.setCreditCode("CREDIT-9");company.setAuditStatus(1);
  when(enterpriseMapper.selectById(9L)).thenReturn(company);
  service.audit(1L,1,"资料核验通过",9L,null,2L);
  assertNull(row.getShopId());verifyNoInteractions(shopService);
 }
 @Test void disabledMemberLosesBuyerScopeEvenWhenApplicationWasApproved() {
  var user=new MemberUserRespDTO();user.setId(7L);user.setStatus(1);user.setMemberIdentity("buyer");
  when(memberApi.getUser(7L)).thenReturn(CommonResult.success(user));
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void memberIdentityMismatchCannotReuseApprovedApplication() {
  activeMember("supplier");
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(1);
  row.setEnterpriseId(9L);row.setIdentity("buyer");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void supplierApprovalFailsClosedWithoutEnterpriseAdminRole() {
  var row=new EnterpriseApplicationDO();row.setId(1L);row.setMemberId(7L);row.setStatus(0);
  row.setIdentity("supplier");row.setCreditCode("CREDIT-9");
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var company=new ProductEnterpriseDO();company.setId(9L);company.setCreditCode("CREDIT-9");company.setAuditStatus(1);
  when(enterpriseMapper.selectById(9L)).thenReturn(company);
  var admin=new AdminUserRespDTO();admin.setId(88L);admin.setStatus(0);
  when(adminUserApi.getUser(88L)).thenReturn(CommonResult.success(admin));
  when(permissionApi.hasAnyRoles(88L,"mall_enterprise_admin")).thenReturn(CommonResult.success(false));
  assertThrows(ServiceException.class,()->service.audit(1L,1,"资料核验通过",9L,88L,2L));
  verifyNoInteractions(shopService,enterpriseAdminMemberMapper,shopAdminAuthorizationMapper);
 }
 private void validEnterpriseAdmin(Long id) {
  var admin=new AdminUserRespDTO();admin.setId(id);admin.setStatus(0);
  when(adminUserApi.getUser(id)).thenReturn(CommonResult.success(admin));
  when(permissionApi.hasAnyRoles(id,"mall_enterprise_admin")).thenReturn(CommonResult.success(true));
 }
}
