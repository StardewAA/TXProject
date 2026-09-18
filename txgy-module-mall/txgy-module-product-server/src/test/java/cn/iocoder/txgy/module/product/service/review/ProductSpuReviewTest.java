package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.review.ProductSpuReviewRecordDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.review.ProductSpuReviewRecordMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import cn.iocoder.txgy.module.product.service.category.ProductCategoryService;
import cn.iocoder.txgy.module.product.service.brand.ProductBrandService;
import cn.iocoder.txgy.module.trade.api.delivery.DeliveryTemplateApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 正式 Mapper + 租户拦截 + SPU 行锁 + Spring 事务，在独立 H2 验证，不访问业务数据库。 */
class ProductSpuReviewTest {
    JdbcTemplate jdbc;
    ProductSpuMapper spus;
    ProductSkuMapper skus;
    ProductSpuReviewRecordMapper records;
    ProductShopService shops;
    ProductSpuReviewService service;
    ProductReviewProperties policy;
    TransactionTemplate tx;
    DataSourceTransactionManager transactionManager;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:spu-review-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
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
        jdbc.execute("CREATE TABLE product_spu_review_record(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,spu_id BIGINT,shop_id BIGINT,"
                + "content_version BIGINT,submission_id VARCHAR(64),action VARCHAR(32),actor_type INT,actor_id BIGINT,remark VARCHAR(1000),snapshot CLOB,"
                + "create_time TIMESTAMP,UNIQUE(tenant_id,spu_id,submission_id,action))");
        jdbc.execute("CREATE TABLE product_shop(id BIGINT PRIMARY KEY,enterprise_id BIGINT,status INT," + audit + ")");
        jdbc.execute("CREATE TABLE product_enterprise(id BIGINT PRIMARY KEY,audit_status INT," + audit + ")");
        jdbc.update("INSERT INTO product_spu(id,shop_id,name,description,pic_url,delivery_types,delivery_template_id,status,review_status,content_version,stock,sales_count,tenant_id) "
                + "VALUES(10,100,'商品','详情','https://example.com/p.png','1',1,0,0,1,9,5,1),(20,200,'别店','详情','https://example.com/p.png','1',1,0,0,1,9,5,2)");
        jdbc.update("INSERT INTO product_sku(id,spu_id,price,stock,sales_count,wms_sku_id,wms_warehouse_id,tenant_id) VALUES(11,10,123,9,5,999,888,1),(21,20,123,9,5,999,888,2)");
        jdbc.update("INSERT INTO product_shop(id,enterprise_id,status,tenant_id) VALUES(100,1000,0,1),(200,2000,0,2)");
        jdbc.update("INSERT INTO product_enterprise(id,audit_status,tenant_id) VALUES(1000,1,1),(2000,1,2)");
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProductSpuMapper.class); configuration.addMapper(ProductSkuMapper.class); configuration.addMapper(ProductSpuReviewRecordMapper.class);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration); factory.setPlugins(interceptor);
        var sessions = new SqlSessionTemplate(factory.getObject());
        spus = sessions.getMapper(ProductSpuMapper.class); skus = sessions.getMapper(ProductSkuMapper.class);
        records = spy(sessions.getMapper(ProductSpuReviewRecordMapper.class));
        shops = mock(ProductShopService.class);
        when(shops.getShop(100L)).thenReturn(new ProductShopDO().setId(100L).setEnterpriseId(1000L).setStatus(0));
        when(shops.getShop(200L)).thenReturn(new ProductShopDO().setId(200L).setEnterpriseId(2000L).setStatus(0));
        var enterprises = mock(ProductEnterpriseMapper.class);
        when(enterprises.selectById(1000L)).thenReturn(new ProductEnterpriseDO().setId(1000L).setAuditStatus(1));
        when(enterprises.selectById(2000L)).thenReturn(new ProductEnterpriseDO().setId(2000L).setAuditStatus(1));
        var delivery = mock(DeliveryTemplateApi.class);
        when(delivery.list()).thenReturn(CommonResult.success(List.of(new DeliveryTemplateApi.Template(1L,"按件",1))));
        var target = new ProductSpuReviewService(); policy = new ProductReviewProperties();
        ReflectionTestUtils.setField(target,"spuMapper",spus); ReflectionTestUtils.setField(target,"skuMapper",skus);
        ReflectionTestUtils.setField(target,"recordMapper",records); ReflectionTestUtils.setField(target,"shopService",shops);
        ReflectionTestUtils.setField(target,"membership",mock(EnterpriseMembershipService.class)); ReflectionTestUtils.setField(target,"properties",policy);
        ReflectionTestUtils.setField(target,"enterpriseMapper",enterprises); ReflectionTestUtils.setField(target,"deliveryApi",delivery);
        ReflectionTestUtils.setField(target,"categoryService",mock(ProductCategoryService.class)); ReflectionTestUtils.setField(target,"brandService",mock(ProductBrandService.class));
        var manager = new DataSourceTransactionManager(source); transactionManager=manager; tx = new TransactionTemplate(manager);
        var proxy = new ProxyFactory(target); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service = (ProductSpuReviewService) proxy.getProxy(); login(1,301);
    }
    void login(int type,long id) {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(id).setUserType(type),new org.springframework.mock.web.MockHttpServletRequest());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); jdbc.execute("DROP ALL OBJECTS"); }
    ProductSpuDO product() { return spus.selectById(10L); }
    String submit() { service.submit(301L,100L,10L,1L); return product().getSubmissionId(); }
    long count(String action) { return jdbc.queryForObject("SELECT COUNT(*) FROM product_spu_review_record WHERE action=?",Long.class,action); }

    @Test void submittedApprovedVersionRemainsOffUntilMerchantListsIt() {
        String id = submit(); login(2,801); service.decide(10L,1L,id,true,"符合要求");
        assertEquals(20,product().getReviewStatus()); assertEquals(1L,product().getApprovedVersion()); assertEquals(0,product().getStatus());
        assertFalse(service.saleable(product())); assertDoesNotThrow(() -> service.requireApprovedForListing(product()));
        var snapshot = service.history(10L).getFirst().getSnapshot();
        assertFalse(snapshot.contains("wmsSkuId")); assertFalse(snapshot.contains("stock")); assertFalse(snapshot.contains("salesCount"));
        assertEquals(9,skus.selectById(11L).getStock()); assertEquals(999L,skus.selectById(11L).getWmsSkuId());
    }
    @Test void sameDecisionRetriesCreateExactlyOneAudit() {
        String id=submit(); login(2,801); service.decide(10L,1L,id,true,""); service.decide(10L,1L,id,true,"");
        assertEquals(1,count("SUBMIT")); assertEquals(1,count("APPROVE"));
        assertThrows(ServiceException.class,()->service.decide(10L,1L,id,false,"不同决策"));
    }
    @Test void withdrawAndResubmitSameContentInvalidatesOldSubmission() {
        String old=submit(); service.withdraw(301L,100L,10L,1L,old); service.withdraw(301L,100L,10L,1L,old);
        String current=submit(); assertNotEquals(old,current); assertEquals(1L,product().getContentVersion());
        login(2,801); assertThrows(ServiceException.class,()->service.decide(10L,1L,old,true,""));
        service.decide(10L,1L,current,true,""); assertEquals(2,count("SUBMIT")); assertEquals(1,count("WITHDRAW"));
    }
    @Test void missingAndStaleVersionsCannotSubmitOrEdit() {
        assertThrows(ServiceException.class,()->service.submit(301L,100L,10L,null));
        assertThrows(ServiceException.class,()->service.submit(301L,100L,10L,0L));
        String id=submit(); login(2,801); assertThrows(ServiceException.class,()->service.decide(10L,2L,id,true,""));
        assertEquals(10,product().getReviewStatus()); assertEquals(0,count("APPROVE"));
    }
    @Test void pendingContentFrozenAndTamperedSnapshotCannotBeApproved() {
        String id=submit();
        assertThrows(ServiceException.class,()->tx.execute(s->service.beforeEdit(service.lock(10L),1L)));
        jdbc.update("UPDATE product_sku SET price=999 WHERE id=11");
        login(2,801); assertThrows(ServiceException.class,()->service.decide(10L,1L,id,true,""));
        assertEquals(10,product().getReviewStatus()); assertEquals(0,count("APPROVE"));
    }
    @Test void knownOperatingAccountCannotSelfReview() {
        login(2,801); service.adminSubmit(10L,1L); String id=product().getSubmissionId();
        assertThrows(ServiceException.class,()->service.decide(10L,1L,id,true,""));
        login(2,802); when(shops.getShop(100L)).thenReturn(new ProductShopDO().setId(100L).setEnterpriseId(1000L).setManagerUserId(802L).setStatus(0));
        assertThrows(ServiceException.class,()->service.decide(10L,1L,id,true,""));
    }
    @Test void rejectedVersionShowsReasonAndBlankRejectionHasNoMutation() {
        String id=submit(); login(2,801); assertThrows(ServiceException.class,()->service.decide(10L,1L,id,false,"  "));
        service.decide(10L,1L,id,false," 请完善材质 ");
        assertEquals(30,product().getReviewStatus()); assertEquals("请完善材质",product().getReviewRemark()); assertNull(product().getApprovedVersion());
    }
    @Test void auditFailureRollsBackApprovalAndVersionState() {
        String id=submit(); login(2,801);
        doThrow(new IllegalStateException("audit down")).when(records).insert(any(ProductSpuReviewRecordDO.class));
        assertThrows(IllegalStateException.class,()->service.decide(10L,1L,id,true,""));
        assertEquals(10,product().getReviewStatus()); assertNull(product().getApprovedVersion()); assertEquals(0,count("APPROVE"));
    }
    @Test void noOpOrSystemStockChangeDoesNotInventRevision() {
        tx.executeWithoutResult(s->{ var p=service.lock(10L); String before=service.beforeEdit(p,1L);
            skus.updateById(new ProductSkuDO().setId(11L).setStock(7).setSalesCount(7)); service.finishEdit(p,before); });
        assertEquals(1L,product().getContentVersion()); assertEquals(0,count("EDIT")); assertEquals(7,skus.selectById(11L).getStock());
    }
    @Test void actualEditTakesOnlineApprovedVersionOffAndKeepsSkuAndStock() {
        String id=submit(); login(2,801); service.decide(10L,1L,id,true,"");
        jdbc.update("UPDATE product_spu SET status=1 WHERE id=10"); login(1,301);
        tx.executeWithoutResult(s->{ var p=service.lock(10L); String before=service.beforeEdit(p,1L);
            skus.updateById(new ProductSkuDO().setId(11L).setPrice(124)); service.finishEdit(p,before); });
        assertEquals(2L,product().getContentVersion()); assertEquals(0,product().getReviewStatus()); assertEquals(0,product().getStatus());
        assertNull(product().getApprovedVersion()); assertNull(product().getSubmissionId()); assertEquals(1,count("EDIT"));
        assertEquals(9,skus.selectById(11L).getStock()); assertEquals(999L,skus.selectById(11L).getWmsSkuId());
        assertThrows(ServiceException.class,()->service.requireApprovedForListing(product()));
    }
    @Test void onlyExplicitUnmodifiedLegacyStrategyAllowsAlreadyOnlineLegacy() {
        jdbc.update("UPDATE product_spu SET status=1,review_status=NULL,content_version=0 WHERE id=10");
        assertTrue(service.saleable(product())); assertNull(product().getReviewStatus());
        assertThrows(ServiceException.class,()->service.requireApprovedForListing(product()));
        policy.setLegacyStrategy("UNCONFIGURED"); assertFalse(service.saleable(product()));
        policy.setLegacyStrategy("KEEP_EXISTING_UNMODIFIED"); jdbc.update("UPDATE product_spu SET status=0 WHERE id=10");
        assertFalse(service.saleable(product()));
    }
    @Test void tenantAndShopMismatchCannotReadOrReviewOtherScope() {
        assertThrows(ServiceException.class,()->service.submit(301L,200L,10L,1L));
        TenantContextHolder.setTenantId(2L); assertThrows(ServiceException.class,()->service.submit(301L,100L,10L,1L));
        assertTrue(service.history(10L).isEmpty()); assertEquals(0,count("SUBMIT"));
    }
    @Test void realPublicQueryFiltersDraftsDisabledShopsAndTenant() {
        var q=new LambdaQueryWrapperX<ProductSpuDO>(); service.applySaleableQuery(q); assertTrue(spus.selectList(q).isEmpty());
        jdbc.update("UPDATE product_spu SET status=1,review_status=NULL WHERE id=10");
        q=new LambdaQueryWrapperX<>(); service.applySaleableQuery(q); assertEquals(List.of(10L),spus.selectList(q).stream().map(ProductSpuDO::getId).toList());
        jdbc.update("UPDATE product_shop SET status=1 WHERE id=100");
        q=new LambdaQueryWrapperX<>(); service.applySaleableQuery(q); assertTrue(spus.selectList(q).isEmpty());
    }
    @Test void pilotAdminWritesAreClosedWithoutDisablingOtherShops() {
        policy.setAdminWriteLockedShopIds(List.of(100L));
        assertThrows(ServiceException.class,()->service.requireAdminWrite(100L));
        assertDoesNotThrow(()->service.requireAdminWrite(200L));
        login(2,801); assertThrows(ServiceException.class,()->service.adminSubmit(10L,1L)); assertEquals(0,count("SUBMIT"));
    }
    @Test void concurrentConflictingReviewsCommitOneDecision() throws Exception {
        String id=submit(); var ready=new CountDownLatch(2); var start=new CountDownLatch(1); var success=new AtomicInteger(); var conflicts=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<2;i++){final int n=i;tasks.add(pool.submit(()->{
                login(2,801+n); ready.countDown();
                try { assertTrue(start.await(10,TimeUnit.SECONDS)); service.decide(10L,1L,id,n==0,n==0?"通过":"驳回"); success.incrementAndGet(); }
                catch(ServiceException ex){conflicts.incrementAndGet();} catch(InterruptedException ex){Thread.currentThread().interrupt();throw new RuntimeException(ex);}
                finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}
            }));}
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();for(var task:tasks)task.get(20,TimeUnit.SECONDS);
        }
        assertEquals(1,success.get());assertEquals(1,conflicts.get()); assertEquals(1,count("APPROVE")+count("REJECT"));
    }
    cn.iocoder.txgy.module.product.service.spu.ProductSpuService deleteService(cn.iocoder.txgy.module.product.service.sku.ProductSkuService skuService) {
        var target=new cn.iocoder.txgy.module.product.service.spu.ProductSpuServiceImpl();
        ReflectionTestUtils.setField(target,"productSpuMapper",spus);ReflectionTestUtils.setField(target,"productSkuService",skuService);
        ReflectionTestUtils.setField(target,"reviewService",service);
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(transactionManager,new AnnotationTransactionAttributeSource()));
        return (cn.iocoder.txgy.module.product.service.spu.ProductSpuService)proxy.getProxy();
    }
    @Test void publicDeleteRejectsMissingStaleAndNegativeVersionUnderRealRowLock() {
        jdbc.update("UPDATE product_spu SET status=-1,content_version=2 WHERE id=10");login(2,801);
        var skuService=mock(cn.iocoder.txgy.module.product.service.sku.ProductSkuService.class);var productService=deleteService(skuService);
        assertThrows(ServiceException.class,()->productService.deleteSpu(10L,null));
        assertThrows(ServiceException.class,()->productService.deleteSpu(10L,1L));
        assertThrows(ServiceException.class,()->productService.deleteSpu(10L,-1L));
        assertNotNull(product());verifyNoInteractions(skuService);
    }
    @Test void publicPageIdsOfOnlyUnavailableProductReturnsZeroRatherThanOtherLiveProducts() {
        jdbc.update("INSERT INTO product_spu(id,shop_id,name,status,review_status,content_version,tenant_id) VALUES(30,100,'其他在售',1,NULL,0,1)");
        var req=new cn.iocoder.txgy.module.product.controller.app.spu.vo.AppProductSpuPageReqVO().setIds(List.of(10L));
        var page=spus.selectPage(req,Set.of(),service::applySaleableQuery);
        assertTrue(page.getList().isEmpty());assertEquals(0L,page.getTotal());
    }
    @Test void publicPageIdsReturnsOnlySelectedSaleableProductsAndPreservesTenantFilter() {
        jdbc.update("UPDATE product_spu SET status=1,review_status=NULL,content_version=0 WHERE id IN(10,20)");
        jdbc.update("INSERT INTO product_spu(id,shop_id,name,status,review_status,content_version,tenant_id) VALUES(30,100,'其他在售',1,NULL,0,1)");
        var req=new cn.iocoder.txgy.module.product.controller.app.spu.vo.AppProductSpuPageReqVO().setIds(List.of(10L,20L));
        var page=spus.selectPage(req,Set.of(),service::applySaleableQuery);
        assertEquals(List.of(10L),page.getList().stream().map(ProductSpuDO::getId).toList());assertEquals(1L,page.getTotal());
        req.setIds(List.of(30L));page=spus.selectPage(req,Set.of(),service::applySaleableQuery);
        assertEquals(List.of(30L),page.getList().stream().map(ProductSpuDO::getId).toList());assertEquals(1L,page.getTotal());
    }
    @Test void publicDeleteStillRequiresRecycledStatusAndRespectsPilotLock() {
        login(2,801);var skuService=mock(cn.iocoder.txgy.module.product.service.sku.ProductSkuService.class);var productService=deleteService(skuService);
        assertThrows(ServiceException.class,()->productService.deleteSpu(10L,1L));
        jdbc.update("UPDATE product_spu SET status=-1 WHERE id=10");policy.setAdminWriteLockedShopIds(List.of(100L));
        assertThrows(ServiceException.class,()->productService.deleteSpu(10L,1L));assertNotNull(product());verifyNoInteractions(skuService);
    }
    @Test void validVersionDeleteKeepsReviewHistoryAndRollsBackIfSkuCleanupFails() {
        String id=submit();service.withdraw(301L,100L,10L,1L,id);
        jdbc.update("UPDATE product_spu SET status=-1 WHERE id=10");login(2,801);
        var skuService=mock(cn.iocoder.txgy.module.product.service.sku.ProductSkuService.class);var productService=deleteService(skuService);
        doThrow(new IllegalStateException("sku cleanup fails")).when(skuService).deleteSkuBySpuId(10L);
        assertThrows(IllegalStateException.class,()->productService.deleteSpu(10L,1L));assertNotNull(product());
        doNothing().when(skuService).deleteSkuBySpuId(10L);productService.deleteSpu(10L,1L);
        assertNull(product());assertEquals(1,count("SUBMIT"));assertEquals(1,count("WITHDRAW"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM product_spu WHERE id=10 AND deleted=TRUE",Integer.class));
    }
}
