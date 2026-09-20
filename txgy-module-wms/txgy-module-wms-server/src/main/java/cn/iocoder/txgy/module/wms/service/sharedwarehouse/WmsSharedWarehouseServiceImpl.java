package cn.iocoder.txgy.module.wms.service.sharedwarehouse;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehousePageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseRespVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse.WmsSharedWarehouseProductQueryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.sharedwarehouse.WmsSharedWarehouseQueryDO;
import cn.iocoder.txgy.module.wms.dal.mysql.sharedwarehouse.WmsSharedWarehouseMapper;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 共享仓库查询 Service 实现类。
 */
@Service
@Validated
public class WmsSharedWarehouseServiceImpl implements WmsSharedWarehouseService {

    @Resource
    private WmsSharedWarehouseMapper sharedWarehouseMapper;
    @Resource
    private WmsWarehouseService warehouseService;

    @Override
    public PageResult<WmsSharedWarehouseRespVO> getSharedWarehousePage(WmsSharedWarehousePageReqVO pageReqVO) {
        long offset = (long) (pageReqVO.getPageNo() - 1) * pageReqVO.getPageSize();
        List<WmsSharedWarehouseRespVO> list = sharedWarehouseMapper
                .selectWarehousePage(pageReqVO.getCode(), pageReqVO.getName(), pageReqVO.getSourceSystem(),
                        offset, pageReqVO.getPageSize())
                .stream().map(this::toWarehouseResp).collect(Collectors.toList());
        return new PageResult<>(list, sharedWarehouseMapper.selectWarehouseCount(
                pageReqVO.getCode(), pageReqVO.getName(), pageReqVO.getSourceSystem()));
    }

    @Override
    public List<WmsSharedWarehouseRespVO> getSharedWarehouseList(WmsSharedWarehousePageReqVO pageReqVO) {
        return sharedWarehouseMapper.selectWarehouseList(pageReqVO.getCode(), pageReqVO.getName(),
                        pageReqVO.getSourceSystem())
                .stream().map(this::toWarehouseResp).collect(Collectors.toList());
    }

    @Override
    public PageResult<WmsSharedWarehouseProductRespVO> getSharedWarehouseProductPage(
            WmsSharedWarehouseProductPageReqVO pageReqVO) {
        warehouseService.validateWarehouseExists(pageReqVO.getWarehouseId());
        long offset = (long) (pageReqVO.getPageNo() - 1) * pageReqVO.getPageSize();
        List<WmsSharedWarehouseProductRespVO> list = sharedWarehouseMapper
                .selectProductPage(pageReqVO.getWarehouseId(), pageReqVO.getItemName(),
                        pageReqVO.getSourceSystem(), offset, pageReqVO.getPageSize())
                .stream().map(this::toProductResp).collect(Collectors.toList());
        return new PageResult<>(list, sharedWarehouseMapper.selectProductCount(
                pageReqVO.getWarehouseId(), pageReqVO.getItemName(), pageReqVO.getSourceSystem()));
    }

    private WmsSharedWarehouseRespVO toWarehouseResp(WmsSharedWarehouseQueryDO row) {
        WmsSharedWarehouseRespVO resp = new WmsSharedWarehouseRespVO();
        resp.setWarehouseId(row.getWarehouseId());
        resp.setWarehouseCode(row.getWarehouseCode());
        resp.setWarehouseName(row.getWarehouseName());
        resp.setOwnerSystem("WMS");
        resp.setSourceSystems(split(row.getSourceSystems()));
        resp.setWmsSkuCount(row.getWmsSkuCount());
        resp.setMallSkuCount(row.getMallSkuCount());
        resp.setErpProductCount(row.getErpProductCount());
        resp.setPhysicalQuantity(row.getPhysicalQuantity());
        resp.setRelatedErpWarehouseNames(split(row.getRelatedErpWarehouseNames()));
        resp.setRemark(row.getRemark());
        return resp;
    }

    private WmsSharedWarehouseProductRespVO toProductResp(WmsSharedWarehouseProductQueryDO row) {
        WmsSharedWarehouseProductRespVO resp = new WmsSharedWarehouseProductRespVO();
        resp.setWarehouseId(row.getWarehouseId());
        resp.setWarehouseName(row.getWarehouseName());
        resp.setWmsSkuId(row.getWmsSkuId());
        resp.setWmsSkuName(row.getWmsSkuName());
        resp.setWmsItemId(row.getWmsItemId());
        resp.setWmsItemName(row.getWmsItemName());
        resp.setBarCode(row.getBarCode());
        resp.setPhysicalQuantity(row.getPhysicalQuantity());
        List<String> sourceSystems = new java.util.ArrayList<>();
        sourceSystems.add("WMS");
        if (row.getMallSkuId() != null) {
            sourceSystems.add("MALL");
        }
        if (row.getErpProductId() != null) {
            sourceSystems.add("ERP");
        }
        resp.setSourceSystems(sourceSystems);
        resp.setMallSkuId(row.getMallSkuId());
        resp.setMallSpuId(row.getMallSpuId());
        resp.setMallSpuName(row.getMallSpuName());
        resp.setMallCachedStock(row.getMallCachedStock());
        resp.setErpProductId(row.getErpProductId());
        resp.setErpProductName(row.getErpProductName());
        return resp;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

}
