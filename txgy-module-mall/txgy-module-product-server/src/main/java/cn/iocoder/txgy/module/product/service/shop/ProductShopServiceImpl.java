package cn.iocoder.txgy.module.product.service.shop;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.service.enterprise.ProductEnterpriseService;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.*;

@Service
public class ProductShopServiceImpl implements ProductShopService {

    @Resource
    private ProductShopMapper shopMapper;

    @Resource
    private ProductEnterpriseService enterpriseService;

    @Override
    public Long createShop(ProductShopDO shop) {
        requireWritableTenant();
        ProductEnterpriseDO enterprise = enterpriseService.validateApproved(shop.getEnterpriseId());
        MallTenantWriteGuard.requireWritable(enterprise.getTenantId());
        shop.setTenantId(enterprise.getTenantId());
        if (shopMapper.selectByEnterpriseId(shop.getEnterpriseId()) != null) {
            throw exception(SHOP_ENTERPRISE_EXISTS);
        }
        validateManagerUserId(shop.getManagerUserId(), null);
        shopMapper.insert(shop);
        // MySQL 的自增主键在当前配置下不会回填到实体，按唯一企业编号回查，保证接口始终返回真实店铺编号。
        return shop.getId() != null ? shop.getId() : shopMapper.selectByEnterpriseId(shop.getEnterpriseId()).getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(ProductShopDO shop) {
        requireWritableTenant();
        ProductShopDO existingShop = validateShopExistsForUpdate(shop.getId());
        // 店铺下的商品、询价报价和成交订单都以 shopId 保留历史；归属一旦迁移会污染全部授权边界。
        // 因此首版将企业归属定义为创建后永久不可变，需要迁移时必须新建店铺并走显式数据迁移流程。
        if (!Objects.equals(existingShop.getEnterpriseId(), shop.getEnterpriseId())) {
            throw exception(SHOP_ENTERPRISE_IMMUTABLE);
        }
        ProductEnterpriseDO enterprise = enterpriseService.validateApproved(existingShop.getEnterpriseId());
        shop.setTenantId(enterprise.getTenantId());
        validateManagerUserId(shop.getManagerUserId(), shop.getId());
        shopMapper.updateById(shop);
    }

    @Override
    public void deleteShop(Long id) {
        requireWritableTenant();
        validateShopExists(id);
        shopMapper.deleteById(id);
    }

    @Override
    public ProductShopDO getShop(Long id) {
        return shopMapper.selectById(id);
    }

    @Override
    public ProductShopDO getShopByEnterpriseId(Long enterpriseId) {
        return enterpriseId == null ? null : shopMapper.selectByEnterpriseId(enterpriseId);
    }

    @Override
    public ProductShopDO getShopByManagerUserId(Long managerUserId) {
        return managerUserId == null ? null : shopMapper.selectByManagerUserId(managerUserId);
    }

    @Override
    public List<ProductShopDO> getShopList() {
        return shopMapper.selectList();
    }

    @Override
    public void validateEnabledShop(Long id) {
        ProductShopDO shop = validateShopExists(id);
        if (CommonStatusEnum.DISABLE.getStatus().equals(shop.getStatus())) {
            throw exception(SHOP_DISABLED);
        }
    }

    private ProductShopDO validateShopExists(Long id) {
        ProductShopDO shop = shopMapper.selectById(id);
        if (shop == null) {
            throw exception(SHOP_NOT_EXISTS);
        }
        return shop;
    }

    private ProductShopDO validateShopExistsForUpdate(Long id) {
        ProductShopDO shop = shopMapper.selectOne(new LambdaQueryWrapperX<ProductShopDO>()
                .eq(ProductShopDO::getId, id).last("FOR UPDATE"));
        if (shop == null) throw exception(SHOP_NOT_EXISTS);
        return shop;
    }

    private void validateManagerUserId(Long managerUserId, Long currentShopId) {
        if (managerUserId == null) {
            return;
        }
        ProductShopDO managedShop = shopMapper.selectByManagerUserId(managerUserId);
        if (managedShop != null && !Objects.equals(managedShop.getId(), currentShopId)) {
            throw exception(SHOP_MANAGER_USER_EXISTS);
        }
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }
}

