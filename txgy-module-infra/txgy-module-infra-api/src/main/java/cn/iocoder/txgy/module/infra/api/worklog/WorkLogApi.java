package cn.iocoder.txgy.module.infra.api.worklog;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.infra.api.worklog.dto.WorkLogRespDTO;
import cn.iocoder.txgy.module.infra.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作留痕 RPC 服务
 *
 * <p><b>权限约定</b>：本接口只返回**当前登录用户自己**的操作留痕，
 * 调用方无法指定要查询的用户。这与「留痕查询」页面的行为一致——
 * 页面在有 {@code infra:work-log:query-all} 权限时才允许查看全员，
 * 而通过本接口始终只能查自己，因此不会成为越权读取他人操作记录的后门。
 *
 * @author 天信管业
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 工作留痕")
public interface WorkLogApi {

    String PREFIX = ApiConstants.PREFIX + "/work-log";

    @GetMapping(PREFIX + "/my-page")
    @Operation(summary = "分页查询当前登录用户自己的操作留痕")
    @Parameter(name = "module", description = "模块，如 wms", example = "wms")
    @Parameter(name = "action", description = "动作，如 CREATE / QUERY", example = "CREATE")
    @Parameter(name = "keyword", description = "关键词，模糊匹配摘要与业务键", example = "岩棉板")
    CommonResult<PageResult<WorkLogRespDTO>> getMyWorkLogPage(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize);

}
