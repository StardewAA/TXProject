package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProProjectRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 项目 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 项目")
public interface MesProProjectApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/project";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询项目")
    @Parameter(name = "id", description = "项目编号", example = "1", required = true)
    CommonResult<MesProProjectRespDTO> getProject(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询项目分页")
    CommonResult<PageResult<MesProProjectRespDTO>> getProjectPage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "sourceType", required = false) Integer sourceType,
            @RequestParam(value = "priority", required = false) Integer priority,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "dept", required = false) String dept,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
