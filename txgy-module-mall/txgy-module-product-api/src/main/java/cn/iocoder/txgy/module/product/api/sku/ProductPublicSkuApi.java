package cn.iocoder.txgy.module.product.api.sku;

import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuLogisticsRespDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;

/**
 * 平台公开商品 SKU 归属解析 API。
 *
 * <p>该接口是跨租户平台能力的唯一商品归属入口。调用方只提交 SKU 编号，租户、企业、店铺和
 * SPU 归属全部由商品服务从受控的门户租户目录中解析，不能由用户端指定。</p>
 */
@FeignClient(name = ApiConstants.NAME)
public interface ProductPublicSkuApi {

    String PREFIX = ApiConstants.PREFIX + "/public-sku";

    /**
     * 批量解析已开放租户中的 SKU 公开信息及当前可售状态。
     *
     * <p>不在门户白名单、已关闭租户和原公共租户 3000 的 SKU 不会返回。</p>
     */
    @PostMapping(PREFIX + "/ownership-list")
    List<ProductPublicSkuRespDTO> getOwnershipList(@RequestBody Collection<Long> skuIds);

    /**
     * 批量读取平台结算所需的 SKU 物流快照。
     *
     * <p>请求体只能包含 SKU 编号；租户、店铺、服务端价格、库存与物流配置均由商品服务解析。
     * 不可售记录会保留归属和原因，但敏感结算字段为空。</p>
     */
    @PostMapping(PREFIX + "/logistics-list")
    List<ProductPublicSkuLogisticsRespDTO> getLogisticsList(@RequestBody Collection<Long> skuIds);

}
