package cn.iocoder.txgy.module.mes.controller.admin.shortage;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.mes.service.shortage.WmsShortageInboxService;
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
    public WmsShortageInboxController(WmsShortageInboxService service){this.service=service;}
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
        service.receive(payload,who[0],who[1],SecurityFrameworkUtils.getLoginUser().getTenantId()); return success(true);
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
