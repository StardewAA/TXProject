package cn.iocoder.txgy.module.ai.service.model;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 按租户和业务场景解析主、备模型。路由表未迁移时兼容回退到默认模型。 */
@Service
@Slf4j
public class AiModelSceneRouteService {

    private final JdbcTemplate jdbc;

    public AiModelSceneRouteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> resolve(String sceneCode) {
        if (sceneCode == null || sceneCode.isBlank()) return List.of();
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) return List.of();
        try {
            return jdbc.query("""
                    SELECT primary_model_id,fallback_model_id
                    FROM ai_model_scene_route
                    WHERE tenant_id=? AND scene_code=? AND enabled=1 AND deleted=0
                    LIMIT 1
                    """, rs -> {
                if (!rs.next()) return List.of();
                List<Long> ids = new ArrayList<>(2);
                long primary = rs.getLong(1);
                if (!rs.wasNull()) ids.add(primary);
                long fallback = rs.getLong(2);
                if (!rs.wasNull() && !ids.contains(fallback)) ids.add(fallback);
                return List.copyOf(ids);
            }, tenantId, sceneCode.strip().toUpperCase());
        } catch (DataAccessException ex) {
            log.warn("[resolve][场景模型路由不可用，降级到默认模型 sceneCode={}]", sceneCode, ex);
            return List.of();
        }
    }
}
