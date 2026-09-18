package cn.iocoder.txgy.module.product.service.enterprise;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.*;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
 @InjectMocks EnterpriseMembershipService service;
 private EnterpriseApplicationDO approved(String identity) {
  var row=new EnterpriseApplicationDO(); row.setId(1L); row.setMemberId(7L); row.setStatus(1);
  row.setEnterpriseId(9L); row.setIdentity(identity);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  var company=new ProductEnterpriseDO(); company.setId(9L); company.setAuditStatus(1);
  when(enterpriseMapper.selectById(9L)).thenReturn(company);
  return row;
 }
 @Test void unapprovedCannotPurchase() {
  var row=new EnterpriseApplicationDO();row.setStatus(0);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void customerWithoutApprovedApplicationCannotPurchaseOrSell() {
  // Customer ownership is intentionally not a dependency of enterprise permission evaluation.
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
  assertThrows(ServiceException.class,()->service.requireBuyer(7L));
  assertThrows(ServiceException.class,()->service.requireShop(7L,3L));
  verifyNoInteractions(enterpriseMapper,shopService);
 }
 @Test void buyerCannotSellEvenIfEnterpriseHasShops() {
  approved("buyer"); assertTrue(service.salesShops(7L).isEmpty());verifyNoInteractions(shopService);
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
 @Test void reviewedApplicationCannotBeApprovedTwice() {
  var row=new EnterpriseApplicationDO();row.setStatus(1);
  when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);
  assertThrows(ServiceException.class,()->service.audit(1L,1,"通过",null,2L));
  verifyNoInteractions(enterpriseMapper);
 }
 @Test void approvalDoesNotAutomaticallyGrantExistingEnterpriseShops() {
  approved("supplier");
  assertTrue(service.salesShops(7L).isEmpty());
  verifyNoInteractions(shopService);
 }
}
