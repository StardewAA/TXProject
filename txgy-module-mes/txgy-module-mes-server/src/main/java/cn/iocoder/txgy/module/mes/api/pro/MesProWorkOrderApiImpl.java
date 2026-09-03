package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkOrderRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.client.MesMdClientDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.service.md.client.MesMdClientService;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 生产工单 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProWorkOrderApiImpl implements MesProWorkOrderApi {

    @Resource
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesMdItemService itemService;
    @Resource
    private MesMdClientService clientService;

    @Override
    public CommonResult<MesProWorkOrderRespDTO> getWorkOrder(Long id) {
        MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(id);
        if (workOrder == null) {
            return success(null);
        }
        return success(buildRespDTO(workOrder));
    }

    @Override
    public CommonResult<MesProWorkOrderRespDTO> getWorkOrderByCode(String code) {
        MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(code);
        if (workOrder == null) {
            return success(null);
        }
        return success(buildRespDTO(workOrder));
    }

    @Override
    public CommonResult<PageResult<MesProWorkOrderRespDTO>> getWorkOrderPage(String code, String name,
                                                                             Integer status, Integer pageNo, Integer pageSize) {
        MesProWorkOrderPageReqVO pageReqVO = new MesProWorkOrderPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setName(name);
        pageReqVO.setStatus(status);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProWorkOrderDO> pageResult = workOrderService.getWorkOrderPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesProWorkOrderRespDTO buildRespDTO(MesProWorkOrderDO workOrder) {
        MesProWorkOrderRespDTO dto = BeanUtils.toBean(workOrder, MesProWorkOrderRespDTO.class);
        // 组装产品名称/编码、客户名称
        if (workOrder.getProductId() != null) {
            MesMdItemDO item = itemService.getItem(workOrder.getProductId());
            if (item != null) {
                dto.setProductName(item.getName());
                dto.setProductCode(item.getCode());
            }
        }
        if (workOrder.getClientId() != null) {
            MesMdClientDO client = clientService.getClient(workOrder.getClientId());
            if (client != null) {
                dto.setClientName(client.getName());
            }
        }
        return dto;
    }

    private List<MesProWorkOrderRespDTO> buildRespDTOList(List<MesProWorkOrderDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // 批量组装产品与客户
        Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(convertSet(list, MesProWorkOrderDO::getProductId));
        Map<Long, MesMdClientDO> clientMap = clientService.getClientMap(convertSet(list, MesProWorkOrderDO::getClientId));
        return list.stream().map(workOrder -> {
            MesProWorkOrderRespDTO dto = BeanUtils.toBean(workOrder, MesProWorkOrderRespDTO.class);
            MesMdItemDO item = itemMap.get(workOrder.getProductId());
            if (item != null) {
                dto.setProductName(item.getName());
                dto.setProductCode(item.getCode());
            }
            MesMdClientDO client = clientMap.get(workOrder.getClientId());
            if (client != null) {
                dto.setClientName(client.getName());
            }
            return dto;
        }).toList();
    }

}
