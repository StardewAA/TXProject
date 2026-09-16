package cn.iocoder.txgy.module.crm.service.product;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.crm.config.wms.CrmWmsProductReadProperties;
import cn.iocoder.txgy.module.crm.controller.admin.product.vo.product.CrmProductWmsSyncRespVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductDO;
import cn.iocoder.txgy.module.crm.dal.mysql.product.CrmProductMapper;
import cn.iocoder.txgy.module.crm.enums.DictTypeConstants;
import cn.iocoder.txgy.module.crm.enums.product.CrmProductDataSourceEnum;
import cn.iocoder.txgy.module.crm.enums.product.CrmProductStatusEnum;
import cn.iocoder.txgy.module.crm.service.wms.CrmWmsProductReadAdapter;
import cn.iocoder.txgy.module.crm.service.wms.CrmWmsProductSkuDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.PRODUCT_CATEGORY_NOT_EXISTS;

/**
 * CRM 产品与 WMS 商品（进仓产品）对接的同步 Service 实现类
 *
 * <p>数据来源为 WMS 交付的只读接口 {@code GET /admin-api/wms/crm/products/page}（见适配器实现）。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class CrmProductWmsSyncServiceImpl implements CrmProductWmsSyncService {

    /**
     * 单次同步最大页数，避免 WMS 返回的总数异常导致死循环
     */
    private static final int MAX_PAGE_COUNT = 500;
    /**
     * 结果中最多保留的明细条数
     */
    private static final int MAX_MESSAGE_COUNT = 20;
    /**
     * crm_product.name 长度上限
     */
    private static final int MAX_NAME_LENGTH = 100;
    /**
     * crm_product.no 长度上限
     */
    private static final int MAX_NO_LENGTH = 64;
    /**
     * crm_product.description 长度上限
     */
    private static final int MAX_DESCRIPTION_LENGTH = 100;
    /**
     * WMS 接口每页条数上限
     */
    private static final int MAX_PAGE_SIZE = 200;

    @Resource
    private CrmWmsProductReadProperties wmsProductReadProperties;
    @Resource
    private CrmWmsProductReadAdapter crmWmsProductReadAdapter;
    @Resource(name = "crmProductMapper")
    private CrmProductMapper productMapper;
    @Resource
    private CrmProductCategoryService productCategoryService;

    @Override
    public CrmProductWmsSyncRespVO syncFromWms() {
        CrmProductWmsSyncRespVO result = new CrmProductWmsSyncRespVO();
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return result.setSkipped(Boolean.TRUE).setMessage("缺少租户上下文，已拒绝同步");
        }
        // remote 模式下 WMS 令牌只对应一个租户，其它租户直接跳过，避免用别人的令牌读本租户数据
        if (wmsProductReadProperties.getTenantId() != null && wmsProductReadProperties.getTenantId() > 0
                && !wmsProductReadProperties.getTenantId().equals(tenantId)) {
            return result.setSkipped(Boolean.TRUE)
                    .setMessage(StrUtil.format("租户【{}】未配置 WMS 产品同步，已跳过", tenantId));
        }

        Long categoryId = resolveCategoryId();
        int pageSize = resolvePageSize();
        for (int pageNo = 1; pageNo <= MAX_PAGE_COUNT; pageNo++) {
            PageResult<CrmWmsProductSkuDTO> page = crmWmsProductReadAdapter.getProductPage(pageNo, pageSize);
            List<CrmWmsProductSkuDTO> skuList = page.getList();
            if (CollUtil.isEmpty(skuList)) {
                break;
            }
            skuList.forEach(sku -> syncOneSku(sku, categoryId, result));
            // 已取完最后一页
            if (page.getTotal() != null && page.getTotal() <= (long) pageNo * pageSize) {
                break;
            }
        }
        return result.setSyncTime(LocalDateTime.now());
    }

    private void syncOneSku(CrmWmsProductSkuDTO sku, Long categoryId, CrmProductWmsSyncRespVO result) {
        result.setTotal(result.getTotal() + 1);
        if (sku.getSkuId() == null) {
            result.setFailed(result.getFailed() + 1);
            addMessage(result, "WMS 返回的规格缺少规格编号，已跳过：" + JsonUtils.toJsonString(sku));
            return;
        }
        try {
            // 1. 已同步过：按 WMS 规格编号幂等更新 WMS 主数据字段
            CrmProductDO exists = productMapper.selectByWmsSkuId(sku.getSkuId());
            if (exists != null) {
                CrmProductDO updateObj = buildUpdateObj(exists, sku);
                if (updateObj == null) {
                    result.setUnchanged(result.getUnchanged() + 1);
                } else {
                    productMapper.updateById(updateObj);
                    result.setUpdated(result.getUpdated() + 1);
                }
                return;
            }
            // 2. 产品编码已被其它产品占用时跳过，避免覆盖人工维护的产品
            String no = resolveProductNo(sku);
            CrmProductDO noOwner = productMapper.selectByNo(no);
            if (noOwner != null) {
                result.setConflict(result.getConflict() + 1);
                addMessage(result, StrUtil.format("产品编码【{}】已被产品【{}({})】占用，跳过 WMS 规格【{}】",
                        no, noOwner.getName(), noOwner.getId(), sku.getSkuCode()));
                return;
            }
            // 3. 新增产品
            productMapper.insert(buildCreateObj(sku, no, categoryId));
            result.setCreated(result.getCreated() + 1);
        } catch (Exception ex) {
            result.setFailed(result.getFailed() + 1);
            addMessage(result, StrUtil.format("同步 WMS 规格【{}】失败：{}", sku.getSkuCode(), ex.getMessage()));
            log.error("[syncOneSku][WMS 规格({}) 同步失败]", sku.getSkuId(), ex);
        }
    }

    /**
     * 构造更新对象；WMS 主数据没有变化时返回 null，避免无意义的更新时间
     */
    private CrmProductDO buildUpdateObj(CrmProductDO exists, CrmWmsProductSkuDTO sku) {
        CrmProductDO updateObj = new CrmProductDO().setId(exists.getId());
        boolean changed = false;
        String name = buildProductName(sku);
        if (ObjUtil.notEqual(exists.getName(), name)) {
            updateObj.setName(name);
            changed = true;
        }
        Integer unit = resolveUnit(sku);
        if (ObjUtil.notEqual(exists.getUnit(), unit)) {
            updateObj.setUnit(unit);
            changed = true;
        }
        String description = buildDescription(sku);
        if (ObjUtil.notEqual(exists.getDescription(), description)) {
            updateObj.setDescription(description);
            changed = true;
        }
        if (ObjUtil.notEqual(exists.getWmsItemId(), sku.getItemId())) {
            updateObj.setWmsItemId(sku.getItemId());
            changed = true;
        }
        if (ObjUtil.notEqual(exists.getWmsSkuCode(), sku.getSkuCode())) {
            updateObj.setWmsSkuCode(sku.getSkuCode());
            changed = true;
        }
        if (!CrmProductDataSourceEnum.isWmsSync(exists.getDataSource())) {
            updateObj.setDataSource(CrmProductDataSourceEnum.WMS_SYNC.getDataSource());
            changed = true;
        }
        return changed ? updateObj : null;
    }

    /**
     * 构造新增对象；价格、分类、负责人、状态使用配置默认值（WMS 接口不返回价格）
     */
    private CrmProductDO buildCreateObj(CrmWmsProductSkuDTO sku, String no, Long categoryId) {
        BigDecimal price = wmsProductReadProperties.getDefaultPrice() != null
                ? wmsProductReadProperties.getDefaultPrice() : BigDecimal.ZERO;
        return CrmProductDO.builder()
                .name(buildProductName(sku))
                .no(no)
                .unit(resolveUnit(sku))
                .price(price)
                .status(CrmProductStatusEnum.ENABLE.getStatus())
                .categoryId(categoryId)
                .description(buildDescription(sku))
                .ownerUserId(wmsProductReadProperties.getDefaultOwnerUserId())
                .wmsItemId(sku.getItemId())
                .wmsSkuId(sku.getSkuId())
                .wmsSkuCode(sku.getSkuCode())
                .dataSource(CrmProductDataSourceEnum.WMS_SYNC.getDataSource())
                .build();
    }

    private String buildProductName(CrmWmsProductSkuDTO sku) {
        String base = StrUtil.blankToDefault(sku.getMaterialName(), sku.getMaterialCode());
        if (StrUtil.isBlank(base)) {
            base = StrUtil.format("WMS-{}-{}", sku.getItemId(), sku.getSkuId());
        }
        String name = StrUtil.isBlank(sku.getSkuName()) ? base : base + " " + sku.getSkuName();
        return StrUtil.subPre(name, MAX_NAME_LENGTH);
    }

    private String buildDescription(CrmWmsProductSkuDTO sku) {
        String description = StrUtil.format("WMS 同步：物料【{} {}】，规格【{} {}】",
                StrUtil.blankToDefault(sku.getMaterialCode(), "-"),
                StrUtil.blankToDefault(sku.getMaterialName(), "-"),
                StrUtil.blankToDefault(sku.getSkuCode(), "-"),
                StrUtil.blankToDefault(sku.getSkuName(), "-"));
        return StrUtil.subPre(description, MAX_DESCRIPTION_LENGTH);
    }

    /**
     * 产品编码：优先使用 WMS 规格编码，缺失时退化为物料编码 + 规格编号，保证唯一
     */
    private String resolveProductNo(CrmWmsProductSkuDTO sku) {
        if (StrUtil.isNotBlank(sku.getSkuCode())) {
            return StrUtil.subPre(sku.getSkuCode(), MAX_NO_LENGTH);
        }
        if (StrUtil.isNotBlank(sku.getMaterialCode())) {
            return StrUtil.subPre(sku.getMaterialCode() + "-" + sku.getSkuId(), MAX_NO_LENGTH);
        }
        return StrUtil.format("WMS-{}-{}", sku.getItemId(), sku.getSkuId());
    }

    /**
     * 单位：按名称匹配 CRM 产品单位字典，匹配不到时使用配置的兜底单位
     */
    private Integer resolveUnit(CrmWmsProductSkuDTO sku) {
        if (StrUtil.isNotBlank(sku.getUnitName())) {
            String value = DictFrameworkUtils.parseDictDataValue(DictTypeConstants.CRM_PRODUCT_UNIT,
                    sku.getUnitName().trim());
            if (NumberUtil.isNumber(value)) {
                return Integer.valueOf(value);
            }
        }
        return wmsProductReadProperties.getDefaultUnit();
    }

    private Long resolveCategoryId() {
        Long categoryId = wmsProductReadProperties.getDefaultCategoryId();
        if (categoryId != null && categoryId > 0) {
            if (productCategoryService.getProductCategory(categoryId) == null) {
                throw exception(PRODUCT_CATEGORY_NOT_EXISTS);
            }
            return categoryId;
        }
        return productCategoryService.getOrCreateRootCategoryId(
                StrUtil.blankToDefault(wmsProductReadProperties.getDefaultCategoryName(), "WMS 同步产品"));
    }

    private int resolvePageSize() {
        Integer pageSize = wmsProductReadProperties.getPageSize();
        if (pageSize == null || pageSize < 1) {
            return MAX_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void addMessage(CrmProductWmsSyncRespVO result, String message) {
        if (result.getMessages() == null) {
            result.setMessages(new ArrayList<>());
        }
        if (result.getMessages().size() < MAX_MESSAGE_COUNT) {
            result.getMessages().add(message);
        }
    }

}
