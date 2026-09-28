package cn.iocoder.txgy.module.erp.service.product;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ProductSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductCategoryDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.service.integration.ErpMesMasterDataSyncService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsMasterDataSyncService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

/**
 * ERP 产品 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpProductServiceImpl implements ErpProductService {

    @Resource
    private ErpProductMapper productMapper;

    @Resource
    private ErpProductCategoryService productCategoryService;
    @Resource
    private ErpProductUnitService productUnitService;
    @Resource
    private ErpWmsMasterDataSyncService wmsMasterDataSyncService;
    @Resource
    private ErpMesMasterDataSyncService mesMasterDataSyncService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductSaveReqVO createReqVO) {
        // TODO 芋艿：校验分类
        validateMaterialCodeUnique(null, createReqVO.getMaterialCode());
        // 插入
        ErpProductDO product = BeanUtils.toBean(createReqVO, ErpProductDO.class);
        product.setSyncVersion(1L);
        productMapper.insert(product);
        wmsMasterDataSyncService.syncProductAfterCommit(product);
        mesMasterDataSyncService.syncProductAfterCommit(product);
        // 返回
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ProductSaveReqVO updateReqVO) {
        // TODO 芋艿：校验分类
        // 校验存在
        ErpProductDO oldProduct = validateProductExists(updateReqVO.getId());
        if (!Objects.equals(oldProduct.getMaterialCode(), updateReqVO.getMaterialCode())) {
            throw exception(PRODUCT_MATERIAL_CODE_IMMUTABLE);
        }
        validateMaterialCodeUnique(updateReqVO.getId(), updateReqVO.getMaterialCode());
        // 更新
        ErpProductDO updateObj = BeanUtils.toBean(updateReqVO, ErpProductDO.class);
        updateObj.setSyncVersion(oldProduct.getSyncVersion() == null ? 1L : oldProduct.getSyncVersion() + 1);
        productMapper.updateById(updateObj);
        wmsMasterDataSyncService.syncProductAfterCommit(updateObj);
        mesMasterDataSyncService.syncProductAfterCommit(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        // 校验存在
        ErpProductDO product = validateProductExists(id);
        // 删除
        productMapper.deleteById(id);
        product.setStatus(CommonStatusEnum.DISABLE.getStatus());
        product.setSyncVersion(product.getSyncVersion() == null ? 1L : product.getSyncVersion() + 1);
        wmsMasterDataSyncService.syncProductAfterCommit(product);
        mesMasterDataSyncService.syncProductAfterCommit(product);
    }

    @Override
    public List<ErpProductDO> validProductList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<ErpProductDO> list = productMapper.selectByIds(ids);
        Map<Long, ErpProductDO> productMap = convertMap(list, ErpProductDO::getId);
        for (Long id : ids) {
            ErpProductDO product = productMap.get(id);
            if (productMap.get(id) == null) {
                throw exception(PRODUCT_NOT_EXISTS);
            }
            if (CommonStatusEnum.isDisable(product.getStatus())) {
                throw exception(PRODUCT_NOT_ENABLE, product.getName());
            }
        }
        return list;
    }

    private ErpProductDO validateProductExists(Long id) {
        ErpProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        return product;
    }

    private void validateMaterialCodeUnique(Long id, String materialCode) {
        ErpProductDO product = productMapper.selectByMaterialCode(materialCode);
        if (product != null && !Objects.equals(product.getId(), id)) {
            throw exception(PRODUCT_MATERIAL_CODE_DUPLICATE, materialCode);
        }
    }

    @Override
    public ErpProductDO getProduct(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public List<ErpProductRespVO> getProductVOListByStatus(Integer status) {
        List<ErpProductDO> list = productMapper.selectListByStatus(status);
        return buildProductVOList(list);
    }

    @Override
    public List<ErpProductRespVO> getProductVOList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<ErpProductDO> list = productMapper.selectByIds(ids);
        return buildProductVOList(list);
    }

    @Override
    public PageResult<ErpProductRespVO> getProductVOPage(ErpProductPageReqVO pageReqVO) {
        PageResult<ErpProductDO> pageResult = productMapper.selectPage(pageReqVO);
        return new PageResult<>(buildProductVOList(pageResult.getList()), pageResult.getTotal());
    }

    private List<ErpProductRespVO> buildProductVOList(List<ErpProductDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Map<Long, ErpProductCategoryDO> categoryMap = productCategoryService.getProductCategoryMap(
                convertSet(list, ErpProductDO::getCategoryId));
        Map<Long, ErpProductUnitDO> unitMap = productUnitService.getProductUnitMap(
                convertSet(list, ErpProductDO::getUnitId));
        return BeanUtils.toBean(list, ErpProductRespVO.class, product -> {
            MapUtils.findAndThen(categoryMap, product.getCategoryId(),
                    category -> product.setCategoryName(category.getName()));
            MapUtils.findAndThen(unitMap, product.getUnitId(),
                    unit -> product.setUnitName(unit.getName()));
        });
    }

    @Override
    public Long getProductCountByCategoryId(Long categoryId) {
        return productMapper.selectCountByCategoryId(categoryId);
    }

    @Override
    public Long getProductCountByUnitId(Long unitId) {
        return productMapper.selectCountByUnitId(unitId);
    }

}
