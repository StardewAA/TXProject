package cn.iocoder.txgy.module.cms.service.finance;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.service.knowledge.CmsKnowledgeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_POLICY_CATEGORY_REQUIRED;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_POLICY_VERSION_CONFLICT;

/** 金融政策结构化字段管理；正文生命周期仍由 CMS 审查与发布流程统一负责。 */
@Service
public class CmsFinancePolicyService {

    private final JdbcTemplate jdbc;
    private final CmsKnowledgeService knowledgeService;

    public CmsFinancePolicyService(JdbcTemplate jdbc, CmsKnowledgeService knowledgeService) {
        this.jdbc = jdbc;
        this.knowledgeService = knowledgeService;
    }

    public FinancePolicyDetail get(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<FinancePolicyDetail> rows = jdbc.query("""
                        SELECT review_id,applicable_regions,applicable_industries,applicable_enterprise_types,
                               support_type,amount_min,amount_max,amount_unit,support_rate,support_description,
                               apply_start_time,apply_end_time,policy_status,interpretation_status,
                               interpretation_json,version
                        FROM cms_finance_policy_ext
                        WHERE tenant_id=? AND review_id=? AND deleted=0
                        """, (rs, row) -> new FinancePolicyDetail(rs.getString(1),
                        values(rs.getString(2)), values(rs.getString(3)), values(rs.getString(4)),
                        rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8),
                        rs.getBigDecimal(9), rs.getString(10), timestamp(rs, 11), timestamp(rs, 12),
                        rs.getInt(13), rs.getString(14), rs.getString(15), rs.getInt(16)), tenantId, reviewId);
        if (rows.isEmpty()) {
            Integer count = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM cms_knowledge_document
                            WHERE tenant_id=? AND review_id=? AND doc_category='FINANCE_POLICY'
                            """, Integer.class, tenantId, reviewId);
            if (count == null || count == 0) throw exception(FINANCE_POLICY_CATEGORY_REQUIRED);
            return new FinancePolicyDetail(reviewId, List.of(), List.of(), List.of(), null,
                    null, null, "万元", null, null, null, null, 1, "PENDING", null, 0);
        }
        return rows.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public FinancePolicyDetail save(String reviewId, FinancePolicyInput input) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        requireFinanceDocument(tenantId, reviewId);
        validateRanges(input);
        List<String> regions = normalize(input.applicableRegions());
        List<String> industries = normalize(input.applicableIndustries());
        List<String> enterpriseTypes = normalize(input.applicableEnterpriseTypes());
        Integer currentVersion = jdbc.query("""
                        SELECT version FROM cms_finance_policy_ext
                        WHERE tenant_id=? AND review_id=? AND deleted=0 FOR UPDATE
                        """, (rs, row) -> rs.getInt(1), tenantId, reviewId).stream().findFirst().orElse(null);
        if (currentVersion == null) {
            if (input.version() != 0) throw exception(FINANCE_POLICY_VERSION_CONFLICT);
            jdbc.update("""
                            INSERT INTO cms_finance_policy_ext
                              (tenant_id,review_id,applicable_regions,applicable_industries,
                               applicable_enterprise_types,support_type,amount_min,amount_max,amount_unit,
                               support_rate,support_description,apply_start_time,apply_end_time,policy_status,version)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                            """, tenantId, reviewId, JsonUtils.toJsonString(regions), JsonUtils.toJsonString(industries),
                    JsonUtils.toJsonString(enterpriseTypes), clean(input.supportType()), input.amountMin(),
                    input.amountMax(), defaultUnit(input.amountUnit()), input.supportRate(),
                    clean(input.supportDescription()), input.applyStartTime(), input.applyEndTime(), input.policyStatus());
        } else {
            int updated = jdbc.update("""
                            UPDATE cms_finance_policy_ext SET applicable_regions=?,applicable_industries=?,
                              applicable_enterprise_types=?,support_type=?,amount_min=?,amount_max=?,amount_unit=?,
                              support_rate=?,support_description=?,apply_start_time=?,apply_end_time=?,policy_status=?,
                              version=version+1
                            WHERE tenant_id=? AND review_id=? AND deleted=0 AND version=?
                            """, JsonUtils.toJsonString(regions), JsonUtils.toJsonString(industries),
                    JsonUtils.toJsonString(enterpriseTypes), clean(input.supportType()), input.amountMin(),
                    input.amountMax(), defaultUnit(input.amountUnit()), input.supportRate(),
                    clean(input.supportDescription()), input.applyStartTime(), input.applyEndTime(), input.policyStatus(),
                    tenantId, reviewId, input.version());
            if (updated != 1) throw exception(FINANCE_POLICY_VERSION_CONFLICT);
        }
        knowledgeService.refreshPublishedDocument(reviewId);
        return get(reviewId);
    }

    private void requireFinanceDocument(long tenantId, String reviewId) {
        List<String> categories = jdbc.query("""
                        SELECT doc_category FROM cms_knowledge_document
                        WHERE tenant_id=? AND review_id=? FOR UPDATE
                        """, (rs, row) -> rs.getString(1), tenantId, reviewId);
        if (categories.isEmpty() || !"FINANCE_POLICY".equalsIgnoreCase(categories.getFirst())) {
            throw exception(FINANCE_POLICY_CATEGORY_REQUIRED);
        }
    }

    private static void validateRanges(FinancePolicyInput input) {
        if (input.amountMin() != null && input.amountMax() != null
                && input.amountMin().compareTo(input.amountMax()) > 0) {
            throw new IllegalArgumentException("最低金额不能大于最高金额");
        }
        if (input.applyStartTime() != null && input.applyEndTime() != null
                && input.applyStartTime().isAfter(input.applyEndTime())) {
            throw new IllegalArgumentException("申报开始时间不能晚于截止时间");
        }
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().map(CmsFinancePolicyService::clean).filter(value -> value != null && !value.isBlank())
                .limit(50).forEach(result::add);
        return List.copyOf(result);
    }

    private static List<String> values(String json) {
        List<String> values = JsonUtils.parseArray(json, String.class);
        return values == null ? List.of() : List.copyOf(values);
    }

    private static LocalDateTime timestamp(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return rs.getTimestamp(index) == null ? null : rs.getTimestamp(index).toLocalDateTime();
    }

    private static String clean(String value) { return value == null ? null : value.strip(); }
    private static String defaultUnit(String value) { return value == null || value.isBlank() ? "万元" : value.strip(); }

    public record FinancePolicyInput(List<String> applicableRegions, List<String> applicableIndustries,
                                     List<String> applicableEnterpriseTypes, String supportType,
                                     BigDecimal amountMin, BigDecimal amountMax, String amountUnit,
                                     BigDecimal supportRate, String supportDescription,
                                     LocalDateTime applyStartTime, LocalDateTime applyEndTime,
                                     int policyStatus, int version) { }

    public record FinancePolicyDetail(String reviewId, List<String> applicableRegions,
                                      List<String> applicableIndustries, List<String> applicableEnterpriseTypes,
                                      String supportType, BigDecimal amountMin, BigDecimal amountMax,
                                      String amountUnit, BigDecimal supportRate, String supportDescription,
                                      LocalDateTime applyStartTime, LocalDateTime applyEndTime, int policyStatus,
                                      String interpretationStatus, String interpretationJson, int version) { }
}
