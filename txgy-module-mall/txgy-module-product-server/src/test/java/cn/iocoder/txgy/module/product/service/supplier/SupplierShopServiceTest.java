package cn.iocoder.txgy.module.product.service.supplier;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.dal.mysql.shop.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.*;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SupplierShopServiceTest {
    SupplierShopService service; EnterpriseMembershipService auth;ProductShopMapper shops;ShopReturnAddressMapper addresses;
    @BeforeEach void setup(){service=new SupplierShopService();auth=mock(EnterpriseMembershipService.class);shops=mock(ProductShopMapper.class);addresses=mock(ShopReturnAddressMapper.class);
        ReflectionTestUtils.setField(service,"membership",auth);ReflectionTestUtils.setField(service,"shops",shops);ReflectionTestUtils.setField(service,"addresses",addresses);
        when(shops.selectOne(any())).thenReturn(new ProductShopDO().setId(10L));}
    SupplierShopService.Save req(String logo){return new SupplierShopService.Save(10L,"测试",logo,"公开联系人","12345","介绍",new SupplierShopService.Address("收件人","123456","陕西省西安市测试地址"));}
    @Test void noAuthorizationNoData(){doThrow(new ServiceException(403,"拒绝")).when(auth).requireShop(1L,10L);assertThrows(ServiceException.class,()->service.save(1L,req("")));verifyNoInteractions(shops,addresses);}
    @Test void missingAddressDenied(){assertThrows(ServiceException.class,()->service.requiredAddress(1L,10L));}
    @Test void unsafeLogoDenied(){assertThrows(ServiceException.class,()->service.save(1L,req("javascript:alert(1)")));verify(shops,never()).updateById(any(ProductShopDO.class));verifyNoInteractions(addresses);}
    @Test void whitelistAndPrivateAddress(){service.save(1L,req("https://example.com/logo.png"));verify(shops).updateById(argThat((ProductShopDO s)->s.getEnterpriseId()==null&&s.getStatus()==null&&s.getManagerUserId()==null&&"测试".equals(s.getName())));verify(addresses).insert(any(ShopReturnAddressDO.class));}
    @Test void existingAddressUpdatedNotDuplicated(){when(addresses.selectById(10L)).thenReturn(new ShopReturnAddressDO().setShopId(10L));service.save(1L,req(""));verify(addresses).updateById(any(ShopReturnAddressDO.class));verify(addresses,never()).insert(any(ShopReturnAddressDO.class));}
}
