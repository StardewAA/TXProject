package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InquiryInvitedScopeMapperContractTest {

    @Test
    void crossTenantTargetLookupKeepsAllEligibilityGuards() throws Exception {
        InterceptorIgnore ignore = InquiryInvitedScopeMapper.class.getAnnotation(InterceptorIgnore.class);
        assertTrue(ignore != null && "true".equals(ignore.tenantLine()));
        Select select = InquiryInvitedScopeMapper.class.getMethod("selectEligibleTarget",
                String.class, Long.class, Long.class, Long.class).getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value()));
        assertTrue(sql.contains("pt.tenant_id&lt;&gt;3000"));
        assertTrue(sql.contains("pt.open_status=0"));
        assertTrue(sql.contains("st.status=0"));
        assertTrue(sql.contains("pe.audit_status=1"));
        assertTrue(sql.contains("ps.status=0"));
        assertTrue(sql.contains("ps.enterprise_id=pe.id"));
    }

    @Test
    void invitationDirectoryIsBoundedFilteredAndReturnsOnlyPublicIdentityFields() throws Exception {
        Select select = InquiryInvitedScopeMapper.class.getMethod("selectEligibleShopOptions",
                Long.class, String.class, int.class).getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value()));
        assertTrue(sql.contains("pt.tenant_id&lt;&gt;#{sourceTenantId}"));
        assertTrue(sql.contains("pt.tenant_id&lt;&gt;3000"));
        assertTrue(sql.contains("pt.deleted=b'0' AND pt.open_status=0"));
        assertTrue(sql.contains("st.deleted=b'0' AND st.status=0"));
        assertTrue(sql.contains("st.expire_time IS NULL OR st.expire_time&gt;NOW()"));
        assertTrue(sql.contains("pe.deleted=b'0' AND pe.audit_status=1"));
        assertTrue(sql.contains("ps.enterprise_id=pe.id AND ps.deleted=b'0' AND ps.status=0"));
        assertTrue(sql.contains("#{keyword}"));
        assertTrue(sql.contains("LIMIT #{limit}"));
        assertTrue(!sql.toLowerCase().contains("mobile") && !sql.toLowerCase().contains("contact")
                && !sql.toLowerCase().contains("license") && !sql.toLowerCase().contains("stock"));
        Set<String> rowFields = Arrays.stream(InquiryInvitedScopeMapper.InvitationTargetRow.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertTrue(rowFields.equals(Set.of("tenantId", "tenantName", "enterpriseId", "enterpriseName",
                "shopId", "shopName")));
    }
}
