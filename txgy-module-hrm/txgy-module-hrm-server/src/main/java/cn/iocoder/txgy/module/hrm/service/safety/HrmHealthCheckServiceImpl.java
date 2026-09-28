package cn.iocoder.txgy.module.hrm.service.safety;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.safety.HrmHealthCheckMapper;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmHealthCheckResultEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HEALTH_CHECK_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HEALTH_CHECK_REQUIRED;

/**
 * HRM 体检与职业健康 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmHealthCheckServiceImpl implements HrmHealthCheckService {

    @Resource
    private HrmHealthCheckMapper healthCheckMapper;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public Long createHealthCheck(HrmHealthCheckSaveReqVO reqVO) {
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmHealthCheckDO healthCheck = BeanUtils.toBean(reqVO, HrmHealthCheckDO.class);
        if (healthCheck.getResult() == null) {
            healthCheck.setResult(HrmHealthCheckResultEnum.QUALIFIED.getResult());
        }
        healthCheckMapper.insert(healthCheck);
        return healthCheck.getId();
    }

    @Override
    public void updateHealthCheck(HrmHealthCheckSaveReqVO reqVO) {
        validateHealthCheckExists(reqVO.getId());
        healthCheckMapper.updateById(BeanUtils.toBean(reqVO, HrmHealthCheckDO.class));
    }

    @Override
    public void deleteHealthCheck(Long id) {
        validateHealthCheckExists(id);
        healthCheckMapper.deleteById(id);
    }

    @Override
    public HrmHealthCheckDO validateHealthCheckExists(Long id) {
        HrmHealthCheckDO healthCheck = healthCheckMapper.selectById(id);
        if (healthCheck == null) {
            throw exception(HEALTH_CHECK_NOT_EXISTS);
        }
        return healthCheck;
    }

    @Override
    public PageResult<HrmHealthCheckDO> getHealthCheckPage(HrmHealthCheckPageReqVO reqVO) {
        return healthCheckMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmHealthCheckDO> getHealthCheckListByEmployeeId(Long employeeId) {
        return healthCheckMapper.selectListByEmployeeId(employeeId);
    }

    @Override
    public void validateHealthCheck(Long employeeId) {
        LocalDate today = LocalDate.now();
        // 有任意一条「结论合格 + 未过有效期」的体检记录即通过
        boolean passed = healthCheckMapper.selectListByEmployeeId(employeeId).stream()
                .anyMatch(item -> HrmHealthCheckResultEnum.QUALIFIED.getResult().equals(item.getResult())
                        && (item.getValidUntil() == null || !item.getValidUntil().isBefore(today)));
        if (!passed) {
            List<HrmHealthCheckDO> list = healthCheckMapper.selectListByEmployeeId(employeeId);
            String reason = CollUtil.isEmpty(list) ? "没有体检记录"
                    : "最近一次体检结论为「" + resolveResultName(list.get(0).getResult()) + "」或已过有效期";
            throw exception(HEALTH_CHECK_REQUIRED, reason);
        }
    }

    private String resolveResultName(Integer result) {
        HrmHealthCheckResultEnum value = HrmHealthCheckResultEnum.valueOf(result);
        return value == null ? "未知" : value.getName();
    }

}
