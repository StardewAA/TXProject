package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** 业务事务末尾只写变更日志；顺序为业务锁 -> 租户时钟 -> 提交，绝不反向加业务锁或RPC。 */
@Service
public class OrderWorkbenchJournal {
    private final JdbcTemplate jdbc;
    public OrderWorkbenchJournal(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void changed(Long orderId) {
        changed(orderId,new OrderWorkbenchModels.Action[0]);
    }
    public void changed(Long orderId,OrderWorkbenchModels.Action... invalidated) {
        if (orderId==null) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("订单待办变更必须与业务使用同一事务");
        long tenant=TenantContextHolder.getRequiredTenantId();
        Pending pending=TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(Pending.class::isInstance).map(Pending.class::cast)
                .filter(p->p.owner==this && p.tenant==tenant).findFirst().orElse(null);
        if (pending==null) {
            pending=new Pending(tenant); TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.orderIds.add(orderId);
        pending.invalidated.computeIfAbsent(orderId,k->EnumSet.noneOf(OrderWorkbenchModels.Action.class)).addAll(Arrays.asList(invalidated));
    }
    private final class Pending implements TransactionSynchronization {
        final OrderWorkbenchJournal owner=OrderWorkbenchJournal.this;
        final long tenant; final SortedSet<Long> orderIds=new TreeSet<>();
        final Map<Long,Set<OrderWorkbenchModels.Action>> invalidated=new HashMap<>();
        Pending(long tenant) { this.tenant=tenant; }
        @Override public void beforeCommit(boolean readOnly) {
            if (readOnly) throw new IllegalStateException("只读事务不能记录订单业务变更");
            long sequence=lock(tenant);
            for (long id:orderIds) jdbc.update("INSERT INTO trade_order_workbench_change(tenant_id,sequence_no,order_id,invalidate_actions) VALUES(?,?,?,?)",
                    tenant,++sequence,id,"|"+String.join("|",invalidated.get(id).stream().map(Enum::name).toList())+"|");
            saveClock(tenant,sequence);
        }
    }
    /** 必须在事务中调用；行锁一直保留到提交，使序号顺序不跨越尚未提交的事务。 */
    long lock(long tenant) {
        jdbc.update("INSERT INTO trade_order_workbench_clock(tenant_id,sequence_no) VALUES(?,0) ON DUPLICATE KEY UPDATE tenant_id=tenant_id",tenant);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT sequence_no FROM trade_order_workbench_clock WHERE tenant_id=? FOR UPDATE",Long.class,tenant));
    }
    void saveClock(long tenant,long sequence) {
        jdbc.update("UPDATE trade_order_workbench_clock SET sequence_no=? WHERE tenant_id=?",sequence,tenant);
    }
}
