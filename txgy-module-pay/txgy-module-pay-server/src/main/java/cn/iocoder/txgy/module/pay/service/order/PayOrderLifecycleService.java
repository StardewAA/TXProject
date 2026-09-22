package cn.iocoder.txgy.module.pay.service.order;

import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderExtensionMapper;
import cn.iocoder.txgy.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderSubmitStateEnum;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.pay.enums.ErrorCodeConstants.*;

/**
 * 支付提交与关闭共用父单行锁。只处理数据库，严禁在持锁事务中调用渠道。
 * 独立事务保证支付尝试在外部调用前落盘，外部异常不会把尝试回滚成“从未支付”。
 */
@Service
public class PayOrderLifecycleService {

    @Resource private PayOrderMapper orderMapper;
    @Resource private PayOrderExtensionMapper orderExtensionMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void prepare(PayOrderDO expectedOrder, PayOrderExtensionDO extension) {
        PayOrderDO current = orderMapper.selectByIdForUpdate(expectedOrder.getId());
        checkIdentity(current, expectedOrder);
        if (PayOrderStatusEnum.isSuccess(current.getStatus())) throw exception(PAY_ORDER_STATUS_IS_SUCCESS);
        if (!PayOrderStatusEnum.isWaiting(current.getStatus())) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING);
        if (current.getExpireTime() == null || !current.getExpireTime().isAfter(LocalDateTime.now())) {
            throw exception(PAY_ORDER_IS_EXPIRED);
        }
        if (!samePaymentTerms(current, expectedOrder)) throw exception(PAY_ORDER_CHANGED);
        // 首次校验后可能已有另一提交创建尝试。必须在共用父单锁内 RC 重读，
        // 否则两个请求都可能在对方落盘前通过校验，随后各自发出不确定的付款请求。
        if (orderExtensionMapper.selectListByOrderId(current.getId()).stream()
                .anyMatch(attempt -> !PayOrderSubmitStateEnum.isDispatched(attempt.getSubmitState()))) {
            throw exception(PAY_ORDER_PREVIOUS_ATTEMPT_UNCONFIRMED);
        }
        if (extension.getId() != null || !Objects.equals(current.getId(), extension.getOrderId())
                || !PayOrderStatusEnum.isWaiting(extension.getStatus())) {
            throw new IllegalArgumentException("Invalid new payment attempt");
        }
        extension.setSubmitState(PayOrderSubmitStateEnum.PREPARED.getState());
        orderExtensionMapper.insert(extension);
    }

    /** 外部调用已退出之后才记录结果。记录失败时原 PREPARED 会阻止关闭，不能猜测成功。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void finishDispatch(PayOrderDO expectedOrder, Long extensionId, boolean responseReceived) {
        // 只推进本次 attempt 的标记，不更新父单。不能反向锁父单再更新已有 extension，
        // 否则会与“先 extension 后父单”的支付成功回调产生锁顺序死锁。
        PayOrderDO current = orderMapper.selectById(expectedOrder.getId());
        checkIdentity(current, expectedOrder);
        int state = (responseReceived ? PayOrderSubmitStateEnum.DISPATCHED : PayOrderSubmitStateEnum.UNKNOWN).getState();
        if (orderExtensionMapper.updateSubmitStateIfPrepared(extensionId, current.getId(), state) != 1) {
            throw exception(PAY_ORDER_PREVIOUS_ATTEMPT_UNCONFIRMED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public boolean closeIfAttemptsClosed(PayOrderDO expectedOrder) {
        PayOrderDO current = orderMapper.selectByIdForUpdate(expectedOrder.getId());
        checkIdentity(current, expectedOrder);
        if (PayOrderStatusEnum.isClosed(current.getStatus())) return true;
        if (!PayOrderStatusEnum.isWaiting(current.getStatus()) || !samePaymentTerms(current, expectedOrder)) return false;
        // RC 当前读：不能使用调用方查询渠道之前读到的 extension 列表。
        // 新尝试也必须先锁同一父单，因此这里不会漏掉已经提交或稍后插入的尝试。
        if (orderExtensionMapper.selectListByOrderId(current.getId()).stream()
                .anyMatch(attempt -> !PayOrderStatusEnum.isClosed(attempt.getStatus())
                        || !PayOrderSubmitStateEnum.isDispatched(attempt.getSubmitState())
                        || StrUtil.isNotBlank(attempt.getChannelErrorCode())
                        || StrUtil.isNotBlank(attempt.getChannelErrorMsg()))) return false;
        return orderMapper.updateByIdAndStatus(current.getId(), PayOrderStatusEnum.WAITING.getStatus(),
                new PayOrderDO().setStatus(PayOrderStatusEnum.CLOSED.getStatus())) == 1;
    }

    /** 金额与付款准备串行化：有任意付款尝试后不可改变金额，同额待付款请求是无写入幂等。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void updatePrice(Long id, Integer payPrice) {
        if (id == null || id <= 0 || payPrice == null || payPrice <= 0) {
            throw exception(cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST);
        }
        PayOrderDO current = orderMapper.selectByIdForUpdate(id);
        if (current == null) throw exception(PAY_ORDER_NOT_FOUND);
        if (!PayOrderStatusEnum.isWaiting(current.getStatus())) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING);
        if (!orderExtensionMapper.selectListByOrderId(id).isEmpty()) throw exception(PAY_ORDER_PRICE_CHANGE_NOT_ALLOWED);
        if (Objects.equals(current.getPrice(), payPrice)) return;
        if (orderMapper.updateByIdAndStatus(id, PayOrderStatusEnum.WAITING.getStatus(),
                new PayOrderDO().setPrice(payPrice)) != 1) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING);
    }

    private void checkIdentity(PayOrderDO current, PayOrderDO expected) {
        if (current == null || !Objects.equals(current.getAppId(), expected.getAppId())
                || !Objects.equals(current.getMerchantOrderId(), expected.getMerchantOrderId())) {
            throw exception(PAY_ORDER_NOT_FOUND);
        }
    }

    private boolean samePaymentTerms(PayOrderDO current, PayOrderDO expected) {
        return Objects.equals(current.getPrice(), expected.getPrice())
                && Objects.equals(current.getExpireTime(), expected.getExpireTime())
                && Objects.equals(current.getSubject(), expected.getSubject())
                && Objects.equals(current.getBody(), expected.getBody())
                && Objects.equals(current.getUserId(), expected.getUserId())
                && Objects.equals(current.getUserType(), expected.getUserType());
    }
}
