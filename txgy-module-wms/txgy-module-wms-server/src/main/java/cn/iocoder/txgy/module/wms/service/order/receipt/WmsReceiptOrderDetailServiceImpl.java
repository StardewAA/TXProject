package cn.iocoder.txgy.module.wms.service.order.receipt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDetailDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderDetailMapper;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.diffList;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

/**
 * WMS 入库单明细 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsReceiptOrderDetailServiceImpl implements WmsReceiptOrderDetailService {

    @Resource
    private WmsReceiptOrderDetailMapper receiptOrderDetailMapper;
    @Resource
    private WmsItemSkuService itemSkuService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.md.item.WmsItemService itemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReceiptOrderDetailList(Long orderId, WmsReceiptOrderSaveReqVO reqVO) {
        List<WmsReceiptOrderDetailDO> list = buildReceiptOrderDetailList(reqVO);
        if (CollUtil.isEmpty(list)) {
            return;
        }
        list.forEach(detail -> detail.setId(null).setOrderId(orderId));
        receiptOrderDetailMapper.insertBatch(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReceiptOrderDetailList(Long orderId, WmsReceiptOrderSaveReqVO reqVO) {
        // 第一步，对比新老数据，获得添加、修改、删除的列表
        List<WmsReceiptOrderDetailDO> oldList = receiptOrderDetailMapper.selectListByOrderId(orderId);
        List<WmsReceiptOrderDetailDO> list = buildReceiptOrderDetailList(reqVO);
        for (WmsReceiptOrderDetailDO detail : list) {
            oldList.stream().filter(old -> java.util.Objects.equals(old.getId(), detail.getId())
                    && java.util.Objects.equals(old.getSkuId(), detail.getSkuId())).findFirst().ifPresent(old -> {
                detail.setQualityRequiredSnapshot(old.getQualityRequiredSnapshot());
                detail.setSourceLineNo(old.getSourceLineNo()).setSourceSnapshotJson(old.getSourceSnapshotJson());
            });
        }
        List<WmsReceiptOrderDetailDO> newList = CollUtil.isEmpty(list) ? ListUtil.of() : list;
        List<List<WmsReceiptOrderDetailDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> ObjectUtil.equal(oldVal.getId(), newVal.getId()));

        // 第二步，批量添加、修改、删除
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            if (CollUtil.isNotEmpty(convertList(diffList.get(0), WmsReceiptOrderDetailDO::getId))) {
                throw exception(RECEIPT_ORDER_DETAIL_NOT_EXISTS);
            }
            diffList.get(0).forEach(detail -> detail.setOrderId(orderId));
            receiptOrderDetailMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            diffList.get(1).forEach(detail -> detail.setOrderId(orderId));
            receiptOrderDetailMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            receiptOrderDetailMapper.deleteByIds(convertList(diffList.get(2), WmsReceiptOrderDetailDO::getId));
        }
    }

    @Override
    public void deleteReceiptOrderDetailListByOrderId(Long orderId) {
        receiptOrderDetailMapper.deleteByOrderId(orderId);
    }

    @Override
    public List<WmsReceiptOrderDetailDO> getReceiptOrderDetailList(Long orderId) {
        return receiptOrderDetailMapper.selectListByOrderId(orderId);
    }

    @Override
    public List<WmsReceiptOrderDetailDO> getReceiptOrderDetailList(Collection<Long> orderIds) {
        if (CollUtil.isEmpty(orderIds)) {
            return ListUtil.of();
        }
        return receiptOrderDetailMapper.selectListByOrderIds(orderIds);
    }

    @Override
    public List<WmsReceiptOrderDetailDO> validateReceiptOrderDetailListExists(Long orderId) {
        List<WmsReceiptOrderDetailDO> details = receiptOrderDetailMapper.selectListByOrderId(orderId);
        if (CollUtil.isEmpty(details)) {
            throw exception(RECEIPT_ORDER_DETAIL_REQUIRED);
        }
        return details;
    }

    @Override
    public long getReceiptOrderDetailCountBySkuId(Long skuId) {
        return receiptOrderDetailMapper.selectCountBySkuId(skuId);
    }

    private List<WmsReceiptOrderDetailDO> buildReceiptOrderDetailList(WmsReceiptOrderSaveReqVO reqVO) {
        if (CollUtil.isEmpty(reqVO.getDetails())) {
            return ListUtil.of();
        }
        return convertList(reqVO.getDetails(), detail -> {
            // 校验 SKU 存在
            var sku = itemSkuService.validateItemSkuExistsAndItemEnabled(detail.getSkuId());
            // 构建对象
            WmsReceiptOrderDetailDO detailDO = BeanUtils.toBean(detail, WmsReceiptOrderDetailDO.class)
                    .setWarehouseId(reqVO.getWarehouseId());
            if (detail.getSourceSnapshotJson() != null) {
                var source = cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(detail.getSourceSnapshotJson(),
                        cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO.class);
                detailDO.setQualityRequiredSnapshot(source.getQualityRequiredSnapshot());
            } else {
                var item = sku == null ? null : itemService.getItem(sku.getItemId());
                detailDO.setQualityRequiredSnapshot(item == null ? null : item.getQualityRequired());
            }
            detailDO.setQualityImages(detail.getQualityImages() == null ? java.util.List.of() : detail.getQualityImages());
            validateAcceptanceQuantities(detailDO, false);
            fillDetailTotalPrice(detailDO);
            return detailDO;
        });
    }

    static void validateAcceptanceQuantities(WmsReceiptOrderDetailDO detail, boolean required) {
        var actual = detail.getActualQuantity(); var qualified = detail.getQualifiedQuantity(); var unqualified = detail.getUnqualifiedQuantity();
        if (!required && actual == null && qualified == null && unqualified == null) return;
        if (actual == null || qualified == null || unqualified == null || detail.getQuantity() == null
                || actual.signum() < 0 || qualified.signum() < 0 || unqualified.signum() < 0
                || actual.compareTo(detail.getQuantity()) > 0 || qualified.compareTo(actual) > 0
                || actual.compareTo(qualified.add(unqualified)) != 0
                || actual.stripTrailingZeros().scale() > 2 || qualified.stripTrailingZeros().scale() > 2 || unqualified.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("验收数量须非负、最多两位小数，实际数=合格数+不合格数，且不得超过申请数");
        }
    }

    private static void fillDetailTotalPrice(WmsReceiptOrderDetailDO detail) {
        if (detail.getTotalPrice() != null || detail.getQuantity() == null || detail.getPrice() == null) {
            return;
        }
        detail.setTotalPrice(MoneyUtils.priceMultiply(detail.getPrice(), detail.getQuantity()));
    }

}
