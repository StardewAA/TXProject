package cn.iocoder.txgy.module.product.dal.mysql.shop;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductShopMapper extends BaseMapperX<ProductShopDO> {

    default ProductShopDO selectByEnterpriseId(Long enterpriseId) {
        return selectOne(ProductShopDO::getEnterpriseId, enterpriseId);
    }

    default ProductShopDO selectByManagerUserId(Long managerUserId) {
        return selectOne(ProductShopDO::getManagerUserId, managerUserId);
    }
}

