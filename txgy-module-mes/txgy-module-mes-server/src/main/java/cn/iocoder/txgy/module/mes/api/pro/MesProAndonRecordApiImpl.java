package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProAndonRecordRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.md.workstation.vo.MesMdWorkstationPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.andon.vo.record.MesProAndonRecordPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.andon.MesProAndonRecordDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.andon.MesProAndonRecordService;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 安灯呼叫记录 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProAndonRecordApiImpl implements MesProAndonRecordApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MesProAndonRecordService andonRecordService;
    @Resource
    private MesMdWorkstationService workstationService;
    @Resource
    private MesProProcessService processService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CommonResult<MesProAndonRecordRespDTO> getAndonRecord(Long id) {
        MesProAndonRecordDO andonRecord = andonRecordService.getAndonRecord(id);
        if (andonRecord == null) {
            return success(null);
        }
        return success(buildRespDTO(andonRecord));
    }

    @Override
    public CommonResult<PageResult<MesProAndonRecordRespDTO>> getAndonRecordPage(Long workstationId, String workstationCode,
                                                                                  Long userId, Long handlerUserId, Integer status,
                                                                                  String createTimeBegin, String createTimeEnd,
                                                                                  Integer pageNo, Integer pageSize) {
        if (workstationId == null) {
            workstationId = resolveWorkstationIdByCode(workstationCode);
        }
        MesProAndonRecordPageReqVO pageReqVO = new MesProAndonRecordPageReqVO();
        pageReqVO.setWorkstationId(workstationId);
        pageReqVO.setUserId(userId);
        pageReqVO.setHandlerUserId(handlerUserId);
        pageReqVO.setStatus(status);
        if (createTimeBegin != null || createTimeEnd != null) {
            pageReqVO.setCreateTime(new LocalDateTime[]{
                    parseTime(createTimeBegin, true),
                    parseTime(createTimeEnd, false)});
        }
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProAndonRecordDO> pageResult = andonRecordService.getAndonRecordPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesProAndonRecordRespDTO buildRespDTO(MesProAndonRecordDO andonRecord) {
        MesProAndonRecordRespDTO dto = BeanUtils.toBean(andonRecord, MesProAndonRecordRespDTO.class);
        if (andonRecord.getWorkstationId() != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(andonRecord.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationCode(workstation.getCode());
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (andonRecord.getProcessId() != null) {
            MesProProcessDO process = processService.getProcess(andonRecord.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
        }
        if (andonRecord.getUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(andonRecord.getUserId()).getCheckedData();
            if (user != null) {
                dto.setUserNickname(user.getNickname());
            }
        }
        if (andonRecord.getHandlerUserId() != null) {
            AdminUserRespDTO handlerUser = adminUserApi.getUser(andonRecord.getHandlerUserId()).getCheckedData();
            if (handlerUser != null) {
                dto.setHandlerNickname(handlerUser.getNickname());
            }
        }
        return dto;
    }

    private List<MesProAndonRecordRespDTO> buildRespDTOList(List<MesProAndonRecordDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, MesMdWorkstationDO> workstationMap = workstationService.getWorkstationMap(
                convertSet(list, MesProAndonRecordDO::getWorkstationId));
        Map<Long, MesProProcessDO> processMap = processService.getProcessMap(
                convertSet(list, MesProAndonRecordDO::getProcessId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(list, MesProAndonRecordDO::getUserId));
        return list.stream().map(andonRecord -> {
            MesProAndonRecordRespDTO dto = BeanUtils.toBean(andonRecord, MesProAndonRecordRespDTO.class);
            MesMdWorkstationDO workstation = workstationMap.get(andonRecord.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationCode(workstation.getCode());
                dto.setWorkstationName(workstation.getName());
            }
            MesProProcessDO process = processMap.get(andonRecord.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
            AdminUserRespDTO user = userMap.get(andonRecord.getUserId());
            if (user != null) {
                dto.setUserNickname(user.getNickname());
            }
            if (andonRecord.getHandlerUserId() != null) {
                AdminUserRespDTO handlerUser = adminUserApi.getUser(andonRecord.getHandlerUserId()).getCheckedData();
                if (handlerUser != null) {
                    dto.setHandlerNickname(handlerUser.getNickname());
                }
            }
            return dto;
        }).toList();
    }

    private Long resolveWorkstationIdByCode(String workstationCode) {
        if (workstationCode == null || workstationCode.isBlank()) {
            return null;
        }
        MesMdWorkstationPageReqVO pageReqVO = new MesMdWorkstationPageReqVO();
        pageReqVO.setCode(workstationCode);
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(1);
        List<MesMdWorkstationDO> list = workstationService.getWorkstationPage(pageReqVO).getList();
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private LocalDateTime parseTime(String time, boolean isBegin) {
        if (time == null || time.isBlank()) {
            return null;
        }
        // 兼容只传日期的情况
        if (time.length() <= 10) {
            time = time + (isBegin ? " 00:00:00" : " 23:59:59");
        }
        return LocalDateTime.parse(time, DATE_TIME_FORMATTER);
    }

}
