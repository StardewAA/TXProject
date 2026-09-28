package cn.iocoder.txgy.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bpm.dal.mysql.wms.WmsApprovalPermissionMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WmsApprovalCandidatesTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private WmsApprovalCandidates candidates;

    @BeforeEach void setup() {
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:wms-permission-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE system_menu(id BIGINT PRIMARY KEY, permission VARCHAR(100), type INT, status INT DEFAULT 0, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_tenant_package(id BIGINT PRIMARY KEY, menu_ids VARCHAR(500), status INT DEFAULT 0, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_tenant(id BIGINT PRIMARY KEY, package_id BIGINT, status INT DEFAULT 0, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_users(id BIGINT PRIMARY KEY, tenant_id BIGINT, status INT DEFAULT 0, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_role(id BIGINT PRIMARY KEY, tenant_id BIGINT, code VARCHAR(100), status INT DEFAULT 0, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_user_role(user_id BIGINT, role_id BIGINT, tenant_id BIGINT, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE system_role_menu(role_id BIGINT, menu_id BIGINT, tenant_id BIGINT, deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO system_menu(id,permission,type) VALUES (1,'wms:receipt-order:approve-warehouse',3),(2,'wms:shipment-order:approve-supervisor',3),(3,'wms:receipt-order:submit-approval',3)");
        jdbc.update("INSERT INTO system_tenant_package(id,menu_ids) VALUES(100,'[1,2,3]'),(200,'[1,2,3]')");
        jdbc.update("INSERT INTO system_tenant(id,package_id) VALUES(10,100),(20,200)");
        grant(11, 101, 10, 1); grant(12, 102, 10, 2); grant(13, 103, 10, 3); grant(21, 201, 20, 1);
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(WmsApprovalPermissionMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        candidates = new WmsApprovalCandidates(session.getMapper(WmsApprovalPermissionMapper.class));
        TenantContextHolder.setTenantId(10L);
    }
    @AfterEach void cleanup() {
        TenantContextHolder.clear();
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }
    private void grant(long user, long role, long tenant, long menu) {
        jdbc.update("INSERT INTO system_users(id,tenant_id) VALUES(?,?)",user,tenant);
        jdbc.update("INSERT INTO system_role(id,tenant_id) VALUES(?,?)",role,tenant);
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(?,?,?)",user,role,tenant);
        jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(?,?,?)",role,menu,tenant);
    }
    @Test void separatesReceiptShipmentAndSubmitOnlyPermissions() {
        assertEquals(Set.of(11L), candidates.resolve("wms_receipt_approval"));
        assertEquals(Set.of(12L), candidates.resolve("wms_shipment_approval"));
    }
    @Test void delegateCandidatesIncludeEveryShipmentApprovalRoleButExcludeSubmitOnly() {
        jdbc.update("INSERT INTO system_menu(id,permission,type) VALUES (4,'wms:shipment-order:approve-decision',3)");
        jdbc.update("UPDATE system_tenant_package SET menu_ids='[1,2,3,4]' WHERE id=100");
        grant(14,104,10,4);
        assertEquals(Set.of(11L), candidates.resolveDelegateUsers("wms_receipt_approval"));
        assertEquals(Set.of(12L,14L), candidates.resolveDelegateUsers("wms_shipment_approval"));
        assertFalse(candidates.resolveDelegateUsers("wms_shipment_approval").contains(13L));
    }
    @Test void anyRoleGrantWorksAndChangesApplyToNextResolution() {
        assertEquals(Set.of(11L), candidates.resolve("wms_receipt_approval"));
        jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(102,1,10)");
        assertEquals(Set.of(11L,12L), candidates.resolve("wms_receipt_approval"));
        jdbc.update("UPDATE system_role_menu SET deleted=1 WHERE role_id=101");
        assertEquals(Set.of(12L), candidates.resolve("wms_receipt_approval"));
    }
    @Test void activeSuperAdminIsAnApprovalCandidateWithoutExplicitMenuGrant() {
        jdbc.update("INSERT INTO system_users(id,tenant_id) VALUES(18,10)");
        jdbc.update("INSERT INTO system_role(id,tenant_id,code) VALUES(108,10,'super_admin')");
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(18,108,10)");
        assertEquals(Set.of(11L,18L), candidates.resolve("wms_receipt_approval"));
        assertEquals(Set.of(12L,18L), candidates.resolve("wms_shipment_approval"));
    }
    @Test void activeTenantAdminIsAnApprovalCandidateWithoutExplicitMenuGrant() {
        jdbc.update("INSERT INTO system_users(id,tenant_id) VALUES(19,10)");
        jdbc.update("INSERT INTO system_role(id,tenant_id,code) VALUES(109,10,'tenant_admin')");
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(19,109,10)");
        assertEquals(Set.of(11L,19L), candidates.resolve("wms_receipt_approval"));
        assertEquals(Set.of(12L,19L), candidates.resolve("wms_shipment_approval"));
    }
    @Test void deduplicatesUsersAcrossRoles() {
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(11,103,10)");
        jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(103,1,10)");
        assertEquals(Set.of(11L,13L), candidates.resolve("wms_receipt_approval"));
    }
    @Test void rejectsCrossTenantLinksAndUsesCurrentTenant() {
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(21,101,10),(12,201,10),(12,101,20)");
        jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(102,1,20)");
        assertEquals(Set.of(11L), candidates.resolve("wms_receipt_approval"));
        TenantContextHolder.setTenantId(20L);
        assertEquals(Set.of(21L), candidates.resolve("wms_receipt_approval"));
    }
    @Test void excludesDisabledAndDeletedUsersRolesAndMemberships() {
        grant(14,104,10,1); grant(15,105,10,1); grant(16,106,10,1); grant(17,107,10,1);
        jdbc.update("UPDATE system_users SET status=1 WHERE id=14");
        jdbc.update("UPDATE system_role SET status=1 WHERE id=105");
        jdbc.update("UPDATE system_users SET deleted=1 WHERE id=16");
        jdbc.update("UPDATE system_user_role SET deleted=1 WHERE user_id=17");
        assertEquals(Set.of(11L), candidates.resolve("wms_receipt_approval"));
        jdbc.update("UPDATE system_role SET deleted=1 WHERE id=101");
        assertTrue(candidates.resolve("wms_receipt_approval").isEmpty());
    }
    @Test void permissionMustBeEnabledAndIncludedInTenantPackage() {
        jdbc.update("UPDATE system_tenant_package SET menu_ids='[2,3]' WHERE id=100");
        assertTrue(candidates.resolve("wms_receipt_approval").isEmpty());
        jdbc.update("UPDATE system_menu SET status=1 WHERE id=2");
        assertTrue(candidates.resolve("wms_shipment_approval").isEmpty());
    }
    @Test void disabledTenantOrPackageHasNoCandidates() {
        jdbc.update("UPDATE system_tenant_package SET status=1 WHERE id=100");
        assertTrue(candidates.resolve("wms_receipt_approval").isEmpty());
        jdbc.update("UPDATE system_tenant_package SET status=0 WHERE id=100");
        jdbc.update("UPDATE system_tenant SET status=1 WHERE id=10");
        assertTrue(candidates.resolve("wms_receipt_approval").isEmpty());
    }
    @Test void missingTenantAndUnrelatedProcessesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> candidates.resolve("erp-sale-order-approval"));
        assertThrows(IllegalArgumentException.class, () -> candidates.resolve("wms_transfer_approval"));
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> candidates.resolve("wms_receipt_approval"));
    }
}
