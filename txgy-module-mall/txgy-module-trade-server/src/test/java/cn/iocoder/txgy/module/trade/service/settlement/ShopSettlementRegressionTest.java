package cn.iocoder.txgy.module.trade.service.settlement;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes production settlement SQL against an isolated database, not mocked query results. */
class ShopSettlementRegressionTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ShopSettlementService service;
    private MockedStatic<SecurityFrameworkUtils> security;
    private final LocalDateTime received = LocalDateTime.of(2020, 1, 15, 12, 0);

    @BeforeEach
    void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:settlement-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        // Reuse the deployable DDL so bill constraints/schema cannot silently diverge in tests.
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("deploy/mysql/repair/2026-09-12-mall-shop-settlement.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root, "Run from within the repository");
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve(
                "deploy/mysql/repair/2026-09-12-mall-shop-settlement.sql"))).execute(source);
        jdbc.execute("CREATE TABLE trade_order (id BIGINT PRIMARY KEY, tenant_id BIGINT, shop_id BIGINT,"
                + " no VARCHAR(64), status INT, pay_status INT, pay_price BIGINT, delivery_price BIGINT,"
                + " refund_price BIGINT, receive_time TIMESTAMP, finish_time TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE trade_order_item (id BIGINT PRIMARY KEY, tenant_id BIGINT, order_id BIGINT,"
                + " shop_id BIGINT, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE trade_after_sale (id BIGINT PRIMARY KEY, tenant_id BIGINT, order_id BIGINT,"
                + " status INT, deleted INT DEFAULT 0)");
        service = new ShopSettlementService();
        var shops = mock(ProductShopApi.class);
        var shop = new ProductShopRespDTO();
        shop.setId(10L);
        shop.setName("结算回归店铺");
        when(shops.getShop(10L)).thenReturn(success(shop));
        var authorization = mock(ProductSupplierApi.class);
        when(authorization.validateShop(5L, 10L)).thenReturn(success(true));
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "shops", shops);
        ReflectionTestUtils.setField(service, "authorization", authorization);
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class);
        login(false);
    }

    @AfterEach
    void cleanup() {
        if (security != null) security.close();
        TenantContextHolder.clear();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    private void login(boolean member) {
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser()
                .setId(member ? 5L : 1L).setUserType(member ? UserTypeEnum.MEMBER.getValue() : UserTypeEnum.ADMIN.getValue()));
    }

    private void order(long id, LocalDateTime receipt, LocalDateTime review) {
        jdbc.update("INSERT INTO trade_order VALUES(?,1,10,?,30,1,10000,1000,0,?,?,0)",
                id, "QA-" + id, receipt, review);
        jdbc.update("INSERT INTO trade_order_item VALUES(?,1,?,10,0)", id, id);
    }

    private Bill generate(String month) {
        return tx.execute(status -> service.generate(new Generate(10L, month, 500)));
    }

    @SuppressWarnings("unchecked")
    private List<Item> lines(Bill bill) {
        return (List<Item>) service.detail(bill.getId(), null, false).get("items");
    }

    private Bill settledOriginal() {
        order(1, received, null);
        Bill bill = generate("2020-01");
        // Model an earlier offline-registered bill; no transfer API is invoked by this fixture.
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=40 WHERE id=?", bill.getId());
        return bill;
    }

    private void completeCarry(Bill bill) {
        tx.executeWithoutResult(status -> service.audit(new Audit(bill.getId(), bill.getVersion(), true, "核对退款")));
        login(true);
        tx.executeWithoutResult(status -> service.confirm(new Confirm(10L, bill.getId(), bill.getVersion())));
        login(false);
        tx.executeWithoutResult(status -> service.carry(bill.getId(), bill.getVersion()));
    }

    @Test
    void receiptNotReviewDeterminesEligibilityAndMonth() {
        order(1, received, null); // received, not reviewed: must be included
        order(2, received, received.plusMonths(2)); // later review must not move its month
        order(3, LocalDateTime.of(2020, 2, 1, 0, 0), received); // exact upper boundary: next month
        order(4, null, received); // no receipt evidence: must not use a review fallback
        Bill bill = generate("2020-01");
        assertEquals(2, bill.getOrderCount());
        assertEquals(19000L, bill.getSettlementAmount());
        assertEquals(List.of(1L, 2L), lines(bill).stream().map(Item::getOrderId).toList());
        assertTrue(lines(bill).stream().allMatch(item -> received.equals(item.getFinishTime())));
    }

    @Test
    void deletedUnsettledOrdersAreStillExcluded() {
        order(1, received, null);
        order(2, received, null);
        jdbc.update("UPDATE trade_order SET deleted=1 WHERE id=2");
        assertEquals(List.of(1L), lines(generate("2020-01")).stream().map(Item::getOrderId).toList());
    }

    @Test
    void deletedOriginalRefundCanBeAuditedConfirmedCarriedAndDoesNotRepeat() {
        settledOriginal();
        jdbc.update("UPDATE trade_order SET status=40,refund_price=10000,deleted=1 WHERE id=1");
        Bill refund = generate("2020-02");
        assertEquals(0, refund.getOrderCount());
        assertEquals(-9500L, refund.getAdjustmentAmount());
        assertEquals(-500L, lines(refund).getFirst().getCommissionAmount());
        completeCarry(refund);
        order(2, LocalDateTime.of(2020, 3, 2, 0, 0), null);
        Bill next = generate("2020-03");
        assertEquals(9500L, next.getCarryAmount());
        assertEquals(0L, next.getAdjustmentAmount());
        assertEquals(0L, next.getSettlementAmount());
        assertEquals(1, next.getOrderCount());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM trade_order WHERE id=1", Integer.class));
    }

    @Test
    void deletingOriginalBetweenGenerationAndAuditDoesNotBlockRefundLine() {
        settledOriginal();
        jdbc.update("UPDATE trade_order SET status=40,refund_price=10000 WHERE id=1");
        Bill refund = generate("2020-02");
        jdbc.update("UPDATE trade_order SET deleted=1 WHERE id=1");
        assertDoesNotThrow(() -> completeCarry(refund));
    }

    @Test
    void newSaleDeletedAfterGenerationCannotPassAudit() {
        order(1, received, null);
        Bill bill = generate("2020-01");
        jdbc.update("UPDATE trade_order SET deleted=1 WHERE id=1");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status ->
                service.audit(new Audit(bill.getId(), bill.getVersion(), true, "不得放行"))));
        assertEquals(10, jdbc.queryForObject("SELECT status FROM trade_shop_settlement_bill WHERE id=?", Integer.class, bill.getId()));
    }

    @Test
    void physicallyMissingOriginalStillFailsClosedAndRollsBackNewBill() {
        settledOriginal();
        jdbc.update("DELETE FROM trade_order WHERE id=1");
        assertTrue(assertThrows(RuntimeException.class, () -> generate("2020-02")).getMessage().contains("历史已结订单缺失"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill", Integer.class));
    }

    @Test
    void deletedOriginalCannotCrossTenantOrShopBoundary() {
        settledOriginal();
        jdbc.update("UPDATE trade_order SET tenant_id=2,deleted=1,refund_price=10000 WHERE id=1");
        assertThrows(RuntimeException.class, () -> generate("2020-02"));
        jdbc.update("UPDATE trade_order SET tenant_id=1,shop_id=99 WHERE id=1");
        assertThrows(RuntimeException.class, () -> generate("2020-02"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill", Integer.class));
    }
}
