package cn.iocoder.txgy.module.hrm.service.employee.info;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeFileDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeContractMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeFileMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_ARCHIVE_INCOMPLETE;

/**
 * HRM 员工档案完整性校验 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmEmployeeArchiveCheckServiceImpl implements HrmEmployeeArchiveCheckService {

    /**
     * 直接用 Mapper 查询，避免与 HrmEmployeeServiceImpl 形成循环依赖
     * （后者要在转正时调用本 Service 做档案卡控）
     */
    @Resource
    private HrmEmployeeMapper employeeMapper;
    @Resource
    private HrmEmployeeContractMapper employeeContractMapper;
    @Resource
    private HrmEmployeeFileMapper employeeFileMapper;

    @Override
    public List<String> getMissingItems(Long employeeId) {
        List<String> missing = new ArrayList<>();
        HrmEmployeeDO employee = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (employee == null) {
            return missing;
        }
        // 1. 手机号：后续开户、发通知、现场联系都依赖它
        if (StrUtil.isBlank(employee.getMobile())) {
            missing.add("手机号");
        }
        // 2. 劳动合同：至少一份
        List<HrmEmployeeContractDO> contracts = employeeContractMapper.selectListByEmployeeId(employeeId);
        if (CollUtil.isEmpty(contracts)) {
            missing.add("劳动合同（尚未建档）");
        }
        // 3. 个人电子档案附件：至少一份
        List<HrmEmployeeFileDO> files = employeeFileMapper.selectListByEmployeeId(employeeId);
        if (CollUtil.isEmpty(files)) {
            missing.add("个人电子档案附件");
        }
        return missing;
    }

    @Override
    public void validateArchiveComplete(Long employeeId, String action) {
        List<String> missing = getMissingItems(employeeId);
        if (CollUtil.isNotEmpty(missing)) {
            throw exception(EMPLOYEE_ARCHIVE_INCOMPLETE, action, String.join("、", missing));
        }
    }

}
