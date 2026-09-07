package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProFeedbackRespDTO;
import cn.iocoder.txgy.module.mes.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 生产报工 RPC API 接口
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 生产报工")
public interface MesProFeedbackApi {

    String PREFIX = ApiConstants.PREFIX + "/pro/feedback";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询生产报工单")
    @Parameter(name = "id", description = "报工编号", example = "1", required = true)
    CommonResult<MesProFeedbackRespDTO> getFeedback(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/page")
    @Operation(summary = "查询生产报工分页")
    CommonResult<PageResult<MesProFeedbackRespDTO>> getFeedbackPage(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "workOrderId", required = false) Long workOrderId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "itemId", required = false) Long itemId,
            @RequestParam(value = "feedbackTimeBegin", required = false) String feedbackTimeBegin,
            @RequestParam(value = "feedbackTimeEnd", required = false) String feedbackTimeEnd,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

}
