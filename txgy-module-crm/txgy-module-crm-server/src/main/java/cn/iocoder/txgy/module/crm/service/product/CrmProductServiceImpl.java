package cn.iocoder.txgy.module.crm.service.product;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.txgy.module.crm.config.wms.CrmWmsProductReadProperties;
import cn.iocoder.txgy.module.crm.controller.admin.product.vo.product.CrmProductPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.product.vo.product.CrmProductSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductDO;
import cn.iocoder.txgy.module.crm.enums.DictTypeConstants;
import cn.iocoder.txgy.module.crm.enums.product.CrmProductDataSourceEnum;
import cn.iocoder.txgy.module.crm.enums.product.CrmProductStatusEnum;
import cn.iocoder.txgy.module.crm.service.wms.CrmWmsProductReadAdapter;
import cn.iocoder.txgy.module.crm.service.wms.CrmWmsProductSkuDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.PRODUCT_WMS_READ_ONLY;

/**
 * CRM 产品 Service 实现类
 *
 * <p>产品数据全部实时取自 WMS 仓库库存接口，不在 CRM 本地落库：
 * WMS 存在的产品才展示，WMS 新增、修改、删除后 CRM 立即同步；不支持手动新增、修改、删除产品。
 * 产品的唯一标识直接使用 WMS 规格编号（skuId）。</p>
 *
 * @author ZanGe丶
 */
@Service
@Validated
@Slf4j
public class CrmProductServiceImpl implements CrmProductService {

    /**
     * 全量拉取（下拉场景）时的最大页数，避免 WMS 返回的总数异常导致死循环
     */
    private static final int MAX_PAGE_COUNT = 500;
    /**
     * WMS 接口每页条数上限
     */
    private static final int MAX_PAGE_SIZE = 200;
    /**
     * 产品名称长度上限
     */
    private static final int MAX_NAME_LENGTH = 100;
    /**
     * 产品编码长度上限
     */
    private static final int MAX_NO_LENGTH = 64;
    /**
     * 产品描述长度上限
     */
    private static final int MAX_DESCRIPTION_LENGTH = 100;

    @Resource
    private CrmWmsProductReadAdapter crmWmsProductReadAdapter;
    @Resource
    private CrmWmsProductReadProperties wmsProductReadProperties;

    // ========== 产品维护：全部由 WMS 实时同步，CRM 不支持手动维护 ==========

    @Override
    public Long createProduct(CrmProductSaveReqVO createReqVO) {
        throw exception(PRODUCT_WMS_READ_ONLY);
    }

    @Override
    public void updateProduct(CrmProductSaveReqVO updateReqVO) {
        throw exception(PRODUCT_WMS_READ_ONLY);
    }

    @Override
    public void deleteProduct(Long id) {
        throw exception(PRODUCT_WMS_READ_ONLY);
    }

    // ========== 产品查询：实时读取 WMS 仓库库存 ==========

    @Override
    public CrmProductDO getProduct(Long id) {
        if (id == null) {
            return null;
        }
        CrmWmsProductSkuDTO sku = crmWmsProductReadAdapter.getBySkuId(id);
        return sku == null ? null : buildProduct(sku);
    }

