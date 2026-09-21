package cn.iocoder.txgy.module.product.service.sku;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuStockLockReqDTO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.txgy.module.product.convert.sku.ProductSkuConvert;
import cn.iocoder.txgy.module.product.dal.dataobject.property.ProductPropertyDO;
import cn.iocoder.txgy.module.product.dal.dataobject.property.ProductPropertyValueDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.service.property.ProductPropertyService;
import cn.iocoder.txgy.module.product.service.property.ProductPropertyValueService;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import cn.iocoder.txgy.module.product.service.sku.adapter.ProductWmsInventoryAdapter;
import cn.iocoder.txgy.module.product.service.sku.dto.ProductWmsStockReconciliationDTO;
import cn.iocoder.txgy.module.product.service.wms.readonly.ProductWmsReadonlyQueryAdapter;
import cn.iocoder.txgy.module.product.service.wms.readonly.ProductWmsSkuSummaryDTO;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品 SKU Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductSkuServiceImpl implements ProductSkuService {

    @Resource
    private ProductSkuMapper productSkuMapper;

    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSpuService productSpuService;
    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductPropertyService productPropertyService;
    @Resource
    private ProductPropertyValueService productPropertyValueService;
    @Resource
    private ProductWmsInventoryAdapter productWmsInventoryAdapter;
    @Resource
    private ProductWmsReadonlyQueryAdapter productWmsReadonlyQueryAdapter;
    @Resource
    private org.springframework.jdbc.core.JdbcTemplate wmsReadonlyJdbc;

    @Override
    public void deleteSku(Long id) {
        // 校验存在
        validateSkuExists(id);
        // 删除
        productSkuMapper.deleteById(id);
    }

    private void validateSkuExists(Long id) {
        if (productSkuMapper.selectById(id) == null) {
            throw exception(SKU_NOT_EXISTS);
        }
    }

    @Override
    public ProductSkuDO getSku(Long id) {
        // WMS 对接尚未启用时，普通商品查询使用商城库存缓存，不调用外部适配器。
        return productSkuMapper.selectById(id);
    }

    @Override
    public ProductSkuDO getSku(Long id, boolean includeDeleted) {
        if (includeDeleted) {
            return productSkuMapper.selectByIdIncludeDeleted(id);
        }
        return getSku(id);
    }

    @Override
    public List<ProductSkuDO> getSkuList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return ListUtil.empty();
        }
        return productSkuMapper.selectByIds(ids);
    }

    @Override
    public void validateSkuList(List<ProductSkuSaveReqVO> skus, Boolean specType) {
        // 0、校验skus是否为空
        if (CollUtil.isEmpty(skus)) {
            throw exception(SKU_NOT_EXISTS);
        }
        skus.forEach(this::validateWmsMapping);
        // 单规格，赋予单规格默认属性
        if (ObjectUtil.equal(specType, false)) {
            ProductSkuSaveReqVO skuVO = skus.get(0);
            List<ProductSkuSaveReqVO.Property> properties = new ArrayList<>();
            ProductSkuSaveReqVO.Property property = new ProductSkuSaveReqVO.Property()
                    .setPropertyId(ProductPropertyDO.ID_DEFAULT).setPropertyName(ProductPropertyDO.NAME_DEFAULT)
                    .setValueId(ProductPropertyValueDO.ID_DEFAULT).setValueName(ProductPropertyValueDO.NAME_DEFAULT);
            properties.add(property);
            skuVO.setProperties(properties);
            return; // 单规格不需要后续的校验
        }

        // 1、校验属性项存在
        Set<Long> propertyIds = skus.stream().filter(p -> p.getProperties() != null)
                // 遍历多个 Property 属性
                .flatMap(p -> p.getProperties().stream())
                // 将每个 Property 转换成对应的 propertyId，最后形成集合
                .map(ProductSkuSaveReqVO.Property::getPropertyId)
                .collect(Collectors.toSet());
        List<ProductPropertyDO> propertyList = productPropertyService.getPropertyList(propertyIds);
        if (propertyList.size() != propertyIds.size()) {
            throw exception(PROPERTY_NOT_EXISTS);
        }

        // 2. 校验，一个 SKU 下，没有重复的属性。校验方式是，遍历每个 SKU ，看看是否有重复的属性 propertyId
        Map<Long, ProductPropertyValueDO> propertyValueMap = convertMap(productPropertyValueService.getPropertyValueListByPropertyId(propertyIds), ProductPropertyValueDO::getId);
        skus.forEach(sku -> {
            Set<Long> skuPropertyIds = convertSet(sku.getProperties(), propertyItem -> propertyValueMap.get(propertyItem.getValueId()).getPropertyId());
            if (skuPropertyIds.size() != sku.getProperties().size()) {
                throw exception(SKU_PROPERTIES_DUPLICATED);
            }
        });

        // 3. 再校验，每个 Sku 的属性值的数量，是一致的。
        int attrValueIdsSize = skus.get(0).getProperties().size();
        for (int i = 1; i < skus.size(); i++) {
            if (attrValueIdsSize != skus.get(i).getProperties().size()) {
                throw exception(SPU_ATTR_NUMBERS_MUST_BE_EQUALS);
            }
        }

        // 4. 最后校验，每个 Sku 之间不是重复的
        // 每个元素，都是一个 Sku 的 attrValueId 集合。这样，通过最外层的 Set ，判断是否有重复的.
        Set<Set<Long>> skuAttrValues = new HashSet<>();
        for (ProductSkuSaveReqVO sku : skus) {
            // 添加失败，说明重复
            if (!skuAttrValues.add(convertSet(sku.getProperties(), ProductSkuSaveReqVO.Property::getValueId))) {
                throw exception(SPU_SKU_NOT_DUPLICATE);
            }
        }
    }

    @Override
    public void createSkuList(Long spuId, List<ProductSkuSaveReqVO> skuCreateReqList) {
        validateNewSkuMappings(skuCreateReqList);
        List<ProductSkuDO> skus = BeanUtils.toBean(skuCreateReqList, ProductSkuDO.class, sku -> sku.setSpuId(spuId).setSalesCount(0));
        productSkuMapper.insertBatch(skus);
    }
    public static void validateNewSkuMappings(List<ProductSkuSaveReqVO> skus) {
        if (CollUtil.isEmpty(skus)) return; // 数量由原有 SKU 完整性校验处理。
        if (skus.stream().anyMatch(s -> s == null || s.getWmsSkuId() != null || s.getWmsWarehouseId() != null))
            throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                    "新增或复制商品不能继承 WMS 映射，请清空规格/仓库编号；真实映射须另行授权配置");
    }

    @Override
    public List<ProductSkuDO> getSkuListBySpuId(Long spuId) {
        return productSkuMapper.selectListBySpuId(spuId);
    }

    @Override
    public List<ProductSkuDO> getSkuListBySpuId(Collection<Long> spuIds) {
        if (CollUtil.isEmpty(spuIds)) {
            return Collections.emptyList();
        }
        return productSkuMapper.selectListBySpuId(spuIds);
    }

    @Override
    public void deleteSkuBySpuId(Long spuId) {
        productSkuMapper.deleteBySpuId(spuId);
    }

    @Override
    public int updateSkuProperty(Long propertyId, String propertyName) {
        // 获取所有的 sku
        List<ProductSkuDO> skuDOList = productSkuMapper.selectList();
        // 处理后需要更新的 sku
        List<ProductSkuDO> updateSkus = new ArrayList<>();
        if (CollUtil.isEmpty(skuDOList)) {
            return 0;
        }
        skuDOList.stream().filter(sku -> sku.getProperties() != null)
                .forEach(sku -> sku.getProperties().forEach(property -> {
                    if (property.getPropertyId().equals(propertyId) && !Objects.equals(property.getPropertyName(), propertyName))
                        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                                "该规格已用于商品，不能通过字典重命名绕过商品审核；请保留历史规格并新建规格");
                }));
        if (CollUtil.isEmpty(updateSkus)) {
            return 0;
        }

        productSkuMapper.updateBatch(updateSkus);
        return updateSkus.size();
    }

    @Override
    public int updateSkuPropertyValue(Long propertyValueId, String propertyValueName) {
        // 获取所有的 sku
        List<ProductSkuDO> skuDOList = productSkuMapper.selectList();
        // 处理后需要更新的 sku
        List<ProductSkuDO> updateSkus = new ArrayList<>();
        if (CollUtil.isEmpty(skuDOList)) {
            return 0;
        }
        skuDOList.stream().filter(sku -> sku.getProperties() != null)
                .forEach(sku -> sku.getProperties().forEach(property -> {
                    if (property.getValueId().equals(propertyValueId) && !Objects.equals(property.getValueName(), propertyValueName))
                        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                                "该规格值已用于商品，不能通过字典重命名绕过商品审核；请保留历史规格并新建规格值");
                }));
        if (CollUtil.isEmpty(updateSkus)) {
            return 0;
        }

        productSkuMapper.updateBatch(updateSkus);
        return updateSkus.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkuList(Long spuId, List<ProductSkuSaveReqVO> skus) {
        var existing = productSkuMapper.selectListBySpuId(spuId);
        var byKey = existing.stream().collect(Collectors.toMap(ProductSkuServiceImpl::reviewPropertyKey, s -> s));
        if (existing.size() != skus.size()) throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "不得新增、删除或重建已有商品SKU，请保留规格编号及历史关联");
        for (var input : skus) {
            var candidate = BeanUtils.toBean(input, ProductSkuDO.class);
            var original = byKey.remove(reviewPropertyKey(candidate));
            if (original == null) throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400, "不得修改已有SKU规格结构");
            if ((input.getWmsSkuId() != null && !Objects.equals(input.getWmsSkuId(), original.getWmsSkuId()))
                    || (input.getWmsWarehouseId() != null && !Objects.equals(input.getWmsWarehouseId(), original.getWmsWarehouseId())))
                throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400, "商品编辑不得修改 WMS 规格和仓库映射");
            // 明确白名单，不写库存/销量/映射/规格，不靠空字段更新策略碰运气。
            var patch = new ProductSkuDO().setId(original.getId()).setPrice(input.getPrice())
                    .setMarketPrice(input.getMarketPrice()).setCostPrice(input.getCostPrice()).setBarCode(input.getBarCode())
                    .setPicUrl(input.getPicUrl()).setWeight(input.getWeight()).setVolume(input.getVolume())
                    .setFirstBrokeragePrice(input.getFirstBrokeragePrice()).setSecondBrokeragePrice(input.getSecondBrokeragePrice());
            if (productSkuMapper.updateById(patch) != 1) throw new IllegalStateException("商品SKU保存失败");
        }
    }
    private static String reviewPropertyKey(ProductSkuDO s) {
        if (CollUtil.isEmpty(s.getProperties())) return "";
        return s.getProperties().stream().sorted(Comparator.comparing(ProductSkuDO.Property::getPropertyId))
                .map(p -> p.getPropertyId() + ":" + p.getValueId()).collect(Collectors.joining("|"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkuStock(ProductSkuUpdateStockReqDTO updateStockReqDTO) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveSkuStock(ProductSkuStockLockReqDTO reqDTO) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseSkuStock(ProductSkuStockLockReqDTO reqDTO) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outboundSkuStock(ProductSkuStockLockReqDTO reqDTO) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inboundReturnSkuStock(String returnNo, ProductSkuStockLockReqDTO reqDTO) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    @Override
    public Map<String, ProductWmsInventoryAdapter.ReservationSummary> getWmsStockStatus(Collection<String> orderNos) {
        return productWmsInventoryAdapter.getReservationSummaries(orderNos);
    }

    @Override
    public List<ProductWmsSkuSummaryDTO> getWmsSkuSummary(Long itemId) {
        if (itemId == null || itemId <= 0) throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400, "WMS商品编号无效");
        Long tenant = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        Set<Long> visible = new HashSet<>(wmsReadonlyJdbc.queryForList(
                "SELECT DISTINCT s.wms_sku_id FROM product_sku s JOIN product_spu p ON p.id=s.spu_id AND p.tenant_id=s.tenant_id "
                        + "WHERE s.tenant_id=? AND s.deleted=0 AND p.deleted=0 AND p.status=1 AND s.wms_sku_id IS NOT NULL",
                Long.class, tenant));
        if (visible.isEmpty()) throw new cn.iocoder.txgy.framework.common.exception.ServiceException(404, "未关联可展示的商城商品");
        var result = productWmsReadonlyQueryAdapter.getSkuSummary(itemId).stream()
                .filter(sku -> visible.contains(sku.getSkuId())).toList();
        if (result.isEmpty()) throw new cn.iocoder.txgy.framework.common.exception.ServiceException(404, "未关联可展示的商城商品");
        return result;
    }

    @Override
    public List<ProductWmsStockReconciliationDTO> getWmsStockReconciliation(Collection<Long> spuIds) {
        List<ProductWmsStockReconciliationDTO> results = productSkuMapper.selectListWithWmsMapping(spuIds).stream()
                .map(this::reconcileWmsStock)
                .toList();
        if (results.isEmpty()) {
            return results;
        }
        Map<Long, String> spuNameMap = productSpuService.getSpuList(
                        results.stream().map(ProductWmsStockReconciliationDTO::getSpuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(spu -> spu.getId(), spu -> spu.getName()));
        results.forEach(item -> item.setSpuName(spuNameMap.get(item.getSpuId())));
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncWmsStockCache(Collection<Long> spuIds) {
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "商城本地库存写入已停用，库存以 WMS 实时查询结果为准");
    }

    private ProductWmsStockReconciliationDTO reconcileWmsStock(ProductSkuDO sku) {
        ProductWmsInventoryAdapter.InventorySnapshot snapshot = productWmsInventoryAdapter.getSnapshot(
                sku.getWmsSkuId(), sku.getWmsWarehouseId());
        String status;
        if (!snapshot.exists()) {
            status = ProductWmsStockReconciliationDTO.STATUS_MISSING_INVENTORY;
        } else if (!Objects.equals(sku.getStock(), snapshot.availableStock())) {
            status = ProductWmsStockReconciliationDTO.STATUS_CACHE_DIFFERENCE;
        } else {
            status = ProductWmsStockReconciliationDTO.STATUS_NORMAL;
        }
        return new ProductWmsStockReconciliationDTO(sku.getId(), sku.getSpuId(), null, sku.getWmsSkuId(),
                sku.getWmsWarehouseId(), sku.getStock(), snapshot.physicalQuantity(), snapshot.lockedQuantity(),
                snapshot.availableStock(), status);
    }

    private StockFulfillment resolveStockFulfillment(ProductSkuStockLockReqDTO reqDTO) {
        Map<Long, Integer> skuCountMap = new LinkedHashMap<>();
        reqDTO.getItems().forEach(item -> {
            if (item.getCount() == null || item.getCount() <= 0) {
                throw exception(SKU_STOCK_NOT_ENOUGH);
            }
            skuCountMap.merge(item.getId(), item.getCount(), Integer::sum);
        });
        Map<Long, ProductSkuDO> skuMap = convertMap(productSkuMapper.selectByIds(skuCountMap.keySet()), ProductSkuDO::getId);
        if (skuMap.size() != skuCountMap.size()) {
            throw exception(SKU_NOT_EXISTS);
        }

        List<ProductSkuStockLockReqDTO.Item> localItems = new ArrayList<>();
        Map<WmsInventoryKey, Integer> wmsCountMap = new LinkedHashMap<>();
        skuCountMap.forEach((skuId, count) -> {
            ProductSkuDO sku = skuMap.get(skuId);
            validateWmsMapping(sku);
            if (sku.getWmsSkuId() == null) {
                ProductSkuStockLockReqDTO.Item localItem = new ProductSkuStockLockReqDTO.Item();
                localItem.setId(skuId).setCount(count);
                localItems.add(localItem);
                return;
            }
            wmsCountMap.merge(new WmsInventoryKey(sku.getWmsSkuId(), sku.getWmsWarehouseId()), count, Integer::sum);
        });
        List<ProductWmsInventoryAdapter.Item> wmsItems = wmsCountMap.entrySet().stream()
                .map(entry -> new ProductWmsInventoryAdapter.Item(entry.getKey().wmsSkuId(),
                        entry.getKey().warehouseId(), entry.getValue()))
                .toList();
        return new StockFulfillment(localItems, wmsItems);
    }

    private void updateLocalSkuStock(List<ProductSkuStockLockReqDTO.Item> localItems, int direction) {
        if (CollUtil.isEmpty(localItems)) {
            return;
        }
        List<ProductSkuUpdateStockReqDTO.Item> items = localItems.stream().map(item -> {
            ProductSkuUpdateStockReqDTO.Item stockItem = new ProductSkuUpdateStockReqDTO.Item();
            return stockItem.setId(item.getId()).setIncrCount(item.getCount() * direction);
        }).toList();
        updateSkuStock(new ProductSkuUpdateStockReqDTO(items));
    }

    private void validateWmsMapping(ProductSkuSaveReqVO sku) {
        if ((sku.getWmsSkuId() == null) != (sku.getWmsWarehouseId() == null)) {
            throw exception(SKU_WMS_MAPPING_INVALID);
        }
    }

    private void validateWmsMapping(ProductSkuDO sku) {
        if ((sku.getWmsSkuId() == null) != (sku.getWmsWarehouseId() == null)) {
            throw exception(SKU_WMS_MAPPING_INVALID);
        }
    }

    private record StockFulfillment(List<ProductSkuStockLockReqDTO.Item> localItems,
                                    List<ProductWmsInventoryAdapter.Item> wmsItems) {
    }

    private record WmsInventoryKey(Long wmsSkuId, Long warehouseId) {
    }

}

