package cn.iocoder.txgy.module.wms.service.md.item;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemBrandDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemBrandMapper;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_BRAND_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_BRAND_HAS_ITEM;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_BRAND_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_BRAND_NOT_EXISTS;

/**
 * WMS 商品品牌 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsItemBrandServiceImpl implements WmsItemBrandService {

    @Resource
    private WmsItemBrandMapper brandMapper;
    @Resource
    private WmsItemService itemService;
    @Resource
    private WmsItemCategoryService categoryService;
    @Resource
    private WmsMerchantService merchantService;

    @Override
    public Long createItemBrand(WmsItemBrandSaveReqVO createReqVO) {
        validateBrandSaveData(null, createReqVO);

        // 新增
        WmsItemBrandDO brand = BeanUtils.toBean(createReqVO, WmsItemBrandDO.class);
        brandMapper.insert(brand);
        return brand.getId();
    }

    @Override
    public void updateItemBrand(WmsItemBrandSaveReqVO updateReqVO) {
        // 校验存在
        validateItemBrandExists(updateReqVO.getId());
        validateBrandSaveData(updateReqVO.getId(), updateReqVO);

        // 更新
        WmsItemBrandDO updateObj = BeanUtils.toBean(updateReqVO, WmsItemBrandDO.class);
        brandMapper.updateById(updateObj);
    }

    private void validateBrandSaveData(Long id, WmsItemBrandSaveReqVO reqVO) {
        validateBrandCodeUnique(id, reqVO.getCode());
        validateBrandNameUnique(id, reqVO.getName());
    }

    private void validateBrandCodeUnique(Long id, String code) {
        WmsItemBrandDO brand = brandMapper.selectByCode(code);
        if (brand == null) {
            return;
        }
        // 如果 id 为空，说明新增；和数据库已有 code 重复
        if (id == null) {
            throw exception(ITEM_BRAND_CODE_DUPLICATE);
        }
        if (ObjectUtil.notEqual(brand.getId(), id)) {
            throw exception(ITEM_BRAND_CODE_DUPLICATE);
        }
    }

    private void validateBrandNameUnique(Long id, String name) {
        WmsItemBrandDO brand = brandMapper.selectByName(name);
        if (brand == null) {
            return;
        }
        if (id == null) {
            throw exception(ITEM_BRAND_NAME_DUPLICATE);
        }
        if (ObjectUtil.notEqual(brand.getId(), id)) {
            throw exception(ITEM_BRAND_NAME_DUPLICATE);
        }
    }

    @Override
    public void deleteItemBrand(Long id) {
        // 校验存在
        validateItemBrandExists(id);
        // 校验品牌下不存在商品
        if (itemService.getItemCountByBrandId(id) > 0) {
            throw exception(ITEM_BRAND_HAS_ITEM);
        }

        // 删除
        brandMapper.deleteById(id);
    }

    @Override
    public WmsItemBrandDO validateItemBrandExists(Long id) {
        WmsItemBrandDO brand = brandMapper.selectById(id);
        if (brand == null) {
            throw exception(ITEM_BRAND_NOT_EXISTS);
        }
        return brand;
    }

    @Override
    public WmsItemBrandDO getItemBrand(Long id) {
        WmsItemBrandDO brand = brandMapper.selectById(id);
        enrichDisplayNames(brand == null ? Collections.emptyList() : List.of(brand));
        return brand;
    }

    @Override
    public PageResult<WmsItemBrandDO> getItemBrandPage(WmsItemBrandPageReqVO pageReqVO) {
        PageResult<WmsItemBrandDO> pageResult = brandMapper.selectPage(pageReqVO);
        enrichDisplayNames(pageResult.getList());
        return pageResult;
    }

    private void enrichDisplayNames(List<WmsItemBrandDO> brands) {
        if (CollUtil.isEmpty(brands)) {
            return;
        }
        Set<Long> categoryIds = brands.stream().map(WmsItemBrandDO::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> supplierIds = brands.stream().map(WmsItemBrandDO::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, WmsItemCategoryDO> categories = categoryIds.isEmpty()
                ? Collections.emptyMap() : categoryService.getItemCategoryMap(categoryIds);
        Map<Long, WmsMerchantDO> suppliers = supplierIds.isEmpty()
                ? Collections.emptyMap() : merchantService.getMerchantMap(supplierIds);
        brands.forEach(brand -> {
            WmsItemCategoryDO category = categories.get(brand.getCategoryId());
            if (category != null) {
                brand.setCategoryName(category.getName());
            }
            WmsMerchantDO supplier = suppliers.get(brand.getSupplierId());
            if (supplier != null) {
                brand.setSupplierName(supplier.getName());
            }
        });
    }

    @Override
    public List<WmsItemBrandDO> getItemBrandList() {
        return brandMapper.selectList();
    }

    @Override
    public List<WmsItemBrandDO> getItemBrandList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return brandMapper.selectByIds(ids);
    }

}
