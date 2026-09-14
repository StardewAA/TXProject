package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProAndonRecordRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 安灯呼叫记录 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 安灯呼叫记录")
public interface MesProAndonRecordApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/andon-record";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询安灯呼叫记录")
    @Parameter(name = "id", description = "安灯呼叫记录编号", example = "1", required = true)
    CommonResult<MesProAndonRecordRespDTO> getAndonRecord(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询安灯呼叫记录分页")
    CommonResult<PageResult<MesProAndonRecordRespDTO>> getAndonRecordPage(
            @RequestParam(value = "workstationId", required = false) Long workstationId,
            @RequestParam(value = "workstationCode", required = false) String workstationCode,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "handlerUserId", required = false) Long handlerUserId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "createTimeBegin", required = false) String createTimeBegin,
            @RequestParam(value = "createTimeEnd", required = false) String createTimeEnd,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
