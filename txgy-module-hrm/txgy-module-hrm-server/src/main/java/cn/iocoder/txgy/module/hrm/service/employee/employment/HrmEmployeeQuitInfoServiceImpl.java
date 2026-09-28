package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quitinfo.HrmEmployeeQuitInfoSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeQuitInfoDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeQuitInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_QUIT_INFO_NOT_EXISTS;

/**
 * HRM 员工离职信息 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmployeeQuitInfoServiceImpl implements HrmEmployeeQuitInfoService {

    @Resource
    private HrmEmployeeQuitInfoMapper quitInfoMapper;

    @Override
    public Long saveEmployeeQuitInfo(HrmEmployeeQuitInfoSaveReqVO saveReqVO) {
        // 1. 校验员工离职信息是否存在
        HrmEmployeeQuitInfoDO quitInfo = BeanUtils.toBean(saveReqVO, HrmEmployeeQuitInfoDO.class);
        HrmEmployeeQuitInfoDO dbQuitInfo = quitInfoMapper.selectByEmployeeId(saveReqVO.getEmployeeId());
        // 1.1 计薪截止日默认等于离职生效日：工资算到离职当天，不再按整月生成缺勤（离职闭环）
        if (quitInfo.getSalaryCutoffDate() == null && saveReqVO.getPlanQuitTime() != null) {
            // 已有离职单且已定过截止日时沿用原值，避免每次修改离职信息把 HR 手工调整的截止日覆盖回去
            LocalDate existingCutoff = dbQuitInfo == null ? null : dbQuitInfo.getSalaryCutoffDate();
            quitInfo.setSalaryCutoffDate(existingCutoff != null
                    ? existingCutoff : saveReqVO.getPlanQuitTime().toLocalDate());
        }
        // 2. 保存员工离职信息
        quitInfo.setId(dbQuitInfo == null ? null : dbQuitInfo.getId());
        quitInfoMapper.insertOrUpdate(quitInfo);
        return quitInfo.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long saveEmployeeQuitInfoIndependently(HrmEmployeeQuitInfoSaveReqVO saveReqVO) {
        // 独立事务提交：即使调用方（立即离职）随后因交接未完成抛出业务异常，离职单也会保留，
        // 交接页因此可正常打开并登记交接明细（P0：解除「离职单」与「交接」互为前置的死锁）
        return saveEmployeeQuitInfo(saveReqVO);
    }

    @Override
    public void deleteEmployeeQuitInfo(Long employeeId) {
        quitInfoMapper.deleteByEmployeeId(employeeId);
    }

    @Override
    public HrmEmployeeQuitInfoDO getQuitInfoByEmployeeId(Long employeeId) {
        return quitInfoMapper.selectByEmployeeId(employeeId);
    }

    @Override
    public List<HrmEmployeeQuitInfoDO> getQuitInfoListByEmployeeIds(Collection<Long> employeeIds) {
        if (CollUtil.isEmpty(employeeIds)) {
            return Collections.emptyList();
        }
        return quitInfoMapper.selectListByEmployeeIds(employeeIds);
    }

    @Override
    public HrmEmployeeQuitInfoDO validateQuitInfoByEmployeeId(Long employeeId) {
        HrmEmployeeQuitInfoDO quitInfo = getQuitInfoByEmployeeId(employeeId);
        if (quitInfo == null) {
            throw exception(EMPLOYEE_QUIT_INFO_NOT_EXISTS);
        }
        return quitInfo;
    }

    @Override
    public List<HrmEmployeeQuitInfoDO> getDueQuitInfoList(LocalDateTime deadlineTime) {
        return quitInfoMapper.selectListByPlanQuitTimeBeforeOrEqual(deadlineTime);
    }

}
