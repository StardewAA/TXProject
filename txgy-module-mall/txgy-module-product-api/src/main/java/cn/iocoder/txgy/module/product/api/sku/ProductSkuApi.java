package cn.iocoder.txgy.module.product.api.sku;

import cn.iocoder.txgy.module.product.enums.ApiConstants;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuStockLockReqDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuStockStatusRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 商品 SKU API 接口
 *
 * @author LeeYan9
 * @since 2022-08-26
 */
@FeignClient(name = ApiConstants.NAME)
public interface ProductSkuApi {

    String PREFIX = ApiConstants.PREFIX + "/sku";

    /**
     * 查询 SKU 信息
     *
     * @param id SKU 编号
     * @return SKU 信息
     */
    @GetMapping(PREFIX + "/get")
    ProductSkuRespDTO getSku(@RequestParam("id") Long id);

    /**
     * 批量查询 SKU 数组
     *
     * @param ids SKU 编号列表
     * @return SKU 数组
     */
    @GetMapping(PREFIX + "/list")
    List<ProductSkuRespDTO> getSkuList(@RequestParam("ids") Collection<Long> ids);

    /**
     * 批量查询 SKU MAP
     *
     * @param ids SKU 编号列表
     * @return SKU MAP
     */
    default Map<Long, ProductSkuRespDTO> getSkuMap(Collection<Long> ids) {
        return convertMap(getSkuList(ids), ProductSkuRespDTO::getId);
    }

    /**
     * 批量查询 SKU 数组
     *
     * @param spuIds SPU 编号列表
     * @return SKU 数组
     */
    @GetMapping(PREFIX + "/list-by-spu-id")
    List<ProductSkuRespDTO> getSkuListBySpuId(@RequestParam("spuIds") Collection<Long> spuIds);

    /**
     * 更新 SKU 库存（增加 or 减少）
     *
     * @param updateStockReqDTO 更新请求
     */
    @PostMapping(PREFIX + "/update-stock")
    void updateSkuStock(@RequestBody ProductSkuUpdateStockReqDTO updateStockReqDTO);

    @PostMapping(PREFIX + "/reserve-stock")
    void reserveSkuStock(@RequestBody ProductSkuStockLockReqDTO reqDTO);

    @PostMapping(PREFIX + "/release-stock")
    void releaseSkuStock(@RequestBody ProductSkuStockLockReqDTO reqDTO);

    @PostMapping(PREFIX + "/outbound-stock")
    void outboundSkuStock(@RequestBody ProductSkuStockLockReqDTO reqDTO);

    @PostMapping(PREFIX + "/inbound-return-stock")
    void inboundReturnSkuStock(@RequestParam("returnNo") String returnNo,
                               @RequestBody ProductSkuStockLockReqDTO reqDTO);

    /** 批量查询订单的 WMS 库存履约状态，键为商城订单号。 */
    @GetMapping(PREFIX + "/wms-stock-status")
    Map<String, ProductSkuStockStatusRespDTO> getWmsStockStatus(@RequestParam("orderNos") Collection<String> orderNos);

}

