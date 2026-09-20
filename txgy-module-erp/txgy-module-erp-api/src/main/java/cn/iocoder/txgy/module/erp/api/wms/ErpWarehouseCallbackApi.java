package cn.iocoder.txgy.module.erp.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWarehouseUpsertCallbackReqDTO;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ERP 接收 WMS 仓库主数据 RPC 接口。
 *
 * <p>WMS 端在仓库增删改时主动 RPC 推送到 ERP，ERP 端基于 wmsWarehouseCode
 * 进行 upsert，使 ERP 用户在采购入库申请 WMS 时下拉选择即可，无需手工录入。</p>
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP 接收 WMS 仓库主数据")
public interface ErpWarehouseCallbackApi {

    String PREFIX = ApiConstants.PREFIX + "/wms-callback";

    @PostMapping(PREFIX + "/warehouse-upsert")
    @Operation(summary = "接收 WMS 仓库主数据 upsert（按 wmsWarehouseCode 落库或更新）")
    CommonResult<Boolean> callbackWarehouseUpsert(@Valid @RequestBody ErpWarehouseUpsertCallbackReqDTO reqDTO);

}
