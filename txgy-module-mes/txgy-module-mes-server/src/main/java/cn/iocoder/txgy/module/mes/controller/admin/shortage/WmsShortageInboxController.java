package cn.iocoder.txgy.module.mes.controller.admin.shortage;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.mes.service.shortage.MesWmsShortageWorkOrderAutoService;
import cn.iocoder.txgy.module.mes.service.shortage.WmsShortageInboxService;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController("mesWmsShortageInboxController")
@RequestMapping("/mes/wms-shortage")
public class WmsShortageInboxController {
    // Parse the original JSON bytes as decimals before any untyped converter can round them to Double.
    private static final JsonMapper PAYLOAD_MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WmsShortageInboxController.class);
    private final WmsShortageInboxService service;
    private final MesWmsShortageWorkOrderAutoService workOrderAutoService;
    @Resource
    private WmsIntegrationApi wmsIntegrationApi;
    public WmsShortageInboxController(WmsShortageInboxService service, MesWmsShortageWorkOrderAutoService workOrderAutoService){
        this.service=service; this.workOrderAutoService=workOrderAutoService;
    }
    private long[] identity() {
        var user=SecurityFrameworkUtils.getLoginUser(); var tenant=TenantContextHolder.getTenantId();
        if(user==null || user.getId()==null || tenant==null || !tenant.equals(user.getTenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"authenticated tenant required");
        return new long[]{tenant,user.getId()};
    }
    @PostMapping("/receive")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:receive')")
    public CommonResult<Boolean> receive(@RequestBody String rawPayload) {
        var who=identity();
        final Map<String,Object> payload;
        try { payload=PAYLOAD_MAPPER.readValue(rawPayload,new TypeReference<Map<String,Object>>() {}); }
        catch(JacksonException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid JSON object"); }
        service.receive(payload,who[0],who[1],SecurityFrameworkUtils.getLoginUser().getTenantId());
        // 收件箱落库即进入待确认；生产工单在 MES 确认协作单 G 时才创建（设计 6.3.5/6.3.6）
        return success(true);
    }
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:query')")
    public CommonResult<Map<String,Object>> page(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize){
        var who=identity(); return success(service.page(who[0],who[1],pageNo,pageSize));
    }
    @GetMapping("/unread-count")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:query')")
    public CommonResult<Long> unreadCount(){var who=identity(); return success(service.unreadCount(who[0],who[1]));}
    @PostMapping("/read")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:query')")
    public CommonResult<Boolean> read(@RequestParam String eventId,@RequestParam long eventVersion){
        var who=identity(); service.markRead(who[0],who[1],eventId,eventVersion); return success(true);
    }

    // MES 确认协作单 G：创建正式生产工单并回写 WMS（设计 6.3.5/6.3.6）
    @PostMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:confirm')")
    public CommonResult<Boolean> confirm(@RequestParam String eventId,@RequestParam long eventVersion){
        var who=identity();
        Map<String,Object> payload=service.getPayload(who[0],eventId);
        String workOrderNo=workOrderAutoService.tryCreateWorkOrder(payload,who[0]);
        service.confirm(who[0],eventId,eventVersion,workOrderNo);
        // 回写 WMS 协作单 G 为已确认（best-effort，失败仅记录，不阻断确认）
        try {
            wmsIntegrationApi.confirmCollaborationOrderG(who[0],(String)payload.get("sourceOrderNo"),
                    (String)payload.get("skuCode"),workOrderNo);
        } catch(RuntimeException ex) { LOG.warn("回写 WMS 协作单 G 确认失败 event={}",eventId,ex); }
        return success(true);
    }

    // MES 驳回协作单 G：记录原因并回写 WMS（设计 6.3.5，驳回必须填原因）
    @PostMapping("/reject")
    @PreAuthorize("@ss.hasPermission('mes:wms-shortage:confirm')")
    public CommonResult<Boolean> reject(@RequestParam String eventId,@RequestParam long eventVersion,@RequestParam String reason){
        var who=identity();
        if(reason==null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"驳回必须填写原因");
        Map<String,Object> payload=service.getPayload(who[0],eventId);
        service.reject(who[0],eventId,eventVersion,reason);
        try {
            wmsIntegrationApi.rejectCollaborationOrderG(who[0],(String)payload.get("sourceOrderNo"),
                    (String)payload.get("skuCode"),reason);
        } catch(RuntimeException ex) { LOG.warn("回写 WMS 协作单 G 驳回失败 event={}",eventId,ex); }
        return success(true);
    }

    // Controller-local handlers run before GlobalExceptionHandler, which otherwise turns errors into HTTP 200.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Boolean>> statusError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(CommonResult.error(ex.getStatusCode().value(),
            ex.getReason()==null ? "request rejected" : ex.getReason()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResult<Boolean>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResult.error(403,"permission denied"));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<Boolean>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(CommonResult.error(400,"invalid request"));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<CommonResult<Boolean>> databaseFailure(DataAccessException ex) {
        LOG.error("WMS shortage inbox database operation failed",ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResult.error(503,"message persistence unavailable"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<Boolean>> unexpectedFailure(Exception ex) {
        LOG.error("WMS shortage inbox request failed",ex);
        return ResponseEntity.internalServerError().body(CommonResult.error(500,"message service unavailable"));
    }
}
