package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkOrderRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 生产工单 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 生产工单")
public interface MesProWorkOrderApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/work-order";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询生产工单")
    @Parameter(name = "id", description = "工单编号", example = "1", required = true)
    CommonResult<MesProWorkOrderRespDTO> getWorkOrder(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/get-by-code")
    @Operation(summary = "通过工单编码查询生产工单")
    @Parameter(name = "code", description = "工单编码", example = "WO-001", required = true)
    CommonResult<MesProWorkOrderRespDTO> getWorkOrderByCode(@RequestParam("code") String code);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询生产工单分页")
    CommonResult<PageResult<MesProWorkOrderRespDTO>> getWorkOrderPage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
