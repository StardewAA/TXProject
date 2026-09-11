package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProCardRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 生产流转卡 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 生产流转卡")
public interface MesProCardApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/card";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询生产流转卡")
    @Parameter(name = "id", description = "流转卡编号", example = "1", required = true)
    CommonResult<MesProCardRespDTO> getCard(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询生产流转卡分页")
    CommonResult<PageResult<MesProCardRespDTO>> getCardPage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "workOrderId", required = false) Long workOrderId,
            @RequestParam(value = "itemId", required = false) Long itemId,
            @RequestParam(value = "batchCode", required = false) String batchCode,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
