package cn.iocoder.txgy.module.product.service.spu;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSpuUpdateStatusReqVO;
import cn.iocoder.txgy.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.txgy.module.product.service.brand.ProductBrandService;
import cn.iocoder.txgy.module.product.service.category.ProductCategoryService;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.product.dal.dataobject.category.ProductCategoryDO.CATEGORY_LEVEL;
import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品 SPU Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductSpuServiceImpl implements ProductSpuService {

    @Resource
    private ProductSpuMapper productSpuMapper;

    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSkuService productSkuService;
    @Resource
    private ProductBrandService brandService;
    @Resource
    private ProductCategoryService categoryService;
    @Resource
    private ProductShopService shopService;
    @Resource private cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService reviewService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpu(ProductSpuSaveReqVO createReqVO) {
        requireWritableTenant();
        checkAdminWrite(createReqVO.getShopId());
        if (createReqVO.getShopId() == null || createReqVO.getShopId() <= 0)
            throw cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid("新增商品须选择有效店铺");
        // 校验分类、品牌
        validateCategory(createReqVO.getCategoryId());
        brandService.validateProductBrand(createReqVO.getBrandId());
        validateShop(createReqVO.getShopId());
        // 校验 SKU
        List<ProductSkuSaveReqVO> skuSaveReqList = createReqVO.getSkus();
        cn.iocoder.txgy.module.product.service.sku.ProductSkuServiceImpl.validateNewSkuMappings(skuSaveReqList);
        productSkuService.validateSkuList(skuSaveReqList, createReqVO.getSpecType());

        ProductSpuDO spu = BeanUtils.toBean(createReqVO, ProductSpuDO.class);
        spu.setId(null).setStatus(0).setReviewStatus(0).setContentVersion(1L).setApprovedVersion(null)
                .setSubmissionId(null).setReviewRemark(null).setSalesCount(0).setBrowseCount(0);
        // 初始化 SPU 中 SKU 相关属性
        initSpuFromSkus(spu, skuSaveReqList);
        // 插入 SPU
        productSpuMapper.insert(spu);
        // 插入 SKU
        productSkuService.createSkuList(spu.getId(), skuSaveReqList);
        reviewService.created(spu.getId());
        // 返回
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(ProductSpuSaveReqVO updateReqVO) {
        requireWritableTenant();
        // 校验 SPU 是否存在
        ProductSpuDO spu = reviewService.lock(updateReqVO.getId());
        checkAdminWrite(spu.getShopId());
        String before = reviewService.beforeEdit(spu, updateReqVO.getExpectedVersion());
        if (!Objects.equals(spu.getShopId(), updateReqVO.getShopId()) || !Objects.equals(spu.getSpecType(), updateReqVO.getSpecType()))
            throw cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid("不得通过商品编辑更换店铺或重建已有规格");
        // 校验分类、品牌
        validateCategory(updateReqVO.getCategoryId());
        brandService.validateProductBrand(updateReqVO.getBrandId());
        validateShop(updateReqVO.getShopId());
        // 校验SKU
        List<ProductSkuSaveReqVO> skuSaveReqList = updateReqVO.getSkus();
        productSkuService.validateSkuList(skuSaveReqList, updateReqVO.getSpecType());

        // 更新 SPU
        ProductSpuDO updateObj = BeanUtils.toBean(updateReqVO, ProductSpuDO.class).setStatus(null)
                .setSalesCount(null).setBrowseCount(null).setStock(null);
        updateObj.setPrice(getMinValue(skuSaveReqList, ProductSkuSaveReqVO::getPrice))
                .setMarketPrice(getMinValue(skuSaveReqList, ProductSkuSaveReqVO::getMarketPrice))
                .setCostPrice(getMinValue(skuSaveReqList, ProductSkuSaveReqVO::getCostPrice));
        productSpuMapper.updateById(updateObj);
        // 批量更新 SKU
        productSkuService.updateSkuList(updateObj.getId(), updateReqVO.getSkus());
        reviewService.finishEdit(spu, before);
    }

    /**
     * 基于 SKU 的信息，初始化 SPU 的信息
     * 主要是计数相关的字段，例如说市场价、最大最小价、库存等等
     *
     * @param spu  商品 SPU
     * @param skus 商品 SKU 数组
     */
    private void initSpuFromSkus(ProductSpuDO spu, List<ProductSkuSaveReqVO> skus) {
        // sku 单价最低的商品的价格
        spu.setPrice(getMinValue(skus, ProductSkuSaveReqVO::getPrice));
        // sku 单价最低的商品的市场价格
        spu.setMarketPrice(getMinValue(skus, ProductSkuSaveReqVO::getMarketPrice));
        // sku 单价最低的商品的成本价格
        spu.setCostPrice(getMinValue(skus, ProductSkuSaveReqVO::getCostPrice));
        // skus 库存总数
        spu.setStock(getSumValue(skus, ProductSkuSaveReqVO::getStock, Math::addExact));
        // 若是 spu 已有状态则不处理
        if (spu.getStatus() == null) {
            spu.setStatus(ProductSpuStatusEnum.DISABLE.getStatus()); // 新商品审核后由商家主动上架
            spu.setSalesCount(0); // 默认商品销量
            spu.setBrowseCount(0); // 默认商品浏览量
        }
    }

    /**
     * 校验商品分类是否合法
     *
     * @param id 商品分类编号
     */
    private void validateCategory(Long id) {
        categoryService.validateCategory(id);
        // 校验层级
        if (categoryService.getCategoryLevel(id) < CATEGORY_LEVEL) {
            throw exception(SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR);
        }
    }

    /**
     * 店铺为空时，商品归属平台自营；有值时必须是启用店铺。
     */
    private void validateShop(Long shopId) {
        if (shopId != null) {
            shopService.validateEnabledShop(shopId);
        }
    }

    @Override
    public List<ProductSpuDO> validateSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        // 获得商品信息
        List<ProductSpuDO> list = productSpuMapper.selectByIds(ids);
        Map<Long, ProductSpuDO> spuMap = CollectionUtils.convertMap(list, ProductSpuDO::getId);
        // 校验
        ids.forEach(id -> {
            ProductSpuDO spu = spuMap.get(id);
            if (spu == null) {
                throw exception(SPU_NOT_EXISTS);
            }
            if (!reviewService.saleable(spu)) {
                throw exception(SPU_NOT_ENABLE, "编号 " + spu.getId());
            }
        });
        return list;
    }

    @Override
    public void updateBrowseCount(Long id, int incrCount) {
        productSpuMapper.updateBrowseCount(id , incrCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id) {
        requireWritableTenant();
        // 兼容既有可信内部调用；公开 Controller 只能使用带 expectedVersion 的重载。
        deleteLocked(reviewService.lock(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id, Long expectedVersion) {
        requireWritableTenant();
        var current = reviewService.lock(id);
        cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.expect(current, expectedVersion);
        deleteLocked(current);
    }

    private void deleteLocked(ProductSpuDO spuDO) {
        checkAdminWrite(spuDO.getShopId());
        // 校验商品状态不是回收站不能删除
        // 判断 SPU 状态是否为回收站
        if (ObjectUtil.notEqual(spuDO.getStatus(), ProductSpuStatusEnum.RECYCLE.getStatus())) {
            throw exception(SPU_NOT_RECYCLE);
        }
        // TODO 芋艿：【可选】参与活动中的商品，不允许删除？？？

        // 删除 SPU
        productSpuMapper.deleteById(spuDO.getId());
        // 删除关联的 SKU
        productSkuService.deleteSkuBySpuId(spuDO.getId());
    }

    private ProductSpuDO validateSpuExists(Long id) {
        ProductSpuDO spuDO = productSpuMapper.selectById(id);
        if (spuDO == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        return spuDO;
    }

    @Override
    public ProductSpuDO getSpu(Long id) {
        return productSpuMapper.selectById(id);
    }

    @Override
    public ProductSpuDO getSpu(Long id, boolean includeDeleted) {
        if (includeDeleted) {
            return productSpuMapper.selectByIdIncludeDeleted(id);
        }
        return getSpu(id);
    }

    @Override
    public List<ProductSpuDO> getSpuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        Map<Long, ProductSpuDO> spuMap = convertMap(productSpuMapper.selectByIds(ids), ProductSpuDO::getId);
        // 需要按照 ids 顺序返回。例如说：店铺装修选择了 [3, 1, 2] 三个商品，返回结果还是 [3, 1, 2]  这样的顺序
        return convertList(ids, spuMap::get);
    }

    @Override
    public List<ProductSpuDO> getSpuListByStatus(Integer status) {
        return productSpuMapper.selectList(ProductSpuDO::getStatus, status);
    }

    @Override
    public PageResult<ProductSpuDO> getSpuPage(ProductSpuPageReqVO pageReqVO) {
        return productSpuMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<ProductSpuDO> getSpuPage(AppProductSpuPageReqVO pageReqVO) {
        // 查找时，如果查找某个分类编号，则包含它的子分类。因为顶级分类不包含商品
        Set<Long> categoryIds = new HashSet<>();
        if (pageReqVO.getCategoryId() != null && pageReqVO.getCategoryId() > 0) {
            categoryIds.add(pageReqVO.getCategoryId());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setStatus(CommonStatusEnum.ENABLE.getStatus()).setParentId(pageReqVO.getCategoryId()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        if (CollUtil.isNotEmpty(pageReqVO.getCategoryIds())) {
            categoryIds.addAll(pageReqVO.getCategoryIds());
            List<ProductCategoryDO> categoryChildren = categoryService.getCategoryList(new ProductCategoryListReqVO()
                    .setStatus(CommonStatusEnum.ENABLE.getStatus()).setParentIds(pageReqVO.getCategoryIds()));
            categoryIds.addAll(convertList(categoryChildren, ProductCategoryDO::getId));
        }
        // 分页查询
        return productSpuMapper.selectPage(pageReqVO, categoryIds, reviewService::applySaleableQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpuStock(Map<Long, Integer> stockIncrCounts) {
        stockIncrCounts.forEach((id, incCount) -> productSpuMapper.updateStock(id, incCount));
    }

    @Override
    public void syncSpuStock(Map<Long, Integer> stockCounts) {
        stockCounts.forEach(productSpuMapper::updateStockCache);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpuStatus(ProductSpuUpdateStatusReqVO updateReqVO) {
        requireWritableTenant();
        // 校验存在
        var current = reviewService.lock(updateReqVO.getId());
        checkAdminWrite(current.getShopId());
        cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.expect(current, updateReqVO.getExpectedVersion());
        if (Objects.equals(current.getStatus(), -1) && !Objects.equals(updateReqVO.getStatus(), -1)) {
            if (!Objects.equals(updateReqVO.getStatus(), 0))
                throw cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid("恢复商品只能成为草稿，须重新审核后上架");
            reviewService.restoreDraft(current); return;
        }
        if (Objects.equals(current.getReviewStatus(), 10) && Objects.equals(updateReqVO.getStatus(), -1))
            throw cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid("请先撤回审核再回收商品");
        if (Objects.equals(updateReqVO.getStatus(), 1)) reviewService.requireApprovedForListing(current);
        // TODO 芋艿：【可选】参与活动中的商品，不允许下架？？？

        // 更新状态
        ProductSpuDO productSpuDO = new ProductSpuDO().setId(updateReqVO.getId()).setStatus(updateReqVO.getStatus());
        productSpuMapper.updateById(productSpuDO);
    }

    private void checkAdminWrite(Long shopId) {
        var user = cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUser();
        if (user != null && Objects.equals(user.getUserType(), cn.iocoder.txgy.framework.common.enums.UserTypeEnum.ADMIN.getValue()))
            reviewService.requireAdminWrite(shopId);
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }

    @Override
    public Map<Integer, Long> getTabsCount(ProductSpuPageReqVO reqVO) {
        Map<Integer, Long> counts = Maps.newLinkedHashMapWithExpectedSize(5);
        // 每个 tab 的数量 = 筛选条件（name/categoryId/createTime）+ 该 tab 的状态/库存条件
        counts.put(ProductSpuPageReqVO.FOR_SALE,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.FOR_SALE));
        counts.put(ProductSpuPageReqVO.IN_WAREHOUSE,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.IN_WAREHOUSE));
        counts.put(ProductSpuPageReqVO.SOLD_OUT,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.SOLD_OUT));
        counts.put(ProductSpuPageReqVO.ALERT_STOCK,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.ALERT_STOCK));
        counts.put(ProductSpuPageReqVO.RECYCLE_BIN,
                productSpuMapper.selectCountByTab(reqVO, ProductSpuPageReqVO.RECYCLE_BIN));
        return counts;
    }

    @Override
    public Long getSpuCountByCategoryId(Long categoryId) {
        return productSpuMapper.selectCount(ProductSpuDO::getCategoryId, categoryId);
    }

}

