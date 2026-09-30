package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverConfirmReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverItemRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverItemSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverProgressRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverWaiveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitSettleReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitHandoverItemDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitInfoDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeQuitHandoverItemMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeQuitInfoMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverItemTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeQuitHandoverStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_HANDOVER_ITEM_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_HANDOVER_ITEM_PENDING;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_HANDOVER_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_SALARY_NOT_SETTLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_INFO_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_TIME_INVALID;

/**
 * HRM 员工离职交接 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmployeeQuitHandoverServiceImpl implements HrmEmployeeQuitHandoverService {

    @Resource
    private HrmEmployeeQuitHandoverItemMapper handoverItemMapper;
    @Resource
    private HrmEmployeeQuitInfoMapper quitInfoMapper;
    @Resource
    private HrmEmployeeMapper employeeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveHandoverItem(HrmEmployeeQuitHandoverItemSaveReqVO reqVO) {
        // 1. 校验员工与离职单存在（离职单被删除后不再允许登记交接明细）
        HrmEmployeeDO employee = employeeMapper.selectById(reqVO.getEmployeeId());
        if (employee == null) {
            throw exception(EMPLOYEE_NOT_EXISTS);
        }
        HrmEmployeeQuitInfoDO quitInfo = quitInfoMapper.selectByEmployeeId(reqVO.getEmployeeId());
        if (quitInfo == null) {
            throw exception(EMPLOYEE_QUIT_INFO_NOT_EXISTS);
        }

        // 2. 保存明细
        HrmEmployeeQuitHandoverItemDO item = BeanUtils.toBean(reqVO, HrmEmployeeQuitHandoverItemDO.class);
        if (item.getStatus() == null) {
            item.setStatus(HrmEmployeeQuitHandoverItemStatusEnum.PENDING.getStatus());
        }
        // 2.1 状态推进到「已交回/无需交接」时记录确认时间
        if (HrmEmployeeQuitHandoverItemStatusEnum.RETURNED.getStatus().equals(item.getStatus())
                || HrmEmployeeQuitHandoverItemStatusEnum.WAIVED.getStatus().equals(item.getStatus())) {
            item.setConfirmTime(LocalDateTime.now());
        } else {
            item.setConfirmTime(null);
        }
        handoverItemMapper.insertOrUpdate(item);

        // 3. 有明细后把离职单交接状态从「未开始」推进到「进行中」
        if (HrmEmployeeQuitHandoverStatusEnum.NOT_STARTED.getStatus().equals(quitInfo.getHandoverStatus())
                || quitInfo.getHandoverStatus() == null) {
            quitInfoMapper.updateById(new HrmEmployeeQuitInfoDO().setId(quitInfo.getId())
                    .setHandoverStatus(HrmEmployeeQuitHandoverStatusEnum.IN_PROGRESS.getStatus()));
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHandoverItem(Long id) {
        HrmEmployeeQuitHandoverItemDO item = handoverItemMapper.selectById(id);
        if (item == null) {
            throw exception(EMPLOYEE_QUIT_HANDOVER_ITEM_NOT_EXISTS);
        }
        handoverItemMapper.deleteById(id);
        // 明细清空后，未确认的交接回退为「未开始」
        HrmEmployeeQuitInfoDO quitInfo = quitInfoMapper.selectByEmployeeId(item.getEmployeeId());
        if (quitInfo != null
                && HrmEmployeeQuitHandoverStatusEnum.IN_PROGRESS.getStatus().equals(quitInfo.getHandoverStatus())
                && CollUtil.isEmpty(handoverItemMapper.selectListByEmployeeId(item.getEmployeeId()))) {
            quitInfoMapper.updateById(new HrmEmployeeQuitInfoDO().setId(quitInfo.getId())
                    .setHandoverStatus(HrmEmployeeQuitHandoverStatusEnum.NOT_STARTED.getStatus()));
        }
    }

    @Override
    public HrmEmployeeQuitHandoverProgressRespVO getHandoverProgress(Long employeeId) {
        HrmEmployeeDO employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw exception(EMPLOYEE_NOT_EXISTS);
        }
        HrmEmployeeQuitInfoDO quitInfo = quitInfoMapper.selectByEmployeeId(employeeId);
        List<HrmEmployeeQuitHandoverItemDO> items = handoverItemMapper.selectListByEmployeeId(employeeId);

        HrmEmployeeQuitHandoverProgressRespVO respVO = new HrmEmployeeQuitHandoverProgressRespVO();
        respVO.setEmployeeId(employeeId).setEmployeeName(employee.getName())
                .setJobNumber(employee.getJobNumber());
        respVO.setItems(buildItemRespVOList(items));
        respVO.setItemCount(items.size());
        respVO.setPendingItemCount((int) items.stream()
                .filter(item -> HrmEmployeeQuitHandoverItemStatusEnum.PENDING.getStatus().equals(item.getStatus()))
                .count());
        respVO.setLostItemCount((int) items.stream()
                .filter(item -> HrmEmployeeQuitHandoverItemStatusEnum.LOST.getStatus().equals(item.getStatus()))
                .count());
        respVO.setConfirmable(respVO.getPendingItemCount() == 0);

        if (quitInfo == null) {
            // 没有离职单：交接也无从谈起
            respVO.setHasQuitInfo(false);
            respVO.setHandoverStatus(HrmEmployeeQuitHandoverStatusEnum.NOT_STARTED.getStatus())
                    .setHandoverStatusName(HrmEmployeeQuitHandoverStatusEnum.NOT_STARTED.getName());
            return respVO;
        }
        respVO.setHasQuitInfo(true);
        respVO.setPlanQuitTime(quitInfo.getPlanQuitTime());
        respVO.setHandoverStatus(quitInfo.getHandoverStatus() == null
                ? HrmEmployeeQuitHandoverStatusEnum.NOT_STARTED.getStatus() : quitInfo.getHandoverStatus());
        HrmEmployeeQuitHandoverStatusEnum statusEnum =
                HrmEmployeeQuitHandoverStatusEnum.valueOf(respVO.getHandoverStatus());
        respVO.setHandoverStatusName(statusEnum == null ? null : statusEnum.getName());
        respVO.setHandoverToEmployeeId(quitInfo.getHandoverToEmployeeId())
                .setHandoverConfirmTime(quitInfo.getHandoverConfirmTime())
                .setHandoverRemark(quitInfo.getHandoverRemark());
        // 结算信息（离职闭环）：计薪截止日 / 是否已结清
        respVO.setSalarySettlementTime(quitInfo.getSalarySettlementTime())
                .setSalaryCutoffDate(quitInfo.getSalaryCutoffDate() != null
                        ? quitInfo.getSalaryCutoffDate()
                        : (quitInfo.getPlanQuitTime() == null ? null : quitInfo.getPlanQuitTime().toLocalDate()))
                .setSalarySettled(Boolean.TRUE.equals(quitInfo.getSalarySettled()))
                .setSalarySettleTime(quitInfo.getSalarySettleTime())
                .setSalarySettleRemark(quitInfo.getSalarySettleRemark());
        respVO.setClosed(HrmEmployeeQuitHandoverStatusEnum.isFinished(respVO.getHandoverStatus())
                && Boolean.TRUE.equals(respVO.getSalarySettled()));
        if (quitInfo.getHandoverToEmployeeId() != null) {
            HrmEmployeeDO handoverTo = employeeMapper.selectById(quitInfo.getHandoverToEmployeeId());
            respVO.setHandoverToEmployeeName(handoverTo == null ? null : handoverTo.getName());
        }
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmHandover(HrmEmployeeQuitHandoverConfirmReqVO reqVO) {
        HrmEmployeeQuitInfoDO quitInfo = validateQuitInfo(reqVO.getEmployeeId());
        // 1. 仍有「待交回」明细时不允许确认，避免漏交
        List<HrmEmployeeQuitHandoverItemDO> items = handoverItemMapper.selectListByEmployeeId(reqVO.getEmployeeId());
        long pendingCount = items.stream()
                .filter(item -> HrmEmployeeQuitHandoverItemStatusEnum.PENDING.getStatus().equals(item.getStatus()))
                .count();
        if (pendingCount > 0) {
            throw exception(EMPLOYEE_QUIT_HANDOVER_ITEM_PENDING, pendingCount);
        }
        // 2. 校验交接人存在
        if (reqVO.getHandoverToEmployeeId() != null) {
            HrmEmployeeDO handoverTo = employeeMapper.selectById(reqVO.getHandoverToEmployeeId());
            if (handoverTo == null) {
                throw exception(EMPLOYEE_NOT_EXISTS);
            }
        }
        // 3. 落库确认结论
        quitInfoMapper.updateById(new HrmEmployeeQuitInfoDO().setId(quitInfo.getId())
                .setHandoverStatus(HrmEmployeeQuitHandoverStatusEnum.CONFIRMED.getStatus())
                .setHandoverToEmployeeId(reqVO.getHandoverToEmployeeId())
                .setHandoverConfirmTime(LocalDateTime.now())
                .setHandoverRemark(reqVO.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void waiveHandover(HrmEmployeeQuitHandoverWaiveReqVO reqVO) {
        HrmEmployeeQuitInfoDO quitInfo = validateQuitInfo(reqVO.getEmployeeId());
        quitInfoMapper.updateById(new HrmEmployeeQuitInfoDO().setId(quitInfo.getId())
                .setHandoverStatus(HrmEmployeeQuitHandoverStatusEnum.WAIVED.getStatus())
                .setHandoverConfirmTime(LocalDateTime.now())
                .setHandoverRemark(reqVO.getRemark()));
    }

    @Override
    public void validateHandoverFinished(Long employeeId, Long quitInfoId) {
        HrmEmployeeQuitInfoDO quitInfo = quitInfoId != null
                ? quitInfoMapper.selectById(quitInfoId) : quitInfoMapper.selectByEmployeeId(employeeId);
        if (quitInfo == null) {
            throw exception(EMPLOYEE_QUIT_INFO_NOT_EXISTS);
        }
        if (!HrmEmployeeQuitHandoverStatusEnum.isFinished(quitInfo.getHandoverStatus())) {
            throw exception(EMPLOYEE_QUIT_HANDOVER_REQUIRED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleQuitSalary(HrmEmployeeQuitSettleReqVO reqVO) {
        HrmEmployeeQuitInfoDO quitInfo = validateQuitInfo(reqVO.getEmployeeId());
        // 1. 允许在确认结清时同步调整计薪截止日（按班组结算周期封账）
        LocalDate cutoffDate = reqVO.getSalaryCutoffDate() != null
                ? reqVO.getSalaryCutoffDate() : quitInfo.getSalaryCutoffDate();
        if (cutoffDate == null && quitInfo.getPlanQuitTime() != null) {
            cutoffDate = quitInfo.getPlanQuitTime().toLocalDate();
        }
        // 2. 计薪截止日不能早于申请离职日期，避免把在岗期间的正班次一刀切掉
        if (cutoffDate != null && quitInfo.getApplyQuitTime() != null
                && cutoffDate.isBefore(quitInfo.getApplyQuitTime().toLocalDate())) {
            throw exception(EMPLOYEE_QUIT_TIME_INVALID);
        }
        // 3. 落库结算结论
        quitInfoMapper.updateById(new HrmEmployeeQuitInfoDO().setId(quitInfo.getId())
                .setSalaryCutoffDate(cutoffDate)
                .setSalarySettled(true)
                .setSalarySettleTime(LocalDateTime.now())
                .setSalarySettleRemark(reqVO.getSalarySettleRemark()));
    }

    @Override
    public void validateSalarySettled(Long employeeId) {
        HrmEmployeeQuitInfoDO quitInfo = quitInfoMapper.selectByEmployeeId(employeeId);
        // 没有离职单（历史数据）或已结清时放行
        if (quitInfo == null || Boolean.TRUE.equals(quitInfo.getSalarySettled())) {
            return;
        }
        throw exception(EMPLOYEE_QUIT_SALARY_NOT_SETTLED);
    }

    private HrmEmployeeQuitInfoDO validateQuitInfo(Long employeeId) {
        HrmEmployeeQuitInfoDO quitInfo = quitInfoMapper.selectByEmployeeId(employeeId);
        if (quitInfo == null) {
            throw exception(EMPLOYEE_QUIT_INFO_NOT_EXISTS);
        }
        return quitInfo;
    }

    private List<HrmEmployeeQuitHandoverItemRespVO> buildItemRespVOList(List<HrmEmployeeQuitHandoverItemDO> items) {
        if (CollUtil.isEmpty(items)) {
            return Collections.emptyList();
        }
        List<HrmEmployeeQuitHandoverItemRespVO> list = new ArrayList<>(items.size());
        for (HrmEmployeeQuitHandoverItemDO item : items) {
            HrmEmployeeQuitHandoverItemRespVO respVO =
                    BeanUtils.toBean(item, HrmEmployeeQuitHandoverItemRespVO.class);
            HrmEmployeeQuitHandoverItemTypeEnum typeEnum =
                    HrmEmployeeQuitHandoverItemTypeEnum.valueOf(item.getItemType());
            respVO.setItemTypeName(typeEnum == null ? null : typeEnum.getName());
            HrmEmployeeQuitHandoverItemStatusEnum statusEnum =
                    HrmEmployeeQuitHandoverItemStatusEnum.valueOf(item.getStatus());
            respVO.setStatusName(statusEnum == null ? null : statusEnum.getName());
            list.add(respVO);
        }
        return list;
    }

}
