package cn.iocoder.txgy.module.erp.service.stock;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.move.ErpStockMovePageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.move.ErpStockMoveSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockMoveDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockMoveItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMoveItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpStockMoveMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockRecordBizTypeEnum;
import cn.iocoder.txgy.module.erp.service.approval.ErpApprovalNotificationService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.stock.bo.ErpStockRecordCreateReqBO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

// TODO 芋艿：记录操作日志

/**
 * ERP 库存调拨单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpStockMoveServiceImpl implements ErpStockMoveService {

    private static final int STATUS_WAIT_SOURCE = 10;
    private static final int STATUS_WAIT_TARGET = 15;
    private static final int STATUS_COMPLETED = 20;

    @Resource
    private ErpStockMoveMapper stockMoveMapper;
    @Resource
    private ErpStockMoveItemMapper stockMoveItemMapper;

    @Resource
    private ErpNoRedisDAO noRedisDAO;

    @Resource
    private ErpProductService productService;
    @Resource
    private ErpWarehouseService warehouseService;
    @Resource
    private ErpStockRecordService stockRecordService;
    @Resource
    private ErpApprovalNotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStockMove(ErpStockMoveSaveReqVO createReqVO) {
        // 1.1 校验出库项的有效性
        List<ErpStockMoveItemDO> stockMoveItems = validateStockMoveItems(createReqVO.getItems());
        MoveWarehouseContext warehouseContext = buildWarehouseContext(stockMoveItems);
        // 1.2 生成调拨单号，并校验唯一性
        String no = noRedisDAO.generate(ErpNoRedisDAO.STOCK_MOVE_NO_PREFIX);
        if (stockMoveMapper.selectByNo(no) != null) {
            throw exception(STOCK_MOVE_NO_EXISTS);
        }

        // 2.1 插入出库单
        ErpStockMoveDO stockMove = BeanUtils.toBean(createReqVO, ErpStockMoveDO.class, in -> in
                .setNo(no).setStatus(STATUS_WAIT_SOURCE)
                .setTotalCount(getSumValue(stockMoveItems, ErpStockMoveItemDO::getCount, BigDecimal::add))
                .setTotalPrice(getSumValue(stockMoveItems, ErpStockMoveItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO)));
        stockMoveMapper.insert(stockMove);
        // 2.2 插入出库单项
        stockMoveItems.forEach(o -> o.setMoveId(stockMove.getId()));
        stockMoveItemMapper.insertBatch(stockMoveItems);
        notifyWarehouseManagers(stockMove, warehouseContext,
                "已发起，等待调出仓库负责人确认，库存尚未变动");
        return stockMove.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStockMove(ErpStockMoveSaveReqVO updateReqVO) {
        // 1.1 校验存在
        ErpStockMoveDO stockMove = validateStockMoveExists(updateReqVO.getId());
        if (!Integer.valueOf(STATUS_WAIT_SOURCE).equals(stockMove.getStatus())) {
            throw exception(STOCK_MOVE_UPDATE_FAIL_APPROVE, stockMove.getNo());
        }
        // 1.2 校验出库项的有效性
        List<ErpStockMoveItemDO> stockMoveItems = validateStockMoveItems(updateReqVO.getItems());
        buildWarehouseContext(stockMoveItems); // 双确认依赖双方仓库负责人，保存时即阻止无负责人数据

        // 2.1 更新出库单
        ErpStockMoveDO updateObj = BeanUtils.toBean(updateReqVO, ErpStockMoveDO.class, in -> in
                .setTotalCount(getSumValue(stockMoveItems, ErpStockMoveItemDO::getCount, BigDecimal::add))
                .setTotalPrice(getSumValue(stockMoveItems, ErpStockMoveItemDO::getTotalPrice, BigDecimal::add)));
        stockMoveMapper.updateById(updateObj);
        // 2.2 更新出库单项
        updateStockMoveItemList(updateReqVO.getId(), stockMoveItems);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStockMoveStatus(Long id, Integer status) {
        boolean approve = ErpAuditStatus.APPROVE.getStatus().equals(status);
        // 1.1 校验存在
        ErpStockMoveDO stockMove = validateStockMoveExists(id);
        // 1.2 校验状态
        if (approve || !Integer.valueOf(STATUS_COMPLETED).equals(stockMove.getStatus())
                || !Integer.valueOf(STATUS_WAIT_SOURCE).equals(status)) {
            throw exception(approve ? STOCK_MOVE_APPROVE_FAIL : STOCK_MOVE_PROCESS_FAIL);
        }

        // 2. 更新状态
        int updateCount = stockMoveMapper.resetConfirmationByIdAndStatus(id, stockMove.getStatus(), status);
        if (updateCount == 0) {
            throw exception(approve ? STOCK_MOVE_APPROVE_FAIL : STOCK_MOVE_PROCESS_FAIL);
        }

        // 3. 变更库存
        List<ErpStockMoveItemDO> stockMoveItems = stockMoveItemMapper.selectListByMoveId(id);
        Integer fromBizType = ErpStockRecordBizTypeEnum.MOVE_OUT_CANCEL.getType();
        Integer toBizType = ErpStockRecordBizTypeEnum.MOVE_IN_CANCEL.getType();
        stockMoveItems.forEach(stockMoveItem -> {
            BigDecimal fromCount = stockMoveItem.getCount();
            BigDecimal toCount = stockMoveItem.getCount().negate();
            stockRecordService.createStockRecord(new ErpStockRecordCreateReqBO(
                    stockMoveItem.getProductId(), stockMoveItem.getFromWarehouseId(), fromCount,
                    fromBizType, stockMoveItem.getMoveId(), stockMoveItem.getId(), stockMove.getNo()));
            stockRecordService.createStockRecord(new ErpStockRecordCreateReqBO(
                    stockMoveItem.getProductId(), stockMoveItem.getToWarehouseId(), toCount,
                    toBizType, stockMoveItem.getMoveId(), stockMoveItem.getId(), stockMove.getNo()));
        });
        notifyWarehouseManagers(stockMove, buildWarehouseContext(stockMoveItems),
                "已反确认，原库存变动已冲销，调拨重新等待确认");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSource(Long id, Long userId) {
        ErpStockMoveDO stockMove = validateStockMoveExists(id);
        if (!Integer.valueOf(STATUS_WAIT_SOURCE).equals(stockMove.getStatus())) {
            throw exception(STOCK_MOVE_CONFIRM_STATUS_INVALID);
        }
        List<ErpStockMoveItemDO> items = stockMoveItemMapper.selectListByMoveId(id);
        MoveWarehouseContext context = buildWarehouseContext(items);
        if (!context.sourceManagerIds().contains(userId)) {
            throw exception(STOCK_MOVE_CONFIRM_USER_INVALID);
        }
        int updated = stockMoveMapper.updateByIdAndStatus(id, STATUS_WAIT_SOURCE,
                new ErpStockMoveDO().setStatus(STATUS_WAIT_TARGET)
                        .setSourceConfirmedBy(userId).setSourceConfirmedAt(LocalDateTime.now()));
        if (updated == 0) {
            throw exception(STOCK_MOVE_CONFIRM_STATUS_INVALID);
        }
        notifyWarehouseManagers(stockMove, context,
                "调出仓库已确认，等待调入仓库负责人确认，库存尚未变动");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmTarget(Long id, Long userId) {
        ErpStockMoveDO stockMove = validateStockMoveExists(id);
        if (!Integer.valueOf(STATUS_WAIT_TARGET).equals(stockMove.getStatus())) {
            throw exception(STOCK_MOVE_CONFIRM_STATUS_INVALID);
        }
        List<ErpStockMoveItemDO> items = stockMoveItemMapper.selectListByMoveId(id);
        MoveWarehouseContext context = buildWarehouseContext(items);
        if (!context.targetManagerIds().contains(userId)) {
            throw exception(STOCK_MOVE_CONFIRM_USER_INVALID);
        }
        int updated = stockMoveMapper.updateByIdAndStatus(id, STATUS_WAIT_TARGET,
                new ErpStockMoveDO().setStatus(STATUS_COMPLETED)
                        .setTargetConfirmedBy(userId).setTargetConfirmedAt(LocalDateTime.now()));
        if (updated == 0) {
            throw exception(STOCK_MOVE_CONFIRM_STATUS_INVALID);
        }
        changeStock(items, stockMove, true);
        notifyWarehouseManagers(stockMove, context,
                "双方确认完成，库存已完成调出和调入变更");
    }

    private void changeStock(List<ErpStockMoveItemDO> items, ErpStockMoveDO move, boolean approve) {
        Integer fromBizType = approve ? ErpStockRecordBizTypeEnum.MOVE_OUT.getType()
                : ErpStockRecordBizTypeEnum.MOVE_OUT_CANCEL.getType();
        Integer toBizType = approve ? ErpStockRecordBizTypeEnum.MOVE_IN.getType()
                : ErpStockRecordBizTypeEnum.MOVE_IN_CANCEL.getType();
        items.forEach(item -> {
            BigDecimal fromCount = approve ? item.getCount().negate() : item.getCount();
            BigDecimal toCount = approve ? item.getCount() : item.getCount().negate();
            stockRecordService.createStockRecord(new ErpStockRecordCreateReqBO(item.getProductId(),
                    item.getFromWarehouseId(), fromCount, fromBizType, item.getMoveId(), item.getId(), move.getNo()));
            stockRecordService.createStockRecord(new ErpStockRecordCreateReqBO(item.getProductId(),
                    item.getToWarehouseId(), toCount, toBizType, item.getMoveId(), item.getId(), move.getNo()));
        });
    }

    private MoveWarehouseContext buildWarehouseContext(List<ErpStockMoveItemDO> items) {
        Set<Long> sourceIds = convertSet(items, ErpStockMoveItemDO::getFromWarehouseId);
        Set<Long> targetIds = convertSet(items, ErpStockMoveItemDO::getToWarehouseId);
        Set<Long> allIds = new LinkedHashSet<>(sourceIds);
        allIds.addAll(targetIds);
        Map<Long, ErpWarehouseDO> warehouses = warehouseService.getWarehouseMap(allIds);
        Set<Long> sourceManagers = getManagerIds(sourceIds, warehouses);
        Set<Long> targetManagers = getManagerIds(targetIds, warehouses);
        return new MoveWarehouseContext(sourceManagers, targetManagers,
                joinWarehouseNames(sourceIds, warehouses), joinWarehouseNames(targetIds, warehouses));
    }

    private Set<Long> getManagerIds(Set<Long> warehouseIds, Map<Long, ErpWarehouseDO> warehouses) {
        Set<Long> result = new LinkedHashSet<>();
        for (Long warehouseId : warehouseIds) {
            ErpWarehouseDO warehouse = warehouses.get(warehouseId);
            if (warehouse == null || warehouse.getPrincipalUserId() == null) {
                throw exception(STOCK_MOVE_WAREHOUSE_MANAGER_MISSING,
                        warehouse != null ? warehouse.getName() : warehouseId);
            }
            result.add(warehouse.getPrincipalUserId());
        }
        return result;
    }

    private String joinWarehouseNames(Set<Long> ids, Map<Long, ErpWarehouseDO> warehouses) {
        return ids.stream().map(warehouses::get).map(ErpWarehouseDO::getName).distinct()
                .reduce((left, right) -> left + "、" + right).orElse("-");
    }

    private void notifyWarehouseManagers(ErpStockMoveDO move, MoveWarehouseContext context, String action) {
        Set<Long> recipients = new LinkedHashSet<>(context.sourceManagerIds());
        recipients.addAll(context.targetManagerIds());
        notificationService.sendStockMoveAfterCommit(recipients, move.getNo(), move.getId(), action,
                context.sourceWarehouseNames(), context.targetWarehouseNames());
    }

    private record MoveWarehouseContext(Set<Long> sourceManagerIds, Set<Long> targetManagerIds,
                                        String sourceWarehouseNames, String targetWarehouseNames) {}

    private List<ErpStockMoveItemDO> validateStockMoveItems(List<ErpStockMoveSaveReqVO.Item> list) {
        // 1.1 校验产品存在
        List<ErpProductDO> productList = productService.validProductList(
                convertSet(list, ErpStockMoveSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(productList, ErpProductDO::getId);
        // 1.2 校验仓库存在
        warehouseService.validWarehouseList(convertSetByFlatMap(list,
                item -> Stream.of(item.getFromWarehouseId(),  item.getToWarehouseId())));
        // 2. 转化为 ErpStockMoveItemDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, ErpStockMoveItemDO.class, item -> item
                .setProductUnitId(productMap.get(item.getProductId()).getUnitId())
                .setTotalPrice(MoneyUtils.priceMultiply(item.getProductPrice(), item.getCount()))));
    }

    private void updateStockMoveItemList(Long id, List<ErpStockMoveItemDO> newList) {
        // 第一步，对比新老数据，获得添加、修改、删除的列表
        List<ErpStockMoveItemDO> oldList = stockMoveItemMapper.selectListByMoveId(id);
        List<List<ErpStockMoveItemDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));

        // 第二步，批量添加、修改、删除
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setMoveId(id));
            stockMoveItemMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            stockMoveItemMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            stockMoveItemMapper.deleteByIds(convertList(diffList.get(2), ErpStockMoveItemDO::getId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStockMove(List<Long> ids) {
        // 1. 校验不处于已审批
        List<ErpStockMoveDO> stockMoves = stockMoveMapper.selectByIds(ids);
        if (CollUtil.isEmpty(stockMoves)) {
            return;
        }
        stockMoves.forEach(stockMove -> {
            if (!Integer.valueOf(STATUS_WAIT_SOURCE).equals(stockMove.getStatus())) {
                throw exception(STOCK_MOVE_DELETE_FAIL_APPROVE, stockMove.getNo());
            }
        });

        // 2. 遍历删除，并记录操作日志
        stockMoves.forEach(stockMove -> {
            // 2.1 删除出库单
            stockMoveMapper.deleteById(stockMove.getId());
            // 2.2 删除出库单项
            stockMoveItemMapper.deleteByMoveId(stockMove.getId());
        });
    }

    private ErpStockMoveDO validateStockMoveExists(Long id) {
        ErpStockMoveDO stockMove = stockMoveMapper.selectById(id);
        if (stockMove == null) {
            throw exception(STOCK_MOVE_NOT_EXISTS);
        }
        return stockMove;
    }

    @Override
    public ErpStockMoveDO getStockMove(Long id) {
        return stockMoveMapper.selectById(id);
    }

    @Override
    public PageResult<ErpStockMoveDO> getStockMovePage(ErpStockMovePageReqVO pageReqVO) {
        return stockMoveMapper.selectPage(pageReqVO);
    }

    // ==================== 出库项 ====================

    @Override
    public List<ErpStockMoveItemDO> getStockMoveItemListByMoveId(Long moveId) {
        return stockMoveItemMapper.selectListByMoveId(moveId);
    }

    @Override
    public List<ErpStockMoveItemDO> getStockMoveItemListByMoveIds(Collection<Long> moveIds) {
        if (CollUtil.isEmpty(moveIds)) {
            return Collections.emptyList();
        }
        return stockMoveItemMapper.selectListByMoveIds(moveIds);
    }

}
