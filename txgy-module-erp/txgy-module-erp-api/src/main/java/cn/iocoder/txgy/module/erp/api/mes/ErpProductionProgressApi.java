package cn.iocoder.txgy.module.erp.api.mes;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.mes.dto.ErpProductionProgressReqDTO;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 生产完工进度 RPC API 接口
 *
 * 供 MES 在工单完工时回传累计合格产量，ERP 侧写入销售订单行已生产数量。
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP 生产完工进度")
public interface ErpProductionProgressApi {

    String PREFIX = ApiConstants.PREFIX + "/production-progress";

    @PutMapping(PREFIX + "/report")
    @Operation(summary = "回传 MES 工单完工进度到销售订单行")
    CommonResult<Boolean> reportProgress(@Valid @RequestBody ErpProductionProgressReqDTO reqDTO);

}