    @Override
    public List<CrmProductDO> getProductList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return convertList(crmWmsProductReadAdapter.getBySkuIds(ids), this::buildProduct);
    }

    @Override
    public PageResult<CrmProductDO> getProductPage(CrmProductPageReqVO pageReqVO) {
        // 导出等不分页场景：逐页拉取 WMS 数据后一次性返回
        if (PageParam.PAGE_SIZE_NONE.equals(pageReqVO.getPageSize())) {
            List<CrmProductDO> all = listAll(pageReqVO.getName());
            return new PageResult<>(all, (long) all.size());
        }
        PageResult<CrmWmsProductSkuDTO> page = crmWmsProductReadAdapter.getProductPage(
                pageReqVO.getPageNo(), pageReqVO.getPageSize(), pageReqVO.getName());
        if (CollUtil.isEmpty(page.getList())) {
            return new PageResult<>(Collections.emptyList(), page.getTotal());
        }
        return new PageResult<>(convertList(page.getList(), this::buildProduct), page.getTotal());
    }

    @Override
    public Long getProductByCategoryId(Long categoryId) {
        // 产品全部来自 WMS，不再归属 CRM 本地产品分类，恒返回 0，避免分类被"已关联产品"拦截
        return 0L;
    }

    @Override
    public List<CrmProductDO> getProductListByStatus(Integer status) {
        // WMS 只返回启用商品；查询下架产品时直接返回空列表
        if (status != null && !CrmProductStatusEnum.ENABLE.getStatus().equals(status)) {
            return Collections.emptyList();
        }
        return listAll(null);
    }

    /**
     * 逐页拉取 WMS 数据，用于下拉、导出等需要全量的场景
     */
    private List<CrmProductDO> listAll(String name) {
        List<CrmProductDO> list = new ArrayList<>();
        int pageSize = resolvePageSize();
        for (int pageNo = 1; pageNo <= MAX_PAGE_COUNT; pageNo++) {
            PageResult<CrmWmsProductSkuDTO> page = crmWmsProductReadAdapter.getProductPage(pageNo, pageSize, name);
            if (CollUtil.isEmpty(page.getList())) {
                break;
            }
            page.getList().forEach(sku -> list.add(buildProduct(sku)));
            if (page.getTotal() != null && page.getTotal() <= (long) pageNo * pageSize) {
                break;
            }
        }
        return list;
    }

    @Override
    public List<CrmProductDO> validProductList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<CrmProductDO> list = getProductList(ids);
        Map<Long, CrmProductDO> productMap = convertMap(list, CrmProductDO::getId);
        // WMS 中不存在的产品（已删除）直接报错，避免商机、合同、报价引用了无效产品
        for (Long id : ids) {
            if (productMap.get(id) == null) {
                throw exception(PRODUCT_NOT_EXISTS);
            }
        }
        return list;
    }

    // ========== WMS 数据 -> CRM 产品 ==========

    private CrmProductDO buildProduct(CrmWmsProductSkuDTO sku) {
        return CrmProductDO.builder()
                .id(sku.getSkuId())
                .name(buildProductName(sku))
                .no(resolveProductNo(sku))
                .unit(resolveUnit(sku))
                .price(resolvePrice(sku))
                .status(CrmProductStatusEnum.ENABLE.getStatus())
                .description(buildDescription(sku))
                .ownerUserId(wmsProductReadProperties.getDefaultOwnerUserId())
                .wmsItemId(sku.getItemId())
                .wmsSkuId(sku.getSkuId())
                .wmsSkuCode(sku.getSkuCode())
                .dataSource(CrmProductDataSourceEnum.WMS_SYNC.getDataSource())
                .stockQuantity(sku.getQuantity())
                .unitName(sku.getUnitName())
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
        String description = StrUtil.format("WMS 实时：物料【{} {}】，规格【{} {}】，库存【{}】",
                StrUtil.blankToDefault(sku.getMaterialCode(), "-"),
                StrUtil.blankToDefault(sku.getMaterialName(), "-"),
                StrUtil.blankToDefault(sku.getSkuCode(), "-"),
                StrUtil.blankToDefault(sku.getSkuName(), "-"),
                sku.getQuantity() == null ? "0" : sku.getQuantity().toPlainString());
        return StrUtil.subPre(description, MAX_DESCRIPTION_LENGTH);
    }

    /**
     * 产品编码：优先使用 WMS 规格编码，缺失时退化为物料编码 + 规格编号
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
     * 单位：按名称匹配 CRM 产品单位字典，匹配不到时使用配置的兜底字典值
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

    /**
     * 价格：优先使用 WMS 销售单价，缺失时使用配置的默认价格
     */
    private BigDecimal resolvePrice(CrmWmsProductSkuDTO sku) {
        if (sku.getSellingPrice() != null) {
            return sku.getSellingPrice();
        }
        return wmsProductReadProperties.getDefaultPrice() != null
                ? wmsProductReadProperties.getDefaultPrice() : BigDecimal.ZERO;
    }

    private int resolvePageSize() {
        Integer pageSize = wmsProductReadProperties.getPageSize();
        if (pageSize == null || pageSize < 1) {
            return MAX_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

}
