package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordLogRespDTO;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.md.workstation.vo.MesMdWorkstationPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workrecord.vo.MesProWorkRecordLogPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workrecord.MesProWorkRecordDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workrecord.MesProWorkRecordLogDO;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.workrecord.MesProWorkRecordService;
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
 * 生产记录（上下工）RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProWorkRecordApiImpl implements MesProWorkRecordApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MesProWorkRecordService workRecordService;
    @Resource
    private MesMdWorkstationService workstationService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CommonResult<MesProWorkRecordRespDTO> getWorkRecordByUserId(Long userId) {
        MesProWorkRecordDO workRecord = workRecordService.getWorkRecord(userId);
        if (workRecord == null) {
            return success(null);
        }
        MesProWorkRecordRespDTO dto = BeanUtils.toBean(workRecord, MesProWorkRecordRespDTO.class);
        fillWorkstationAndUser(dto, workRecord.getWorkstationId(), workRecord.getUserId());
        return success(dto);
    }

    @Override
    public CommonResult<MesProWorkRecordLogRespDTO> getWorkRecordLog(Long id) {
        MesProWorkRecordLogDO log = workRecordService.getWorkRecordLog(id);
        if (log == null) {
            return success(null);
        }
        MesProWorkRecordLogRespDTO dto = BeanUtils.toBean(log, MesProWorkRecordLogRespDTO.class);
        fillWorkstationAndUser(dto, log.getWorkstationId(), log.getUserId());
        return success(dto);
    }

    @Override
    public CommonResult<PageResult<MesProWorkRecordLogRespDTO>> getWorkRecordLogPage(Long userId, Long workstationId,
                                                                                     String workstationCode, Integer type,
                                                                                     String createTimeBegin, String createTimeEnd,
                                                                                     Integer pageNo, Integer pageSize) {
        if (workstationId == null) {
            workstationId = resolveWorkstationIdByCode(workstationCode);
        }
        MesProWorkRecordLogPageReqVO pageReqVO = new MesProWorkRecordLogPageReqVO();
        pageReqVO.setUserId(userId);
        pageReqVO.setWorkstationId(workstationId);
        pageReqVO.setType(type);
        if (createTimeBegin != null || createTimeEnd != null) {
            pageReqVO.setCreateTime(new LocalDateTime[]{
                    parseTime(createTimeBegin, true),
                    parseTime(createTimeEnd, false)});
        }
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProWorkRecordLogDO> pageResult = workRecordService.getWorkRecordLogPage(pageReqVO);
        return success(new PageResult<>(buildLogRespDTOList(pageResult.getList()), pageResult.getTotal()));
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

    private void fillWorkstationAndUser(MesProWorkRecordRespDTO dto, Long workstationId, Long userId) {
        if (workstationId != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(workstationId);
            if (workstation != null) {
                dto.setWorkstationCode(workstation.getCode());
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (userId != null) {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            if (user != null) {
                dto.setUserNickname(user.getNickname());
            }
        }
    }

    private void fillWorkstationAndUser(MesProWorkRecordLogRespDTO dto, Long workstationId, Long userId) {
        if (workstationId != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(workstationId);
            if (workstation != null) {
                dto.setWorkstationCode(workstation.getCode());
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (userId != null) {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            if (user != null) {
                dto.setUserNickname(user.getNickname());
            }
        }
    }

    private List<MesProWorkRecordLogRespDTO> buildLogRespDTOList(List<MesProWorkRecordLogDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, MesMdWorkstationDO> workstationMap = workstationService.getWorkstationMap(
                convertSet(list, MesProWorkRecordLogDO::getWorkstationId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(list, MesProWorkRecordLogDO::getUserId));
        return list.stream().map(log -> {
            MesProWorkRecordLogRespDTO dto = BeanUtils.toBean(log, MesProWorkRecordLogRespDTO.class);
            MesMdWorkstationDO workstation = workstationMap.get(log.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationCode(workstation.getCode());
                dto.setWorkstationName(workstation.getName());
            }
            AdminUserRespDTO user = userMap.get(log.getUserId());
            if (user != null) {
                dto.setUserNickname(user.getNickname());
            }
            return dto;
        }).toList();
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
