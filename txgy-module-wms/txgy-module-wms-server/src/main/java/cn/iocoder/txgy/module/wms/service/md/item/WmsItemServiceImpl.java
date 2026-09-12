package cn.iocoder.txgy.module.wms.service.md.item;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemListReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

/**
 * WMS 商品 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsItemServiceImpl implements WmsItemService {

    @Resource
    private WmsItemMapper itemMapper;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private WmsItemCategoryService categoryService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private WmsItemBrandService brandService;
    @Resource
    private WmsItemSkuService itemSkuService;
    @Resource
    private WmsItemQualityReportService qualityReportService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(WmsItemSaveReqVO createReqVO) {
        // 校验数据
        validateItemSaveData(createReqVO, null);

        // 插入商品
        WmsItemDO item = BeanUtils.toBean(createReqVO, WmsItemDO.class);
        item.setStatus(CommonStatusEnum.ENABLE.getStatus());
        itemMapper.insert(item);
        // 插入 SKU
        itemSkuService.createItemSkuList(item.getId(), createReqVO.getSkus());
        if (createReqVO.getInitialQualityReport() != null) {
            createReqVO.getInitialQualityReport().setItemId(item.getId());
            qualityReportService.createQualityReport(createReqVO.getInitialQualityReport());
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(WmsItemSaveReqVO updateReqVO) {
        // 校验存在
        WmsItemDO existingItem = validateItemExists(updateReqVO.getId());
        // 校验数据
        validateItemSaveData(updateReqVO, existingItem.getCategoryId());

        // 更新商品
        WmsItemDO updateObj = BeanUtils.toBean(updateReqVO, WmsItemDO.class);
        itemMapper.updateById(updateObj);
        // 更新 SKU
        itemSkuService.updateItemSkuList(updateReqVO.getId(), updateReqVO.getSkus());
    }

    private void validateItemSaveData(WmsItemSaveReqVO reqVO, Long existingCategoryId) {
        // 校验商品编号唯一
        validateItemCodeUnique(reqVO.getId(), reqVO.getCode());
        // 校验商品名称唯一
        validateItemNameUnique(reqVO.getId(), reqVO.getName());
        // 新增商品必须使用可用分类；编辑商品保持原分类时，兼容历史已关闭分类
        if (existingCategoryId == null || !ObjectUtil.equal(existingCategoryId, reqVO.getCategoryId())) {
            validateCategoryEnabled(reqVO.getCategoryId());
        } else {
            categoryService.validateItemCategoryExists(reqVO.getCategoryId());
        }
        // 校验商品品牌存在
        validateBrandExists(reqVO.getBrandId());
    }

    private void validateItemCodeUnique(Long id, String code) {
        WmsItemDO item = itemMapper.selectByCode(code);
        if (item == null) {
            return;
        }
        if (id == null || ObjectUtil.notEqual(item.getId(), id)) {
            throw exception(ITEM_CODE_DUPLICATE);
        }
    }

    private void validateItemNameUnique(Long id, String name) {
        WmsItemDO item = itemMapper.selectByName(name);
        if (item == null) {
            return;
        }
        if (id == null || ObjectUtil.notEqual(item.getId(), id)) {
            throw exception(ITEM_NAME_DUPLICATE);
        }
    }

    private void validateCategoryEnabled(Long categoryId) {
        categoryService.validateItemCategoryEnabled(categoryId);
    }

    private void validateBrandExists(Long brandId) {
        if (brandId == null) {
            return;
        }
        brandService.validateItemBrandExists(brandId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        // 校验存在
        WmsItemDO item = validateItemExists(id);

        // 删除前统一检查商品关联的 WMS 业务数据，避免只提示第一个 SKU 的关联类型
        List<String> associations = new ArrayList<>(itemSkuService.getItemSkuAssociationSummary(id));
        long qualityReportCount = qualityReportService.getQualityReportCount(id);
        if (qualityReportCount > 0) {
            associations.add("质检报告(" + qualityReportCount + "条)");
        }
        if (!associations.isEmpty()) {
            throw exception(ITEM_HAS_ASSOCIATIONS, item.getName(), String.join("、", associations));
        }

        // 删除商品和 SKU
        itemSkuService.deleteItemSkuListByItemId(id);
        itemMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItemStatus(Long id, Integer status) {
        validateItemExists(id);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(status)
                && !CommonStatusEnum.DISABLE.getStatus().equals(status)) {
            throw exception(ITEM_STATUS_INVALID);
        }
        itemMapper.updateById(new WmsItemDO().setId(id).setStatus(status));
    }

    @Override
    public WmsItemDO validateItemExists(Long id) {
        WmsItemDO item = itemMapper.selectById(id);
        if (item == null) {
            throw exception(ITEM_NOT_EXISTS);
        }
        return item;
    }

    @Override
    public WmsItemDO validateItemExistsAndEnabled(Long id) {
        WmsItemDO item = validateItemExists(id);
        if (!CommonStatusEnum.isEnable(item.getStatus())) {
            throw exception(ITEM_IS_DISABLED);
        }
        return item;
    }

    @Override
    public WmsItemDO getItem(Long id) {
        return itemMapper.selectById(id);
    }

    @Override
    public PageResult<WmsItemDO> getItemPage(WmsItemPageReqVO pageReqVO) {
        return itemMapper.selectPage(pageReqVO,
                categoryService.getSelfAndChildItemCategoryIdList(pageReqVO.getCategoryId()));
    }

    @Override
    public List<WmsItemDO> getItemList(WmsItemListReqVO listReqVO) {
        return itemMapper.selectList(listReqVO,
                categoryService.getSelfAndChildItemCategoryIdList(listReqVO.getCategoryId()));
    }

    @Override
    public List<WmsItemDO> getItemList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return itemMapper.selectByIds(ids);
    }

    @Override
    public long getItemCountByCategoryId(Long categoryId) {
        return itemMapper.selectCountByCategoryId(categoryId);
    }

    @Override
    public long getItemCountByBrandId(Long brandId) {
        return itemMapper.selectCountByBrandId(brandId);
    }

}
