package cn.iocoder.txgy.module.trade.service.settlement;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import cn.iocoder.txgy.module.trade.service.supplier.AdminShopScopeService;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import static cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.*;
import static cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService.invalid;

/** All SQL is explicitly tenant-scoped: JDBC does not run the MyBatis tenant interceptor. */
@Service
public class ShopSettlementService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ProductSupplierApi authorization;
    @Resource private ProductShopApi shops;
    @Resource private AdminShopScopeService adminShopScope;
    private static final BeanPropertyRowMapper<Bill> BILLS=new BeanPropertyRowMapper<>(Bill.class);
    private static final BeanPropertyRowMapper<Item> ITEMS=new BeanPropertyRowMapper<>(Item.class);
    private long tenant(){return TenantContextHolder.getRequiredTenantId();}
    private long actor(){var u=SecurityFrameworkUtils.getLoginUser();if(u==null)throw invalid("请先登录");return u.getId();}
    private void admin(){var u=SecurityFrameworkUtils.getLoginUser();if(u==null || !Objects.equals(u.getUserType(),UserTypeEnum.ADMIN.getValue()))throw invalid("请使用平台管理员账号");}
    private void member(Long shop){
        var u=SecurityFrameworkUtils.getLoginUser();
        if(u==null || !Objects.equals(u.getUserType(),UserTypeEnum.MEMBER.getValue()) || shop==null
                || !Boolean.TRUE.equals(authorization.validateShop(u.getId(),shop).getCheckedData()))throw invalid("无该店铺结算权限");
    }
    private Bill bill(Long id){
        var list=jdbc.query("SELECT * FROM trade_shop_settlement_bill WHERE tenant_id=? AND id=?",BILLS,tenant(),id);
        if(list.isEmpty())throw invalid("账单不存在或无权访问");return list.getFirst();
    }
    private long lock(Long shop){
        jdbc.update("INSERT IGNORE INTO trade_shop_settlement_lock(tenant_id,shop_id) VALUES(?,?)",tenant(),shop);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT credit_amount FROM trade_shop_settlement_lock WHERE tenant_id=? AND shop_id=? FOR UPDATE",Long.class,tenant(),shop));
    }
    private Bill locked(Long id,int version){
        Bill before=bill(id);lock(before.getShopId());
        // A normal repeatable-read SELECT may retain the snapshot from before waiting for the shop lock.
        // Use a current read so concurrent actions see the committed status/version, not the old bill.
        var rows=jdbc.query("SELECT * FROM trade_shop_settlement_bill WHERE tenant_id=? AND id=? FOR UPDATE",BILLS,tenant(),id);
        if(rows.isEmpty())throw invalid("账单不存在或无权访问");Bill b=rows.getFirst();
        if(b.getVersion()!=version)throw invalid("账单已重新核算，请刷新核对最新版本");return b;
    }
    private List<Item> items(Long id){return jdbc.query("SELECT * FROM trade_shop_settlement_item WHERE tenant_id=? AND bill_id=? ORDER BY id",ITEMS,tenant(),id);}
    private void log(Bill b,String action,String remark){
        jdbc.update("INSERT INTO trade_shop_settlement_log(tenant_id,bill_id,version,action,actor_id,actor_type,remark,snapshot_json) VALUES(?,?,?,?,?,?,?,?)",
            tenant(),b.getId(),b.getVersion(),action,actor(),SecurityFrameworkUtils.getLoginUser().getUserType(),remark,
            cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(Map.of("bill",b,"items",items(b.getId()))));
    }
    public PageResult<Bill> page(Long shop,PageParam page,Integer status,boolean supplier){
        if(supplier)member(shop);else admin();
        return pageScoped(shop,page,status);
    }
    public PageResult<Bill> adminSupplierPage(Long shop,PageParam page,Integer status){
        adminShopScope.requireShop(shop);
        return pageScoped(shop,page,status);
    }
    private PageResult<Bill> pageScoped(Long shop,PageParam page,Integer status){
        String where=" WHERE tenant_id=?";var args=new ArrayList<Object>();args.add(tenant());
        if(shop!=null){where+=" AND shop_id=?";args.add(shop);}if(status!=null){where+=" AND status=?";args.add(status);}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill"+where,Long.class,args.toArray()));
        int size=Math.max(1,Math.min(page.getPageSize(),100)),num=Math.max(1,page.getPageNo());args.add(size);args.add((long)(num-1)*size);
        return new PageResult<>(jdbc.query("SELECT * FROM trade_shop_settlement_bill"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",BILLS,args.toArray()),total);
    }
    public Map<String,Object> detail(Long id,Long shop,boolean supplier){
        if(supplier)member(shop);else admin();
        return detailScoped(id,shop,supplier);
    }
    public Map<String,Object> adminSupplierDetail(Long id,Long shop){
        adminShopScope.requireShop(shop);
        return detailScoped(id,shop,true);
    }
    private Map<String,Object> detailScoped(Long id,Long shop,boolean scoped){
        var b=bill(id);if(scoped && !Objects.equals(shop,b.getShopId()))throw invalid("无该账单权限");
        return Map.of("bill",b,"items",items(id),"logs",jdbc.queryForList("SELECT version,action,actor_id AS actorId,actor_type AS actorType,remark,create_time AS createTime FROM trade_shop_settlement_log WHERE tenant_id=? AND bill_id=? ORDER BY id",tenant(),id));
    }
    public String proof(Long id,Long shop,boolean supplier){detail(id,shop,supplier);return jdbc.queryForObject("SELECT payment_proof FROM trade_shop_settlement_bill WHERE tenant_id=? AND id=?",String.class,tenant(),id);}
    public String adminSupplierProof(Long id,Long shop){adminSupplierDetail(id,shop);return jdbc.queryForObject("SELECT payment_proof FROM trade_shop_settlement_bill WHERE tenant_id=? AND id=?",String.class,tenant(),id);}

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public Bill generate(Generate req){
        admin();var month=SettlementMath.month(req.month());long carry=lock(req.shopId());
        var shop=shops.getShop(req.shopId()).getCheckedData();if(shop==null)throw invalid("店铺不存在");
        var existing=jdbc.query("SELECT * FROM trade_shop_settlement_bill WHERE tenant_id=? AND shop_id=? AND bill_month=?",BILLS,tenant(),req.shopId(),req.month());
        if(!existing.isEmpty() && !List.of(10,50).contains(existing.getFirst().getStatus()))throw invalid("该月账单已进入确认流程，不可重复生成");
        Long id=existing.isEmpty()?null:existing.getFirst().getId();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill WHERE tenant_id=? AND shop_id=? AND status NOT IN(40,60) AND id<>?",Long.class,tenant(),req.shopId(),id==null?0:id)>0)throw invalid("请先完成该店铺上一张账单，避免重复抵扣");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill WHERE tenant_id=? AND shop_id=? AND bill_month>?",Long.class,tenant(),req.shopId(),req.month())>0)throw invalid("请按月份顺序结算，不能回填较早月份");
        if(id==null){
            jdbc.update("INSERT INTO trade_shop_settlement_bill(tenant_id,shop_id,shop_name,bill_month,no,commission_rate,carry_amount) VALUES(?,?,?,?,?,?,?)",tenant(),req.shopId(),shop.getName(),req.month(),"SET-"+req.shopId()+"-"+req.month(),req.commissionRate(),carry);
            id=jdbc.queryForObject("SELECT id FROM trade_shop_settlement_bill WHERE tenant_id=? AND shop_id=? AND bill_month=?",Long.class,tenant(),req.shopId(),req.month());
        }else{
            // Only a draft/rejected version can be recalculated; old totals are recorded before replacing its lines.
            Bill old=existing.getFirst();log(old,"RECALCULATE","旧版应结(分):"+old.getSettlementAmount()+"；订单数:"+old.getOrderCount());
            jdbc.update("DELETE FROM trade_shop_settlement_item WHERE tenant_id=? AND bill_id=?",tenant(),id);
            jdbc.update("UPDATE trade_shop_settlement_bill SET version=version+1,status=10,commission_rate=?,carry_amount=?,audit_remark=NULL,update_time=NOW() WHERE tenant_id=? AND id=?",req.commissionRate(),carry,tenant(),id);
        }
        var end=month.plusMonths(1).atDay(1).atStartOfDay();
        // Receipt determines the settlement month. trade_order.finish_time is populated only after
        // all items are reviewed, so it must not gate received-but-unreviewed orders or shift months.
        // Keep the existing snapshot column name finish_time, but snapshot the actual receipt time.
        var orders=jdbc.queryForList("SELECT o.id,o.no,o.pay_price,o.delivery_price,o.refund_price,o.receive_time AS finish_time FROM trade_order o WHERE o.tenant_id=? AND o.shop_id=? AND o.deleted=0 AND o.status=30 AND o.pay_status=1 AND o.receive_time<? AND NOT EXISTS(SELECT 1 FROM trade_shop_settlement_item s WHERE s.tenant_id=o.tenant_id AND s.original_order_id=o.id) ORDER BY o.id LIMIT 5001 FOR UPDATE",tenant(),req.shopId(),end);
        if(orders.size()>5000)throw invalid("单月待结订单超过5000条，请联系平台分批核算支持");
        long pay=0,refund=0,commission=0,adjust=0;int count=0;
        for(var order:orders){
            long orderId=n(order,"id");if(!eligible(orderId,req.shopId()))continue;
            long p=n(order,"pay_price"),r=n(order,"refund_price"),c=SettlementMath.commission(p,r,req.commissionRate());
            add(id,orderId,Objects.toString(order.get("no")),1,orderId,null,p,n(order,"delivery_price"),0,r,req.commissionRate(),c,p-r-c,order.get("finish_time"));
            pay=Math.addExact(pay,p);refund=Math.addExact(refund,r);commission=Math.addExact(commission,c);count++;
        }
        var originals=jdbc.query("SELECT i.* FROM trade_shop_settlement_item i JOIN trade_shop_settlement_bill b ON b.id=i.bill_id AND b.tenant_id=i.tenant_id WHERE i.tenant_id=? AND b.shop_id=? AND i.kind=1 AND b.status IN(40,60) ORDER BY i.order_id",ITEMS,tenant(),req.shopId());
        if(originals.size()>100000)throw invalid("历史结算订单较多，请联系平台启用分批核算");
        for(var origin:originals){
            // A buyer can hide a canceled, fully refunded order via logical deletion. Its retained
            // financial row is still required for an already-settled order's refund adjustment.
            var order=jdbc.queryForList("SELECT pay_price,refund_price FROM trade_order WHERE tenant_id=? AND shop_id=? AND id=? FOR UPDATE",tenant(),req.shopId(),origin.getOrderId());
            if(order.isEmpty())throw invalid("历史已结订单缺失，不能静默忽略退款调整");
            long r=n(order.getFirst(),"refund_price");
            long previous=Objects.requireNonNull(jdbc.queryForObject("SELECT MAX(i.refund_total) FROM trade_shop_settlement_item i JOIN trade_shop_settlement_bill b ON b.id=i.bill_id AND b.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.order_id=? AND b.status IN(40,60)",Long.class,tenant(),origin.getOrderId()));
            if(r<previous)throw invalid("历史累计退款倒退，请核对原单");if(r==previous)continue;
            if(n(order.getFirst(),"pay_price")!=origin.getPayAmount())throw invalid("已结订单实付金额发生变化，请核对");
            long a=SettlementMath.adjustment(origin.getPayAmount(),previous,r,origin.getCommissionRate());
            long c=SettlementMath.commission(origin.getPayAmount(),r,origin.getCommissionRate())-SettlementMath.commission(origin.getPayAmount(),previous,origin.getCommissionRate());
            add(id,origin.getOrderId(),origin.getOrderNo(),2,null,origin.getBillId(),origin.getPayAmount(),origin.getDeliveryAmount(),previous,r,origin.getCommissionRate(),c,a,origin.getFinishTime());adjust=Math.addExact(adjust,a);
        }
        if(items(id).isEmpty() && carry==0)throw invalid("没有符合条件的订单或退款调整，不生成空账单");
        long amount=Math.subtractExact(Math.addExact(pay-refund-commission,adjust),carry);
        if(Math.abs(amount)>9_000_000_000_000L)throw invalid("账单金额超出安全范围");
        jdbc.update("UPDATE trade_shop_settlement_bill SET order_count=?,order_pay_amount=?,refund_amount=?,commission_amount=?,adjustment_amount=?,settlement_amount=?,update_time=NOW() WHERE tenant_id=? AND id=?",count,pay,refund,commission,adjust,amount,tenant(),id);
        Bill b=bill(id);log(b,"GENERATE","生成自然月账单；应结(分):"+amount+"；实际打款未执行");return b;
    }
    private static long n(Map<String,Object> map,String key){Object v=map.get(key);return v==null?0:((Number)v).longValue();}
    private boolean eligible(long order,Long shop){
        // The parent order remains locked, including while these checks run. Every after-sale
        // creation takes that parent lock before insertion, so no new application can slip in.
        var lines=jdbc.queryForList("SELECT shop_id FROM trade_order_item WHERE tenant_id=? AND order_id=? AND deleted=0 FOR UPDATE",tenant(),order);
        // Do not lock after-sales: refund completion already holds an after-sale lock before
        // requesting the order lock. At READ_COMMITTED an uncommitted closure is still active
        // (fail closed); a creation committed while waiting for the order lock is visible.
        var active=jdbc.queryForList("SELECT id FROM trade_after_sale WHERE tenant_id=? AND order_id=? AND deleted=0 AND status NOT IN(50,61,62,63)",tenant(),order);
        return !lines.isEmpty() && lines.stream().allMatch(line->line.get("shop_id") instanceof Number owner && owner.longValue()==shop) && active.isEmpty();
    }
    private void add(Long bill,long order,String no,int kind,Long original,Long originalBill,long pay,long delivery,long before,long refund,int rate,long commission,long amount,Object finish){
        jdbc.update("INSERT INTO trade_shop_settlement_item(tenant_id,bill_id,order_id,order_no,kind,original_order_id,original_bill_id,pay_amount,delivery_amount,refund_before,refund_total,commission_rate,commission_amount,amount,finish_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",tenant(),bill,order,no,kind,original,originalBill,pay,delivery,before,refund,rate,commission,amount,finish);
    }
    private void verify(Bill b){
        for(Item i:items(b.getId())){
            // Only historical adjustment lines may read a logically deleted original. New sale
            // lines still require a visible completed order; tenant and shop restrictions stay intact.
            var order=jdbc.queryForList("SELECT pay_price,refund_price,status FROM trade_order WHERE tenant_id=? AND shop_id=? AND id=? AND (deleted=0 OR ?=2) FOR UPDATE",tenant(),b.getShopId(),i.getOrderId(),i.getKind());
            if(order.isEmpty() || n(order.getFirst(),"pay_price")!=i.getPayAmount() || n(order.getFirst(),"refund_price")!=i.getRefundTotal()
                || (i.getKind()==1 && n(order.getFirst(),"status")!=30) || !eligible(i.getOrderId(),b.getShopId()))throw invalid("原订单退款或售后状态已变化，请平台退回并重新核算账单");
        }
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void audit(Audit req){
        admin();Bill b=locked(req.id(),req.version());
        if(req.approved()){SettlementMath.transition(b.getStatus(),10);verify(b);}
        else if(!List.of(10,20,30).contains(b.getStatus()))throw invalid("该账单不能退回");
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=?,audit_remark=?,update_time=NOW() WHERE tenant_id=? AND id=?",req.approved()?20:50,req.remark().trim(),tenant(),b.getId());
        log(b,req.approved()?"AUDIT":"REJECT",req.remark().trim());
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void confirm(Confirm req){
        member(req.shopId());confirmScoped(req);
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void adminSupplierConfirm(Confirm req){
        adminShopScope.requireShop(req.shopId());confirmScoped(req);
    }
    private void confirmScoped(Confirm req){
        Bill b=locked(req.id(),req.version());if(!Objects.equals(req.shopId(),b.getShopId()))throw invalid("无该账单权限");
        if(b.getStatus()==30)return;SettlementMath.transition(b.getStatus(),20);verify(b);
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=30,update_time=NOW() WHERE tenant_id=? AND id=?",tenant(),b.getId());log(b,"SHOP_CONFIRM","店铺确认账单，非付款操作");
    }
    public static void validateProof(String value){
        if(value==null || value.length()>2800000 || !(value.startsWith("data:image/png;base64,")||value.startsWith("data:image/jpeg;base64,")))throw invalid("付款凭证须为2MB内PNG/JPEG图片");
        try{
            byte[] bytes=Base64.getDecoder().decode(value.substring(value.indexOf(',')+1));if(bytes.length>2097152)throw new IllegalArgumentException();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException();var reader=readers.next();
                try{reader.setInput(input);String format=reader.getFormatName();if(!(format.equalsIgnoreCase("PNG")||format.equalsIgnoreCase("JPEG"))||reader.getWidth(0)>6000||reader.getHeight(0)>6000||(long)reader.getWidth(0)*reader.getHeight(0)>16000000||reader.read(0)==null)throw new IllegalArgumentException();}finally{reader.dispose();}
            }
        }catch(Exception e){throw invalid("付款凭证不是有效的受支持图片");}
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void pay(Pay req){
        admin();Bill b=locked(req.id(),req.version());
        if(b.getStatus()==40 && Objects.equals(b.getPaymentReference(),req.reference().trim()) && Objects.equals(b.getSettlementAmount(),req.amount()))return;
        SettlementMath.transition(b.getStatus(),30);verify(b);
        if(b.getSettlementAmount()<=0 || !Objects.equals(b.getSettlementAmount(),req.amount()))throw invalid("登记付款金额必须等于正数应结金额");
        if(req.paidAt().isAfter(LocalDateTime.now()) || req.paidAt().isBefore(b.getCreateTime()))throw invalid("付款时间须在账单生成后且不晚于当前时间");
        validateProof(req.proof());
        if(req.reference().trim().isEmpty())throw invalid("请填写付款流水号");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM trade_shop_settlement_bill WHERE tenant_id=? AND payment_reference=?",Long.class,tenant(),req.reference().trim())>0)throw invalid("付款流水号已登记，不能重复使用");
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=40,payment_reference=?,payment_time=?,payment_proof=?,update_time=NOW() WHERE tenant_id=? AND id=?",req.reference().trim(),req.paidAt(),req.proof(),tenant(),b.getId());
        jdbc.update("UPDATE trade_shop_settlement_lock SET credit_amount=0 WHERE tenant_id=? AND shop_id=?",tenant(),b.getShopId());log(b,"OFFLINE_PAID","登记线下付款；流水:"+req.reference().trim()+"；金额(分):"+req.amount());
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public void carry(Long id,int version){
        admin();Bill b=locked(id,version);if(b.getStatus()==60)return;SettlementMath.transition(b.getStatus(),30);verify(b);
        if(b.getSettlementAmount()>0)throw invalid("正数账单须登记实际线下付款");
        jdbc.update("UPDATE trade_shop_settlement_lock SET credit_amount=? WHERE tenant_id=? AND shop_id=?",-b.getSettlementAmount(),tenant(),b.getShopId());
        jdbc.update("UPDATE trade_shop_settlement_bill SET status=60,update_time=NOW() WHERE tenant_id=? AND id=?",tenant(),id);log(b,"CARRY","无需付款；下期抵扣(分):"+(-b.getSettlementAmount()));
    }
}
