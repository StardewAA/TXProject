package cn.iocoder.txgy.module.hrm.api.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.api.employee.dto.HrmEmployeeComplianceRespDTO;
import cn.iocoder.txgy.module.hrm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/**
 * HRM 员工上岗合规状态 API
 *
 * <p><b>这是给 MES（现场执行模块）预留的接口：</b>MES 在"人员进场登记 / 派工上岗 / 排班"前调用，
 * 拿到该员工在 HRM 侧的资质结论（在职状态、证照有效性、三级安全教育、体检、黑名单），
 * 再叠加 MES 自己的项目要求（项目证照要求、安全交底、保险覆盖等）决定是否放行。</p>
 *
 * <p>职责边界：HRM 回答"这个人合不合格"，MES 回答"这个人能不能进这个场"。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "RPC 服务 - HRM 员工上岗合规状态")
@FeignClient(name = ApiConstants.NAME)
public interface HrmEmployeeComplianceApi {

    String PREFIX = ApiConstants.PREFIX + "/employee-compliance";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "获得单个员工的上岗合规状态",
            description = "MES 进场卡控使用：不合格时 reasons 会列出具体原因，可直接提示给现场人员")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "1024")
    CommonResult<HrmEmployeeComplianceRespDTO> getEmployeeCompliance(@RequestParam("employeeId") Long employeeId);

    @GetMapping(PREFIX + "/list")
    @Operation(summary = "批量获得员工的上岗合规状态",
            description = "MES 花名册/批量派工使用，避免 N 次单查")
    @Parameter(name = "employeeIds", description = "员工编号集合", required = true)
    CommonResult<List<HrmEmployeeComplianceRespDTO>> getEmployeeComplianceList(
            @RequestParam("employeeIds") Collection<Long> employeeIds);

}
