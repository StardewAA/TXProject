package cn.iocoder.txgy.module.pay.dal.mysql.order;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayOrderExtensionMapper extends BaseMapperX<PayOrderExtensionDO> {

    default PayOrderExtensionDO selectByNo(String no) {
        return selectOne(PayOrderExtensionDO::getNo, no);
    }

    default int updateByIdAndStatus(Long id, Integer status, PayOrderExtensionDO update) {
        return update(update, new LambdaQueryWrapper<PayOrderExtensionDO>()
                .eq(PayOrderExtensionDO::getId, id).eq(PayOrderExtensionDO::getStatus, status));
    }

    default List<PayOrderExtensionDO> selectListByOrderId(Long orderId) {
        return selectList(PayOrderExtensionDO::getOrderId, orderId);
    }

    default int updateSubmitStateIfPrepared(Long id, Long orderId, Integer state) {
        return update(new PayOrderExtensionDO().setSubmitState(state),
                new LambdaQueryWrapper<PayOrderExtensionDO>()
                        .eq(PayOrderExtensionDO::getId, id).eq(PayOrderExtensionDO::getOrderId, orderId)
                        .eq(PayOrderExtensionDO::getSubmitState, 0));
    }

    /** 仅在查询得到无错误的明确 CLOSED 后清除历史错误，记录真实关闭证据。 */
    default int updateVerifiedClosed(Long id, Integer expectedStatus, String notifyData) {
        return update(null, new LambdaUpdateWrapper<PayOrderExtensionDO>()
                .eq(PayOrderExtensionDO::getId, id).eq(PayOrderExtensionDO::getStatus, expectedStatus)
                .set(PayOrderExtensionDO::getStatus, 30)
                .set(PayOrderExtensionDO::getChannelErrorCode, null)
                .set(PayOrderExtensionDO::getChannelErrorMsg, null)
                .set(PayOrderExtensionDO::getChannelNotifyData, notifyData));
    }

    default List<PayOrderExtensionDO> selectListByOrderIdAndStatus(Long orderId, Integer status) {
        return selectList(PayOrderExtensionDO::getOrderId, orderId,
                PayOrderExtensionDO::getStatus, status);
    }

    default List<PayOrderExtensionDO> selectListByStatusAndCreateTimeGe(Integer status, LocalDateTime minCreateTime) {
        return selectList(new LambdaQueryWrapper<PayOrderExtensionDO>()
                .eq(PayOrderExtensionDO::getStatus, status)
                .ge(PayOrderExtensionDO::getCreateTime, minCreateTime));
    }

}
