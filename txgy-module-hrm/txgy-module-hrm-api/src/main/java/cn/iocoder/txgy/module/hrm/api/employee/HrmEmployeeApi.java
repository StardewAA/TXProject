package cn.iocoder.txgy.module.hrm.api.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HRM 员工档案 API
 *
 * <p>供其它模块（如 system 的登录认证）按工号反查员工绑定的后台用户，用于「工号登录」。</p>
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - HRM 员工档案")
public interface HrmEmployeeApi {

    String PREFIX = ApiConstants.PREFIX + "/employee";

    /**
     * 按工号获得员工绑定的后台用户编号
     *
     * @param jobNumber 工号（租户内唯一）
     * @return 后台用户编号；工号不存在或未绑定用户时返回 null
     */
    @GetMapping(PREFIX + "/user-id-by-job-number")
    @Operation(summary = "按工号获得绑定的后台用户编号")
    CommonResult<Long> getUserIdByJobNumber(
            @NotEmpty(message = "工号不能为空") @RequestParam("jobNumber") String jobNumber);

}
