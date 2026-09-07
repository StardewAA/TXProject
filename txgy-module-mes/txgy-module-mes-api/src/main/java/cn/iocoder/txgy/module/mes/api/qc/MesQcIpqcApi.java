package cn.iocoder.txgy.module.mes.api.qc;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.qc.dto.MesQcIpqcRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 过程检验单（IPQC）RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 过程检验单")
public interface MesQcIpqcApi {

    String PREFIX = ApiConstants.PREFIX + "/qc/ipqc";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询过程检验单")
    @Parameter(name = "id", description = "检验单编号", example = "1", required = true)
    CommonResult<MesQcIpqcRespDTO> getIpqc(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询过程检验单分页")
    CommonResult<PageResult<MesQcIpqcRespDTO>> getIpqcPage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "workOrderId", required = false) Long workOrderId,
            @RequestParam(value = "itemId", required = false) Long itemId,
            @RequestParam(value = "checkResult", required = false) Integer checkResult,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
