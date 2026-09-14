package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProRouteRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工艺路线 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 工艺路线")
public interface MesProRouteApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/route";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询工艺路线")
    @Parameter(name = "id", description = "工艺路线编号", example = "1", required = true)
    CommonResult<MesProRouteRespDTO> getRoute(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询工艺路线分页")
    CommonResult<PageResult<MesProRouteRespDTO>> getRoutePage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
