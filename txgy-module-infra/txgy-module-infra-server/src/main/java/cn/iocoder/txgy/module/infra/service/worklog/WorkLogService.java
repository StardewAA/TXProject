package cn.iocoder.txgy.module.infra.service.worklog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.audit.WorkTrace;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogPageReqVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.worklog.WorkLogDO;
import cn.iocoder.txgy.module.infra.dal.mysql.worklog.WorkLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class WorkLogService {
    public static final String QUERY_ALL = "infra:work-log:query-all";
    private final WorkLogMapper mapper;
    private final PermissionCommonApi permissionApi;

    public boolean canViewAll() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) throw exception(UNAUTHORIZED);
        return Boolean.TRUE.equals(permissionApi.hasAnyPermissions(userId, QUERY_ALL).getCheckedData());
    }
    private Long actor(WorkLogPageReqVO req) {
        return WorkLogPolicy.actor(req.getScope(), SecurityFrameworkUtils.getLoginUserId(), req.getUserId(),
                "ALL".equals(req.getScope()) && canViewAll());
    }
    private Long tenant() {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null) throw exception(FORBIDDEN);
        return tenant;
    }
    public PageResult<WorkLogDO> page(WorkLogPageReqVO req) {
        return mapper.page(req, tenant(), actor(req));
    }
    public void freezeExport(WorkLogPageReqVO req) {
        req.setMaxId(mapper.exportBoundary(tenant(), actor(req)));
    }
    public WorkLogDO get(Long id, String scope) {
        WorkLogPageReqVO req = new WorkLogPageReqVO();
        req.setScope(scope);
        Long actor = actor(req);
        Long tenant = tenant();
        WorkLogDO row = mapper.selectById(id);
        if (row == null) throw exception(NOT_FOUND);
        WorkLogPolicy.checkDetail(tenant, row.getTenantId(), actor, row.getUserId());
        return row;
    }

    /** Receives trusted service-side API events, before legacy 8K truncation. */
    public void capture(ApiAccessLogCreateReqDTO dto) {
        if (dto.getAuditTenantId() != null) {
            cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(dto.getAuditTenantId(), () -> captureInTenant(dto));
        } else {
            captureInTenant(dto);
        }
    }
    private void captureInTenant(ApiAccessLogCreateReqDTO dto) {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || dto.getUserId() == null || !Objects.equals(dto.getUserType(), UserTypeEnum.ADMIN.getValue())) return;
        String url = StrUtil.nullToDefault(dto.getRequestUrl(), "");
        if (!url.startsWith("/admin-api/")) return;
        WorkLogDO row = BeanUtils.toBean(dto, WorkLogDO.class);
        row.setTenantId(tenant);
        String userName = dto.getUserName();
        if (StrUtil.isBlank(userName)) userName = mapper.findUserName(dto.getUserId(), tenant);
        row.setUserName(StrUtil.blankToDefault(userName, "用户 #" + dto.getUserId()));
        row.setModule(url.substring("/admin-api/".length()).split("/", 2)[0]);
        row.setAction(action(url, dto.getRequestMethod()));
        row.setRequestParams(WorkTrace.sanitizeJson(dto.getRequestParams()));
        row.setChanges(dto.getChanges() == null ? "{\"entries\":[],\"warnings\":[\"此记录来自旧版采集服务，未提供字段变更明细\"]}" : WorkTrace.sanitizeJson(dto.getChanges()));
        row.setBusinessKey(businessKey(row.getRequestParams()));
        row.setSummary(StrUtil.maxLength(StrUtil.blankToDefault(dto.getOperateName(), url), 512));
        row.setResultMsg(StrUtil.maxLength(StrUtil.nullToDefault(dto.getResultMsg(), ""), 1024));
        mapper.insert(row);
    }
    static String action(String url, String method) {
        String path = url.toLowerCase(Locale.ROOT);
        if (path.contains("export")) return "EXPORT";
        if (path.contains("import")) return "IMPORT";
        if (path.contains("reject")) return "REJECT";
        if (path.contains("approve") || path.contains("audit")) return "APPROVE";
        if (path.contains("submit")) return "SUBMIT";
        if ("GET".equals(method) || path.contains("/query") || path.contains("/search")) return "QUERY";
        if ("DELETE".equals(method) || path.contains("/delete")) return "DELETE";
        if ("PUT".equals(method) || "PATCH".equals(method) || path.contains("/update")) return "UPDATE";
        if ("POST".equals(method)) return "CREATE";
        return "OTHER";
    }
    static String businessKey(String json) {
        if (StrUtil.isBlank(json)) return "";
        try {
            List<String> keys = new ArrayList<>();
            collectKeys(JsonUtils.parseObject(json, Object.class), keys, 0);
            return StrUtil.maxLength(String.join(" · ", keys), 1000);
        } catch (RuntimeException e) { return ""; }
    }
    private static void collectKeys(Object value, List<String> keys, int depth) {
        if (depth > 10 || keys.size() >= 12) return;
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object v = entry.getValue();
                if (v == null) continue;
                if (v instanceof Map || v instanceof Collection) collectKeys(v, keys, depth + 1);
                else if (key.matches("(?i).*(id|ids|code|no|name|keyword|number)$")) keys.add(key + ": " + v);
                if (keys.size() >= 12) break;
            }
        } else if (value instanceof Collection<?> values) {
            for (Object v : values) { collectKeys(v, keys, depth + 1); if (keys.size() >= 12) break; }
        }
    }
}
