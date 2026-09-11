package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProCardRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.card.vo.MesProCardPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.card.MesProCardDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.pro.card.MesProCardService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 生产流转卡 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProCardApiImpl implements MesProCardApi {

    @Resource
    private MesProCardService cardService;
    @Resource
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesMdItemService itemService;

    @Override
    public CommonResult<MesProCardRespDTO> getCard(Long id) {
        MesProCardDO card = cardService.getCard(id);
        if (card == null) {
            return success(null);
        }
        return success(buildRespDTO(card));
    }

    @Override
    public CommonResult<PageResult<MesProCardRespDTO>> getCardPage(String code, Long workOrderId, Long itemId,
                                                                   String batchCode, Integer pageNo, Integer pageSize) {
        MesProCardPageReqVO pageReqVO = new MesProCardPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setWorkOrderId(workOrderId);
        pageReqVO.setItemId(itemId);
        pageReqVO.setBatchCode(batchCode);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProCardDO> pageResult = cardService.getCardPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesProCardRespDTO buildRespDTO(MesProCardDO card) {
        MesProCardRespDTO dto = BeanUtils.toBean(card, MesProCardRespDTO.class);
        if (card.getWorkOrderId() != null) {
            MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(card.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
                dto.setWorkOrderName(workOrder.getName());
            }
        }
        if (card.getItemId() != null) {
            MesMdItemDO item = itemService.getItem(card.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
        }
        return dto;
    }

    private List<MesProCardRespDTO> buildRespDTOList(List<MesProCardDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, MesProWorkOrderDO> workOrderMap = workOrderService.getWorkOrderMap(
                convertSet(list, MesProCardDO::getWorkOrderId));
        Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(convertSet(list, MesProCardDO::getItemId));
        return list.stream().map(card -> {
            MesProCardRespDTO dto = BeanUtils.toBean(card, MesProCardRespDTO.class);
            MesProWorkOrderDO workOrder = workOrderMap.get(card.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
                dto.setWorkOrderName(workOrder.getName());
            }
            MesMdItemDO item = itemMap.get(card.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
            return dto;
        }).toList();
    }

}
