package cn.iocoder.txgy.module.mes.api.qc;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.qc.dto.MesQcIpqcRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.qc.ipqc.vo.MesQcIpqcPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.ipqc.MesQcIpqcDO;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import cn.iocoder.txgy.module.mes.service.qc.ipqc.MesQcIpqcService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 过程检验单（IPQC）RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesQcIpqcApiImpl implements MesQcIpqcApi {

    @Resource
    private MesQcIpqcService ipqcService;
    @Resource
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesMdItemService itemService;
    @Resource
    private MesMdWorkstationService workstationService;
    @Resource
    private MesProProcessService processService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CommonResult<MesQcIpqcRespDTO> getIpqc(Long id) {
        MesQcIpqcDO ipqc = ipqcService.getIpqc(id);
        if (ipqc == null) {
            return success(null);
        }
        return success(buildRespDTO(ipqc));
    }

    @Override
    public CommonResult<PageResult<MesQcIpqcRespDTO>> getIpqcPage(String code, Long workOrderId, Long itemId,
                                                                  Integer checkResult, Integer status,
                                                                  Integer pageNo, Integer pageSize) {
        MesQcIpqcPageReqVO pageReqVO = new MesQcIpqcPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setWorkOrderId(workOrderId);
        pageReqVO.setItemId(itemId);
        pageReqVO.setCheckResult(checkResult);
        pageReqVO.setStatus(status);
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesQcIpqcDO> pageResult = ipqcService.getIpqcPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesQcIpqcRespDTO buildRespDTO(MesQcIpqcDO ipqc) {
        MesQcIpqcRespDTO dto = BeanUtils.toBean(ipqc, MesQcIpqcRespDTO.class);
        if (ipqc.getWorkOrderId() != null) {
            MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(ipqc.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
            }
        }
        if (ipqc.getItemId() != null) {
            MesMdItemDO item = itemService.getItem(ipqc.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
        }
        if (ipqc.getWorkstationId() != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(ipqc.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (ipqc.getProcessId() != null) {
            MesProProcessDO process = processService.getProcess(ipqc.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
        }
        if (ipqc.getInspectorUserId() != null) {
            AdminUserRespDTO inspector = adminUserApi.getUser(ipqc.getInspectorUserId()).getCheckedData();
            if (inspector != null) {
                dto.setInspectorNickname(inspector.getNickname());
            }
        }
        return dto;
    }

    private List<MesQcIpqcRespDTO> buildRespDTOList(List<MesQcIpqcDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // 批量组装工单、产品、工作站、工序、检测人
        Map<Long, MesProWorkOrderDO> workOrderMap = workOrderService.getWorkOrderMap(
                convertSet(list, MesQcIpqcDO::getWorkOrderId));
        Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(convertSet(list, MesQcIpqcDO::getItemId));
        Map<Long, MesMdWorkstationDO> workstationMap = workstationService.getWorkstationMap(
                convertSet(list, MesQcIpqcDO::getWorkstationId));
        Map<Long, MesProProcessDO> processMap = processService.getProcessMap(
                convertSet(list, MesQcIpqcDO::getProcessId));
        Map<Long, AdminUserRespDTO> inspectorMap = adminUserApi.getUserMap(
                convertSet(list, MesQcIpqcDO::getInspectorUserId));
        return list.stream().map(ipqc -> {
            MesQcIpqcRespDTO dto = BeanUtils.toBean(ipqc, MesQcIpqcRespDTO.class);
            MesProWorkOrderDO workOrder = workOrderMap.get(ipqc.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
            }
            MesMdItemDO item = itemMap.get(ipqc.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
            MesMdWorkstationDO workstation = workstationMap.get(ipqc.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
            MesProProcessDO process = processMap.get(ipqc.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
            AdminUserRespDTO inspector = inspectorMap.get(ipqc.getInspectorUserId());
            if (inspector != null) {
                dto.setInspectorNickname(inspector.getNickname());
            }
            return dto;
        }).toList();
    }

}
