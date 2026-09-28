package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Metric;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Profile;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Rule;
import cn.iocoder.txgy.module.training.dal.mysql.analytics.PromotionThresholdRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PromotionThresholdService {
    private static final Map<String, String> NEXT_LEVEL = Map.of(
            "APPRENTICE", "JUNIOR", "JUNIOR", "INTERMEDIATE", "INTERMEDIATE", "SENIOR",
            "SENIOR", "TECHNICIAN", "TECHNICIAN", "SENIOR_TECHNICIAN");
    private static final Map<String, String> LEVEL_NAMES = Map.of(
            "APPRENTICE", "学徒技师", "JUNIOR", "初级工", "INTERMEDIATE", "中级工",
            "SENIOR", "高级工", "TECHNICIAN", "技师", "SENIOR_TECHNICIAN", "高级技师");
    private static final Map<String, String> SOURCE_NAMES = Map.ofEntries(
            Map.entry("COURSE", "课程与成长地图"), Map.entry("EXAM", "考试与认证"),
            Map.entry("VR", "实操训练"), Map.entry("HRM", "任职与工时"),
            Map.entry("QUALITY", "质量管理"), Map.entry("SAFETY", "安全管理"),
            Map.entry("PROJECT", "项目实践"), Map.entry("LECTURER", "讲师培养"),
            Map.entry("SKILL", "技能评价"), Map.entry("KNOWLEDGE", "知识沉淀"),
            Map.entry("REVIEW", "专家评审"));

    @Resource private PromotionThresholdRepository db;
    @Resource private SecurityFrameworkService ss;

    private long tenant() {
        Long id = TenantContextHolder.getTenantId();
        check(id != null, "缺少企业信息");
        return id;
    }

    private long loginUser() {
        Long id = SecurityFrameworkUtils.getLoginUserId();
        check(id != null, "请先登录");
        return id;
    }

    private void canManage() { check(ss.hasPermission("training:analytics:promotion:manage"), "无晋级档案维护权限"); }
    private void canRead(long userId) {
        if (userId != loginUser()) canManage();
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new ServiceException(1_045_090_001, message);
    }

    public Map<String, Object> context() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("levels", List.of(
                level("APPRENTICE"), level("JUNIOR"), level("INTERMEDIATE"),
                level("SENIOR"), level("TECHNICIAN"), level("SENIOR_TECHNICIAN")));
        result.put("paths", List.of(
                path("APPRENTICE", "JUNIOR"), path("JUNIOR", "INTERMEDIATE"),
                path("INTERMEDIATE", "SENIOR"), path("SENIOR", "TECHNICIAN"),
                path("TECHNICIAN", "SENIOR_TECHNICIAN")));
        result.put("sources", Map.ofEntries(Map.entry("COURSE", "课程/成长地图"), Map.entry("EXAM", "考试与认证"),
                Map.entry("VR", "VR实操"), Map.entry("HRM", "人力与工时"), Map.entry("QUALITY", "质量"),
                Map.entry("SAFETY", "安全"), Map.entry("PROJECT", "项目"), Map.entry("LECTURER", "直播与讲师"),
                Map.entry("SKILL", "技能矩阵"), Map.entry("KNOWLEDGE", "知识库"), Map.entry("REVIEW", "专家评审")));
        return result;
    }

    private Map<String, Object> path(String from, String to) {
        return Map.of("fromLevel", from, "toLevel", to, "name", LEVEL_NAMES.get(from) + " → " + LEVEL_NAMES.get(to));
    }
    private Map<String, Object> level(String value) { return Map.of("value", value, "label", LEVEL_NAMES.get(value)); }

    public List<Map<String, Object>> rules(String from, String to) {
        long tenantId = tenant();
        return db.list("""
                SELECT r.id,r.from_level,r.to_level,r.rule_key,r.rule_name,r.operator,r.rule_value,r.unit,
                       r.is_hard,r.source_module,r.description,r.sort,r.status,r.tenant_id
                FROM training_promotion_rule r
                WHERE r.deleted=0 AND r.from_level=? AND r.to_level=?
                  AND (r.tenant_id=? OR (r.tenant_id=0 AND NOT EXISTS(
                    SELECT 1 FROM training_promotion_rule x WHERE x.deleted=0 AND x.tenant_id=?
                      AND x.from_level=r.from_level AND x.to_level=r.to_level AND x.rule_key=r.rule_key)))
                ORDER BY r.sort,r.id
                """, from, to, tenantId, tenantId);
    }

    public List<Map<String, Object>> employees(String keyword) {
        canManage();
        String term = "%" + Objects.toString(keyword, "").trim() + "%";
        return db.list("SELECT id,username,nickname FROM system_users WHERE tenant_id=? AND deleted=0 AND status=0 " +
                "AND (username LIKE ? OR nickname LIKE ?) ORDER BY id DESC LIMIT 200", tenant(), term, term);
    }

    public Map<String, Object> profile(Long requestedUserId) {
        long userId = requestedUserId == null ? loginUser() : requestedUserId;
        canRead(userId);
        Map<String, Object> row = db.one("SELECT user_id,current_level,level_since,job_type FROM training_promotion_profile " +
                "WHERE tenant_id=? AND user_id=? AND deleted=0", tenant(), userId);
        if (row != null) return row;
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("userId", userId); fallback.put("currentLevel", "APPRENTICE");
        fallback.put("levelSince", LocalDate.now()); fallback.put("jobType", null);
        return fallback;
    }

    @Transactional
    public void saveProfile(Profile req) {
        canManage();
        check(NEXT_LEVEL.containsKey(req.currentLevel()) || "SENIOR_TECHNICIAN".equals(req.currentLevel()), "人员等级无效");
        db.update("""
                INSERT INTO training_promotion_profile(user_id,current_level,level_since,job_type,tenant_id)
                VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE current_level=VALUES(current_level),
                level_since=VALUES(level_since),job_type=VALUES(job_type),deleted=b'0',update_time=NOW()
                """, req.userId(), req.currentLevel(), req.levelSince(), req.jobType(), tenant());
    }

    @Transactional
    public void reportMetric(Metric req) {
        canManage();
        check(db.count("SELECT COUNT(*) FROM training_promotion_rule WHERE deleted=0 AND rule_key=?", req.ruleKey()) > 0,
                "指标编码不在晋级门槛表中");
        db.update("""
                INSERT INTO training_promotion_metric(user_id,rule_key,metric_value,source_module,evidence,metric_time,tenant_id)
                VALUES(?,?,?,?,?,NOW(),?) ON DUPLICATE KEY UPDATE metric_value=VALUES(metric_value),
                source_module=VALUES(source_module),evidence=VALUES(evidence),metric_time=NOW(),deleted=b'0',update_time=NOW()
                """, req.userId(), req.ruleKey(), req.metricValue(), req.sourceModule(), req.evidence(), tenant());
    }

    @Transactional
    public void updateRule(Rule req) {
        canManage();
        check(Set.of(">=", "<=", "=").contains(req.operator()), "比较符仅支持 >=、<=、=");
        Map<String, Object> original = db.one("SELECT * FROM training_promotion_rule WHERE id=? AND deleted=0 AND tenant_id IN (0,?)", req.id(), tenant());
        check(original != null, "规则不存在");
        db.update("""
                INSERT INTO training_promotion_rule(from_level,to_level,rule_key,rule_name,operator,rule_value,unit,is_hard,
                source_module,description,sort,status,tenant_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE operator=VALUES(operator),rule_value=VALUES(rule_value),is_hard=VALUES(is_hard),
                status=VALUES(status),deleted=b'0',update_time=NOW()
                """, original.get("fromLevel"), original.get("toLevel"), original.get("ruleKey"), original.get("ruleName"),
                req.operator(), req.ruleValue(), original.get("unit"), req.hard(), original.get("sourceModule"),
                original.get("description"), original.get("sort"), req.status(), tenant());
    }

    public Map<String, Object> evaluate(Long requestedUserId, boolean saveSnapshot) {
        long userId = requestedUserId == null ? loginUser() : requestedUserId;
        canRead(userId);
        Map<String, Object> profile = profile(userId);
        String from = Objects.toString(profile.get("currentLevel"), "APPRENTICE");
        String to = NEXT_LEVEL.get(from);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId); result.put("currentLevel", from); result.put("currentLevelName", LEVEL_NAMES.get(from));
        result.put("nextLevel", to); result.put("nextLevelName", LEVEL_NAMES.get(to)); result.put("levelSince", profile.get("levelSince"));
        if (to == null) {
            result.put("qualified", true); result.put("progress", 100); result.put("rules", List.of());
            result.put("passedCount", 0); result.put("totalCount", 0);
            result.put("message", "已达到最高等级"); return result;
        }
        List<Map<String, Object>> rules = rules(from, to).stream().filter(r -> number(r.get("status")).intValue() == 0).toList();
        Map<String, MetricValue> metrics = metricValues(userId, profile, rules);
        List<Map<String, Object>> details = new ArrayList<>();
        BigDecimal totalProgress = BigDecimal.ZERO;
        boolean qualified = true;
        for (Map<String, Object> rule : rules) {
            String key = Objects.toString(rule.get("ruleKey"));
            BigDecimal target = decimal(rule.get("ruleValue"));
            MetricValue metric = metrics.get(key);
            boolean available = metric != null;
            boolean passed = available && compare(metric.value(), Objects.toString(rule.get("operator")), target);
            boolean hard = Boolean.TRUE.equals(rule.get("isHard")) || number(rule.get("isHard")).intValue() == 1;
            if (hard && !passed) qualified = false;
            BigDecimal progress = progress(metric == null ? null : metric.value(), Objects.toString(rule.get("operator")), target, passed);
            totalProgress = totalProgress.add(progress);
            Map<String, Object> item = new LinkedHashMap<>(rule);
            item.put("actualValue", available ? metric.value() : null); item.put("available", available);
            item.put("passed", passed); item.put("progress", progress); item.put("metricSource", available ? metric.source() : rule.get("sourceModule"));
            item.put("metricTime", available ? metric.time() : null);
            item.put("gap", gap(available ? metric.value() : null, Objects.toString(rule.get("operator")), target, Objects.toString(rule.get("unit"), ""), Objects.toString(rule.get("sourceModule"))));
            details.add(item);
        }
        BigDecimal overall = rules.isEmpty() ? BigDecimal.ZERO : totalProgress.divide(BigDecimal.valueOf(rules.size()), 0, RoundingMode.HALF_UP);
        result.put("qualified", qualified && !rules.isEmpty()); result.put("progress", overall); result.put("rules", details);
        result.put("passedCount", details.stream().filter(r -> Boolean.TRUE.equals(r.get("passed"))).count());
        result.put("totalCount", details.size());
        if (saveSnapshot) saveSnapshot(userId, from, to, Boolean.TRUE.equals(result.get("qualified")), overall, details);
        return result;
    }

    private Map<String, MetricValue> metricValues(long userId, Map<String, Object> profile, List<Map<String, Object>> rules) {
        Map<String, MetricValue> values = new HashMap<>();
        for (Map<String, Object> row : db.list("SELECT rule_key,metric_value,source_module,metric_time FROM training_promotion_metric " +
                "WHERE tenant_id=? AND user_id=? AND deleted=0", tenant(), userId)) {
            values.put(Objects.toString(row.get("ruleKey")), new MetricValue(decimal(row.get("metricValue")), Objects.toString(row.get("sourceModule")), row.get("metricTime")));
        }
        Object sinceValue = profile.get("levelSince");
        LocalDate since = sinceValue instanceof LocalDate date ? date : LocalDate.parse(Objects.toString(sinceValue));
        values.put("months_in_level", new MetricValue(BigDecimal.valueOf(Math.max(0, ChronoUnit.MONTHS.between(since, LocalDate.now()))), "人员档案", LocalDate.now()));
        Set<String> keys = new HashSet<>(); rules.forEach(r -> keys.add(Objects.toString(r.get("ruleKey"))));
        if (keys.contains("required_course_rate")) autoCourseRate(values, "required_course_rate", userId, "c.is_required=b'1'");
        if (keys.contains("map_entry_rate")) autoCourseRate(values, "map_entry_rate", userId, "c.course_level='APPRENTICE'");
        if (keys.contains("map_core_rate")) autoCourseRate(values, "map_core_rate", userId, "c.course_level IN ('JUNIOR','MIDDLE')");
        if (keys.contains("map_advanced_rate")) autoCourseRate(values, "map_advanced_rate", userId, "c.course_level='SENIOR'");
        if (keys.contains("map_all_rate")) autoCourseRate(values, "map_all_rate", userId, "1=1");
        return values;
    }

    private void autoCourseRate(Map<String, MetricValue> values, String key, long userId, String condition) {
        if (values.containsKey(key)) return;
        Map<String, Object> row = db.one("SELECT COUNT(DISTINCT t.course_id) total," +
                "COUNT(DISTINCT CASE WHEN t.course_status=2 THEN t.course_id END) completed " +
                "FROM training_student_task t JOIN training_course c ON c.id=t.course_id AND c.deleted=0 " +
                "WHERE t.tenant_id=? AND t.user_id=? AND t.deleted=0 AND " + condition, tenant(), userId);
        long total = number(row == null ? null : row.get("total")).longValue();
        if (total > 0) {
            long completed = number(row.get("completed")).longValue();
            values.put(key, new MetricValue(BigDecimal.valueOf(completed * 100.0 / total).setScale(2, RoundingMode.HALF_UP), "学习任务", LocalDate.now()));
        }
    }

    private void saveSnapshot(long userId, String from, String to, boolean qualified, BigDecimal progress, Object detail) {
        try {
            db.update("INSERT INTO training_promotion_evaluation(user_id,from_level,to_level,qualified,progress,detail_json,evaluated_by,tenant_id) VALUES(?,?,?,?,?,?,?,?)",
                    userId, from, to, qualified, progress, JsonUtils.toJsonString(detail), loginUser(), tenant());
        } catch (Exception ex) {
            throw new ServiceException(1_045_090_002, "保存晋级评估失败");
        }
    }

    private static boolean compare(BigDecimal actual, String operator, BigDecimal target) {
        int cmp = actual.compareTo(target);
        return switch (operator) { case ">=" -> cmp >= 0; case "<=" -> cmp <= 0; case "=" -> cmp == 0; default -> false; };
    }
    private static BigDecimal progress(BigDecimal actual, String operator, BigDecimal target, boolean passed) {
        if (actual == null) return BigDecimal.ZERO;
        if (passed) return BigDecimal.valueOf(100);
        if (">=".equals(operator) && target.signum() > 0) return actual.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(99));
        return BigDecimal.ZERO;
    }
    private static String gap(BigDecimal actual, String operator, BigDecimal target, String unit, String source) {
        if (actual == null) return "暂无" + SOURCE_NAMES.getOrDefault(source, "业务") + "数据";
        if (compare(actual, operator, target)) return "已达标";
        if (">=".equals(operator)) return "还差 " + target.subtract(actual).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + unit;
        if ("<=".equals(operator)) return "需降低 " + actual.subtract(target).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + unit;
        return "要求为 " + target.stripTrailingZeros().toPlainString() + unit;
    }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal d ? d : new BigDecimal(Objects.toString(value, "0")); }
    private static Number number(Object value) { return value instanceof Number n ? n : 0; }
    private record MetricValue(BigDecimal value, String source, Object time) {}
}
