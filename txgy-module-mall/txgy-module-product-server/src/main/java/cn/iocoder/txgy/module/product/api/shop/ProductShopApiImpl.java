package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ShopReturnAddressDO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ShopReturnAddressMapper;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 企业店铺 API 实现。
 *
 * @author DAMU
 */
@RestController
@Validated
public class ProductShopApiImpl implements ProductShopApi {

    @Resource
    private ProductShopService shopService;
    @Resource
    private ShopReturnAddressMapper shopReturnAddressMapper;
    @Resource
    private ProductEnterpriseMapper productEnterpriseMapper;

    @Override
    public CommonResult<ProductShopRespDTO> getShop(Long id) {
        ProductShopDO shop = shopService.getShop(id);
        ProductShopRespDTO response = BeanUtils.toBean(shop, ProductShopRespDTO.class);
        if (response != null) {
            ShopReturnAddressDO address = shopReturnAddressMapper.selectById(id);
            String dispatchAddress = address != null ? address.getAddress() : null;
            if ((dispatchAddress == null || dispatchAddress.isBlank()) && shop.getEnterpriseId() != null) {
                ProductEnterpriseDO enterprise = productEnterpriseMapper.selectById(shop.getEnterpriseId());
                dispatchAddress = enterprise != null ? enterprise.getAddress() : null;
            }
            response.setDispatchAddress(dispatchAddress);
        }
        return CommonResult.success(response);
    }

    @Override
    public CommonResult<ProductShopRespDTO> getShopByManagerUserId(Long managerUserId) {
        ProductShopDO shop = shopService.getShopByManagerUserId(managerUserId);
        return CommonResult.success(BeanUtils.toBean(shop, ProductShopRespDTO.class));
    }

}

