package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordLogRespDTO;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProWorkRecordRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 生产记录（上下工）RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 生产记录（上下工）")
public interface MesProWorkRecordApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/work-record";

    @GetMapping(PREFIX + "/get-current")
    @Operation(summary = "查询用户当前的上下工绑定关系")
    @Parameter(name = "userId", description = "用户编号", example = "1", required = true)
    CommonResult<MesProWorkRecordRespDTO> getWorkRecordByUserId(@RequestParam("userId") Long userId);

    @GetMapping(PREFIX + "/log/get")
    @Operation(summary = "通过 ID 查询上下工记录流水")
    @Parameter(name = "id", description = "流水编号", example = "1", required = true)
    CommonResult<MesProWorkRecordLogRespDTO> getWorkRecordLog(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/log/page")
    @Operation(summary = "查询上下工记录流水分页")
    CommonResult<PageResult<MesProWorkRecordLogRespDTO>> getWorkRecordLogPage(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "workstationId", required = false) Long workstationId,
            @RequestParam(value = "workstationCode", required = false) String workstationCode,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "createTimeBegin", required = false) String createTimeBegin,
            @RequestParam(value = "createTimeEnd", required = false) String createTimeEnd,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
