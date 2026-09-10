package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
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

    @Override
    public CommonResult<ProductShopRespDTO> getShop(Long id) {
        ProductShopDO shop = shopService.getShop(id);
        return CommonResult.success(BeanUtils.toBean(shop, ProductShopRespDTO.class));
    }

    @Override
    public CommonResult<ProductShopRespDTO> getShopByManagerUserId(Long managerUserId) {
        ProductShopDO shop = shopService.getShopByManagerUserId(managerUserId);
        return CommonResult.success(BeanUtils.toBean(shop, ProductShopRespDTO.class));
    }

}

