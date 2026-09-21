package cn.iocoder.txgy.module.hrm.api.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 员工档案 API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class HrmEmployeeApiImpl implements HrmEmployeeApi {

    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public CommonResult<Long> getUserIdByJobNumber(String jobNumber) {
        // 工号在租户内唯一：租户上下文由 Feign 请求头透传，查询会自动按租户过滤
        HrmEmployeeDO employee = employeeService.getEmployeeByJobNumber(jobNumber);
        if (employee == null) {
            return success(null);
        }
        return success(employee.getUserId());
    }

}
