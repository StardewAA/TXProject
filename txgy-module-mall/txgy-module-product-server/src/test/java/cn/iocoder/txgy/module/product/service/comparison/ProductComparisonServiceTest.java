package cn.iocoder.txgy.module.product.service.comparison;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.app.comparison.*;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.review.*;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MyBatis SQL and H2 persistence, synthetic three-shop samples only; no application database. */
class ProductComparisonServiceTest {
    JdbcTemplate jdbc; ProductComparisonService service; ProductSpuMapper spus; ProductSkuMapper skus;
    ProductShopService shops; ComparisonListRepository repository; ProductReviewProperties policy;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:comparison-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(source);
        String audit = "creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,tenant_id BIGINT NOT NULL";
        jdbc.execute("CREATE TABLE product_spu(id BIGINT PRIMARY KEY,shop_id BIGINT,name VARCHAR(255),keyword VARCHAR(255),"
                + "introduction VARCHAR(255),description CLOB,category_id BIGINT,brand_id BIGINT,pic_url VARCHAR(512),slider_pic_urls VARCHAR(3000),"
                + "sort INT,status INT,spec_type BOOLEAN,price INT,market_price INT,cost_price INT,stock INT,delivery_types VARCHAR(100),"
                + "delivery_template_id BIGINT,give_integral INT,sub_commission_type BOOLEAN,sales_count INT,virtual_sales_count INT,browse_count INT,"
                + "review_status INT,content_version BIGINT DEFAULT 0,approved_version BIGINT,submission_id VARCHAR(64),review_remark VARCHAR(1000)," + audit + ")");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT PRIMARY KEY,spu_id BIGINT,properties VARCHAR(3000),price INT,market_price INT,cost_price INT,"
                + "bar_code VARCHAR(64),pic_url VARCHAR(512),stock INT,wms_sku_id BIGINT,wms_warehouse_id BIGINT,weight DOUBLE,volume DOUBLE,"
                + "first_brokerage_price INT,second_brokerage_price INT,sales_count INT," + audit + ")");
        jdbc.execute("CREATE TABLE product_comparison_list(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,member_id BIGINT NOT NULL,"
                + "version BIGINT NOT NULL,sku_ids VARCHAR(100) NOT NULL,quantity INT NOT NULL,preference VARCHAR(16) NOT NULL,"
                + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,member_id))");
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProductSpuMapper.class); configuration.addMapper(ProductSkuMapper.class);
        // Deliberately no tenant interceptor: new queries must be explicitly safe on their own.
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());
        spus = sessions.getMapper(ProductSpuMapper.class); skus = sessions.getMapper(ProductSkuMapper.class);
        shops = mock(ProductShopService.class);
        when(shops.getShop(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0); var s = new ProductShopDO().setId(id).setName("样例店" + id).setEnterpriseId(id * 10).setStatus(0);
            s.setTenantId(id == 400 ? 2L : 1L); return s;
        });
        var enterprises = mock(ProductEnterpriseMapper.class);
        when(enterprises.selectById(anyLong())).thenReturn(new ProductEnterpriseDO().setAuditStatus(1));
        var review = new ProductSpuReviewService(); policy = new ProductReviewProperties();
        ReflectionTestUtils.setField(review, "shopService", shops); ReflectionTestUtils.setField(review, "enterpriseMapper", enterprises);
        ReflectionTestUtils.setField(review, "properties", policy);
        repository = new ComparisonListRepository(); ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        service = new ProductComparisonService(); ReflectionTestUtils.setField(service, "skuMapper", skus);
        ReflectionTestUtils.setField(service, "spuMapper", spus); ReflectionTestUtils.setField(service, "reviewService", review);
        ReflectionTestUtils.setField(service, "shopService", shops); ReflectionTestUtils.setField(service, "repository", repository);
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO product_spu(id,shop_id,name,pic_url,status,price,stock,review_status,content_version,approved_version,tenant_id) VALUES(?,?,?,?,1,1,123,20,1,1,?)",
                    i * 10, i * 100, i == 4 ? "不可公开秘密商品" : "同名样例保温板", "https://example.test/spu.png", i == 4 ? 2 : 1);
            jdbc.update("INSERT INTO product_sku(id,spu_id,properties,price,cost_price,pic_url,stock,wms_sku_id,tenant_id) VALUES(?,?,?,?,99,?,100,1234,?)",
                    i * 10 + 1, i * 10, "[{\"propertyId\":1,\"propertyName\":\"厚度\",\"valueId\":" + i + ",\"valueName\":\"" + i * 10 + "mm\"}]",
                    i * 100, "https://example.test/sku.png", i == 4 ? 2 : 1);
        }
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); jdbc.execute("DROP ALL OBJECTS"); }
    ComparisonRequest request(Long... ids) { var r = new ComparisonRequest(); r.setSkuIds(List.of(ids)); r.setVersion(0L); return r; }

    @Test void threeShopRawValuesStayDistinctWithoutInventingSameModelRecommendation() {
        var response = service.calculate(request(11L, 21L, 31L));
        assertEquals("INSUFFICIENT_STANDARD_DATA", response.getRecommendationStatus()); assertNull(response.getRecommendedSkuId());
        assertEquals(List.of(100, 200, 300), response.getItems().stream().map(ComparisonResponse.Item::getPrice).toList());
        assertEquals(List.of("10mm", "20mm", "30mm"), response.getItems().stream().map(i -> i.getProperties().getFirst().value()).toList());
        assertTrue(response.getItems().stream().allMatch(i -> i.isAvailable() && "UNKNOWN".equals(i.getStandardStatus())));
        assertTrue(response.getItems().stream().allMatch(i -> "PENDING_ERP".equals(i.getStockStatus())));
        assertTrue(response.getItems().stream().allMatch(i -> i.getDifferences().stream().anyMatch(d -> d.contains("未标准化"))));
    }
    @Test void sameRawNameAndSpecStillDoNotProveSameModel() {
        jdbc.update("UPDATE product_sku SET properties=(SELECT properties FROM product_sku WHERE id=11) WHERE id IN(21,31)");
        var response = service.calculate(request(11L,21L,31L));
        assertEquals("INSUFFICIENT_STANDARD_DATA", response.getRecommendationStatus()); assertNull(response.getRecommendedSkuId());
    }
    @Test void missingSpecificationsRemainUnknown() {
        jdbc.update("UPDATE product_sku SET properties=NULL WHERE id=31");
        var item = service.calculate(request(11L,31L)).getItems().getLast();
        assertTrue(item.getProperties().isEmpty()); assertFalse(item.getUnknowns().isEmpty()); assertEquals("UNKNOWN", item.getStandardStatus());
    }
    @Test void crossTenantAndMissingIdsHaveIdenticalRedactedShape() {
        var items = service.calculate(request(41L,9999L)).getItems();
        for (var item : items) {
            assertFalse(item.isAvailable()); assertNull(item.getPrice()); assertNull(item.getSpuName()); assertNull(item.getSpuId());
            assertNull(item.getShopId()); assertNull(item.getPicUrl()); assertTrue(item.getProperties().isEmpty());
            assertEquals("商品暂不可比较", item.getUnavailableReason());
        }
    }
    @Test void corruptCrossTenantSkuToSpuRelationshipCannotLeak() {
        jdbc.update("UPDATE product_sku SET tenant_id=1 WHERE id=41");
        assertFalse(service.calculate(request(11L,41L)).getItems().getLast().isAvailable());
    }
    @Test void deletedSkuAndSpuAreNotVisible() {
        jdbc.update("UPDATE product_sku SET deleted=TRUE WHERE id=11");
        jdbc.update("UPDATE product_spu SET deleted=TRUE WHERE id=20");
        assertTrue(service.calculate(request(11L,21L)).getItems().stream().noneMatch(ComparisonResponse.Item::isAvailable));
    }
    @Test void pendingVersionNeverLeaksNewPriceOrName() {
        jdbc.update("UPDATE product_spu SET review_status=10,name='未审核秘密价格',status=1 WHERE id=10");
        assertFalse(service.calculate(request(11L,21L)).getItems().getFirst().isAvailable());
        assertNull(service.calculate(request(11L,21L)).getItems().getFirst().getPrice());
    }
    @Test void mismatchedApprovedVersionIsUnavailable() {
        jdbc.update("UPDATE product_spu SET content_version=2 WHERE id=10");
        assertFalse(service.calculate(request(11L,21L)).getItems().getFirst().isAvailable());
    }
    @Test void explicitUnmodifiedLegacyRemainsSaleable() {
        jdbc.update("UPDATE product_spu SET review_status=NULL,approved_version=NULL WHERE id=10");
        assertTrue(service.calculate(request(11L,21L)).getItems().getFirst().isAvailable());
        policy.setLegacyStrategy("REQUIRE_REVIEW");
        assertFalse(service.calculate(request(11L,21L)).getItems().getFirst().isAvailable());
    }
    @Test void disabledOrForeignShopCannotBeCompared() {
        var disabled = new ProductShopDO().setId(100L).setStatus(1); disabled.setTenantId(1L);
        when(shops.getShop(100L)).thenReturn(disabled);
        var foreign = new ProductShopDO().setId(200L).setStatus(0).setEnterpriseId(2000L); foreign.setTenantId(2L);
        when(shops.getShop(200L)).thenReturn(foreign);
        assertTrue(service.calculate(request(11L,21L)).getItems().stream().noneMatch(ComparisonResponse.Item::isAvailable));
    }
    @Test void sameShopAndSingleSelectionHaveClearNonRecommendationReason() {
        assertEquals("NEED_MORE_ITEMS", service.calculate(request(11L)).getRecommendationStatus());
        jdbc.update("UPDATE product_spu SET shop_id=100 WHERE id=20");
        assertEquals("NEED_DIFFERENT_SHOPS", service.calculate(request(11L,21L)).getRecommendationStatus());
    }
    @Test void freshPriceReadNeverUsesSavedSelectionAsPriceSnapshot() {
        service.saveList(316L,request(11L,21L));
        jdbc.update("UPDATE product_sku SET price=2222 WHERE id=11");
        assertEquals(2222, service.calculate(request(11L,21L)).getItems().getFirst().getPrice());
    }
    @Test void priceIsExactSkuNotSpuMinimumAndQuantityDoesNotInventQuote() {
        var req=request(11L,21L); req.setQuantity(999);
        var response=service.calculate(req); assertEquals(100,response.getItems().getFirst().getPrice()); assertEquals(999,response.getQuantity());
        assertEquals("SALE_UNIT",response.getQuantityUnit()); assertTrue(response.getWarnings().stream().anyMatch(w->w.contains("不是")));
    }
    @Test void calculateHasNoDatabaseOrStockSideEffects() {
        service.calculate(request(11L,21L,31L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_comparison_list",Integer.class));
        assertEquals(400,jdbc.queryForObject("SELECT SUM(stock) FROM product_sku",Integer.class));
        assertEquals(492,jdbc.queryForObject("SELECT SUM(stock) FROM product_spu",Integer.class));
    }
    @Test void emptyReadIsSideEffectFreeAndIdentityScoped() {
        var result=service.getList(316L); assertEquals(0,result.version()); assertTrue(result.skuIds().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_comparison_list",Integer.class));
        service.saveList(316L,request(11L,21L)); assertTrue(service.getList(317L).skuIds().isEmpty());
        TenantContextHolder.setTenantId(2L); assertTrue(service.getList(316L).skuIds().isEmpty());
    }
    @Test void quantityAndPreferencePersistAcrossClientsAndClearUsesVersion() {
        var req=request(11L,21L); req.setQuantity(12);req.setPreference("BALANCED");
        var saved=service.saveList(316L,req); assertEquals(1,saved.version());
        assertEquals(saved,service.getList(316L));
        var clear=request();clear.setVersion(1L);var empty=service.saveList(316L,clear);
        assertEquals(2,empty.version());assertTrue(empty.skuIds().isEmpty());
    }
    @Test void staleOrMissingVersionNeverOverwrites() {
        var req=request(11L,21L); service.saveList(316L,req);
        assertEquals(409,assertThrows(ServiceException.class,()->service.saveList(316L,req)).getCode());
        req.setVersion(3L);assertEquals(409,assertThrows(ServiceException.class,()->service.saveList(316L,req)).getCode());
        req.setVersion(null);assertEquals(400,assertThrows(ServiceException.class,()->service.saveList(316L,req)).getCode());
        assertEquals(1,service.getList(316L).version());
    }
    @Test void invalidTenantAndAnonymousPrivateListFailClosed() {
        TenantContextHolder.clear(); assertThrows(ServiceException.class,()->service.calculate(request(11L)));
        TenantContextHolder.setTenantId(1L);TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class,()->service.calculate(request(11L)));
        TenantContextHolder.setIgnore(false);assertThrows(ServiceException.class,()->service.getList(null));
    }
    @Test void invalidAndDuplicateSelectionsRejectBeforeWrite() {
        for (var ids : List.of(List.of(11L,11L),List.of(-1L),List.of(1L,2L,3L,4L,5L))) {
            var req=request();req.setSkuIds(ids);assertThrows(ServiceException.class,()->service.saveList(316L,req));
        }
        var req=request(11L);req.setQuantity(1000);assertThrows(ServiceException.class,()->service.calculate(req));
        req.setQuantity(1);req.setPreference("CHEAPEST_ANY_MATERIAL");assertThrows(ServiceException.class,()->service.calculate(req));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product_comparison_list",Integer.class));
    }
    @Test void guestMergeAndHiddenSkuCannotBypassVisibility() {
        assertThrows(ServiceException.class,()->service.saveList(316L,request(11L,41L)));
        assertEquals(0,service.getList(316L).version());
    }
    @Test void savedHiddenSelectionIsNotSilentlyDeletedButCalculateRedacts() {
        service.saveList(316L,request(11L,21L));jdbc.update("UPDATE product_spu SET status=0 WHERE id=10");
        assertEquals(List.of(11L,21L),service.getList(316L).skuIds());
        assertFalse(service.calculate(request(11L,21L)).getItems().getFirst().isAvailable());
        var remove=request(21L);remove.setVersion(1L);assertEquals(List.of(21L),service.saveList(316L,remove).skuIds());
    }
    @Test void multipleHiddenSelectionsCanBeRemovedOneAtATime() {
        service.saveList(316L,request(11L,21L,31L));jdbc.update("UPDATE product_spu SET status=0 WHERE id IN(10,20)");
        var remove=request(21L,31L);remove.setVersion(1L);assertEquals(List.of(21L,31L),service.saveList(316L,remove).skuIds());
        assertFalse(service.calculate(remove).getItems().getFirst().isAvailable());
        var removeSecond=request(31L);removeSecond.setVersion(2L);assertEquals(List.of(31L),service.saveList(316L,removeSecond).skuIds());
    }
    @Test void retainedHiddenEntryDoesNotAllowNewHiddenGuestMerge() {
        service.saveList(316L,request(11L,21L));jdbc.update("UPDATE product_spu SET status=0 WHERE id IN(10,30)");
        var merge=request(11L,21L,31L);merge.setVersion(1L);
        assertEquals(400,assertThrows(ServiceException.class,()->service.saveList(316L,merge)).getCode());
        assertEquals(List.of(11L,21L),service.getList(316L).skuIds());assertEquals(1,service.getList(316L).version());
    }
    @Test void firstInsertRaceHasExactlyOneWinner() throws Exception { race(0); }
    @Test void updateRaceHasExactlyOneWinner() throws Exception {
        repository.save(1L,316L,new ComparisonListRepository.Selection(0,List.of(11L),1,"PRICE"));race(1);
    }
    void race(long version) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CyclicBarrier(2);var tasks=new ArrayList<Future<Boolean>>();
            for(long sku:List.of(11L,21L)) tasks.add(executor.submit(()->{
                gate.await();try{repository.save(1L,316L,new ComparisonListRepository.Selection(version,List.of(sku),2,"PRICE"));return true;}
                catch(ServiceException error){assertEquals(409,error.getCode());return false;}
            }));
            assertEquals(1,tasks.stream().map(f->{try{return f.get(15,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).filter(Boolean::booleanValue).count());
            assertEquals(version+1,repository.get(1L,316L).version());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM product_comparison_list",Integer.class));
        }
    }
    @Test void corruptStoredIdsDoNotBecomeSilentEmptySelection() {
        service.saveList(316L,request(11L));jdbc.update("UPDATE product_comparison_list SET sku_ids='11,secret' WHERE member_id=316");
        assertEquals(503,assertThrows(ServiceException.class,()->service.getList(316L)).getCode());
    }
    @Test void corruptStoredQuantityDoesNotBecomeTrustedPurchaseInput() {
        service.saveList(316L,request(11L));jdbc.update("UPDATE product_comparison_list SET quantity=0 WHERE member_id=316");
        assertEquals(503,assertThrows(ServiceException.class,()->service.getList(316L)).getCode());
    }
    @Test void controllerRequiresMemberNotAdminForPrivateList() {
        var controller=new AppProductComparisonController();ReflectionTestUtils.setField(controller,"service",service);
        assertThrows(ServiceException.class,controller::list);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2),new org.springframework.mock.web.MockHttpServletRequest());
        assertThrows(ServiceException.class,controller::list);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(316L).setUserType(1),new org.springframework.mock.web.MockHttpServletRequest());
        assertEquals(0,controller.list().getCheckedData().version());
    }
}
