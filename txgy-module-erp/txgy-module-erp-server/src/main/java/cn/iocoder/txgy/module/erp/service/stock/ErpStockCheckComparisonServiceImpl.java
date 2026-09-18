package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.check.ErpStockCheckComparisonRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockCheckDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockCheckItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockMirrorDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMirrorMapper;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.STOCK_CHECK_NOT_EXISTS;

/**
 * ERP 盘点单三方比对 Service 实现类
 *
 * <p>三方取值：</p>
 * <ul>
 *     <li>ERP 账面：{@code erp_stock}（比较时刻实时值）+ 盘点单上的账面快照</li>
 *     <li>ERP 实盘：{@code erp_stock_check_item.actual_count}</li>
 *     <li>WMS 实物：{@code erp_stock_mirror}（第 2 批旁路同步落地的镜像）</li>
 * </ul>
 *
 * <p>WMS 镜像缺失时本接口不会失败，而是把该行标记为「无镜像数据」，
 * 三方自动降级为两方；这样在 WMS 主数据尚未补齐的阶段也能先用起来，
 * 补齐后无需改代码即可显示真实的三方对比。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpStockCheckComparisonServiceImpl implements ErpStockCheckComparisonService {

    @Resource
    private ErpStockCheckService stockCheckService;
    @Resource
    private ErpStockMapper stockMapper;
    @Resource
    private ErpStockMirrorMapper stockMirrorMapper;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpWarehouseService warehouseService;

    @Override
    public ErpStockCheckComparisonRespVO getComparison(Long checkId) {
        ErpStockCheckDO stockCheck = stockCheckService.getStockCheck(checkId);
        if (stockCheck == null) {
            throw exception(STOCK_CHECK_NOT_EXISTS);
        }
        List<ErpStockCheckItemDO> items = stockCheckService.getStockCheckItemListByCheckId(checkId);

        // 批量取产品与仓库信息，避免逐行查询
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                convertSet(items, ErpStockCheckItemDO::getProductId));
        Map<Long, ErpWarehouseDO> warehouseMap = warehouseService.getWarehouseMap(
                convertSet(items, ErpStockCheckItemDO::getWarehouseId));

        ErpStockCheckComparisonRespVO result = new ErpStockCheckComparisonRespVO()
                .setCheckId(stockCheck.getId()).setCheckNo(stockCheck.getNo())
                .setCheckTime(stockCheck.getCheckTime()).setStatus(stockCheck.getStatus())
                .setTotalLines(items.size());

        List<ErpStockCheckComparisonRespVO.Line> lines = new ArrayList<>(items.size());
        int checkDiffLines = 0, mirrorLines = 0, wmsAdjustLines = 0, bookDriftLines = 0;
        for (ErpStockCheckItemDO item : items) {
            ErpStockCheckComparisonRespVO.Line line = new ErpStockCheckComparisonRespVO.Line()
                    .setItemId(item.getId()).setProductId(item.getProductId())
                    .setWarehouseId(item.getWarehouseId()).setRemark(item.getRemark());
            MapUtils.findAndThen(productMap, item.getProductId(), product -> line
                    // 统一物料编码优先，缺失时退回条码，保证前端总有可读编码
                    .setProductCode(product.getMaterialCode() != null ? product.getMaterialCode() : product.getBarCode())
                    .setProductName(product.getName()).setProductUnitName(product.getUnitName()));
            MapUtils.findAndThen(warehouseMap, item.getWarehouseId(), warehouse -> line
                    .setWarehouseName(warehouse.getName()).setWmsWarehouseCode(warehouse.getWmsWarehouseCode()));

            BigDecimal snapshot = nvl(item.getStockCount());
            BigDecimal actual = nvl(item.getActualCount());
            ErpStockDO stock = stockMapper.selectByProductIdAndWarehouseId(item.getProductId(), item.getWarehouseId());
            BigDecimal book = stock == null ? BigDecimal.ZERO : nvl(stock.getCount());
            ErpStockMirrorDO mirror = stockMirrorMapper.selectByProductAndWarehouse(
                    item.getProductId(), item.getWarehouseId());

            line.setSnapshotBookCount(snapshot).setActualCount(actual).setBookCount(book);
            line.setCheckDiff(actual.subtract(snapshot));
            BigDecimal bookDrift = book.subtract(snapshot);
            line.setBookDrift(bookDrift).setBookDriftDetected(bookDrift.signum() != 0);

            if (mirror == null) {
                // 无 WMS 镜像：明确标记，而不是当作 0，避免把「没数据」误判成「实物为 0」
                line.setMirrorAvailable(false).setWmsAdjustRequired(false);
            } else {
                BigDecimal wms = nvl(mirror.getCount());
                BigDecimal wmsVsActual = wms.subtract(actual);
                line.setWmsCount(wms).setWmsSyncTime(mirror.getSyncTime())
                        .setWmsVsActual(wmsVsActual).setWmsVsBook(wms.subtract(book))
                        .setMirrorAvailable(true).setWmsAdjustRequired(wmsVsActual.signum() != 0);
            }

            if (line.getCheckDiff().signum() != 0) {
                checkDiffLines++;
            }
            if (Boolean.TRUE.equals(line.getMirrorAvailable())) {
                mirrorLines++;
            }
            if (Boolean.TRUE.equals(line.getWmsAdjustRequired())) {
                wmsAdjustLines++;
            }
            if (Boolean.TRUE.equals(line.getBookDriftDetected())) {
                bookDriftLines++;
            }
            lines.add(line);
        }

        return result.setLines(lines)
                .setCheckDiffLineCount(checkDiffLines)
                .setMirrorCoveredLineCount(mirrorLines)
                .setWmsAdjustLineCount(wmsAdjustLines)
                .setBookDriftLineCount(bookDriftLines)
                .setMirrorAvailable(mirrorLines > 0);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
