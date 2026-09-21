package cn.iocoder.txgy.module.hrm.service.safety;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;

import java.util.List;

/**
 * HRM 体检与职业健康 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmHealthCheckService {

    Long createHealthCheck(HrmHealthCheckSaveReqVO reqVO);

    void updateHealthCheck(HrmHealthCheckSaveReqVO reqVO);

    void deleteHealthCheck(Long id);

    HrmHealthCheckDO validateHealthCheckExists(Long id);

    PageResult<HrmHealthCheckDO> getHealthCheckPage(HrmHealthCheckPageReqVO reqVO);

    List<HrmHealthCheckDO> getHealthCheckListByEmployeeId(Long employeeId);

    /**
     * 校验员工存在「在有效期内的合格体检记录」
     *
     * <p>结论不合格 / 需复查未复查 / 已过有效期 → 抛业务异常。</p>
     */
    void validateHealthCheck(Long employeeId);

}
