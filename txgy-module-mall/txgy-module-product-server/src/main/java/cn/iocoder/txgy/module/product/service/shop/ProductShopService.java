package cn.iocoder.txgy.module.product.service.shop;

import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;

import java.util.List;

/** 产业链店铺服务。 */
public interface ProductShopService {

    Long createShop(ProductShopDO shop);

    void updateShop(ProductShopDO shop);

    void deleteShop(Long id);

    ProductShopDO getShop(Long id);

    ProductShopDO getShopByEnterpriseId(Long enterpriseId);

    ProductShopDO getShopByManagerUserId(Long managerUserId);

    List<ProductShopDO> getShopList();

    void validateEnabledShop(Long id);

    /**
     * 取得该店铺全部合法后台处置人编号（负责人 + 被授权到该店铺的账号/客服）。
     */
    List<Long> getAuthorizedAdminUserIds(Long shopId);
}

