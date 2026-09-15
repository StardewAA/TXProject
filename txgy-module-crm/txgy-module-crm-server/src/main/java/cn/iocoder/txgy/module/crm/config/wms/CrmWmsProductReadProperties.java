package cn.iocoder.txgy.module.crm.config.wms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * CRM 读取 WMS 商品（进仓产品）并同步导入 CRM 产品管理的配置。
 *
 * <p>mode=mock：本地直接读取同库 WMS 表，便于本地联调；
 * mode=remote：调用 WMS 交付文档中的 {@code GET /admin-api/wms/crm/products/page} 只读接口。</p>
 *
 * <p>WMS 接口只返回商品编码、名称、规格、单位，不返回价格，也不包含库存承诺。
 * 因此同步写入 CRM 产品时，价格、分类、负责人、状态使用本配置的默认值，不覆盖已有人工维护的数据。</p>
 */
@Data
@ConfigurationProperties(prefix = "txgy.integration.wms.crm-product")
public class CrmWmsProductReadProperties {

    /**
     * 运行模式：mock / remote
     */
    private String mode = "mock";

    /**
     * remote 模式下调用 WMS 接口使用的 Authorization 头，示例：Bearer xxx
     *
     * <p>建议使用仅具备 {@code wms:item:query} 查询权限的专用账号，由部署方注入，不要写入前端。</p>
     */
    private String authorization;

    /**
     * 允许同步的租户编号；为空或 0 表示不限制，按当前租户上下文同步。
     *
     * <p>remote 模式下 WMS 只接受令牌所属租户的数据，配置后其它租户直接跳过同步。</p>
     */
    private Long tenantId;

    /**
     * 每次向 WMS 拉取的条数，WMS 限制 1-200
     */
    private Integer pageSize = 200;

    /**
     * 同步产品的默认分类编号，为空或 0 表示按 {@link #defaultCategoryName} 查找或创建根分类
     */
    private Long defaultCategoryId;

    /**
     * 同步产品的默认分类名称，用于自动创建分类
     */
    private String defaultCategoryName = "WMS 同步产品";

    /**
     * 同步产品的默认负责人用户编号
     */
    private Long defaultOwnerUserId = 1L;

    /**
     * WMS 单位名称无法匹配 CRM 产品单位字典（crm_product_unit）时使用的兜底字典值，默认 1 = 个
     */
    private Integer defaultUnit = 1;

    /**
     * 同步产品的默认价格，单位：元。仅新建产品时使用，不覆盖已有产品的价格
     */
    private BigDecimal defaultPrice = BigDecimal.ZERO;

}
