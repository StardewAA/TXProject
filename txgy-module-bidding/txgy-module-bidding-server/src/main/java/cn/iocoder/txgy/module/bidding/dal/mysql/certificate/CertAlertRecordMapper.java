package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警记录 Mapper（幂等查重 / 通知状态联动回写）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九：idempotent_key 幂等（同 cert+alert_type+触发日期
 * 只落一条）；notify_status 与 cert_notify_outbox 联动（表12）。
 */
@Mapper
public interface CertAlertRecordMapper extends BaseMapperX<CertAlertRecordDO> {

    /**
     * 幂等查重：存在即说明当日该证该类型已触发，定时任务重跑直接跳过。
     */
    default CertAlertRecordDO selectByIdempotentKey(String idempotentKey) {
        return selectOne(new LambdaQueryWrapper<CertAlertRecordDO>()
                .eq(CertAlertRecordDO::getIdempotentKey, idempotentKey)
                .last("LIMIT 1"));
    }

    /**
     * 通知发送结果回写（SUCCESS/FAILED/DEAD 只前进不回退，防旧状态覆盖新状态）。
     */
    default void updateNotifyStatus(Long alertId, String notifyStatus) {
        update(null, new LambdaUpdateWrapper<CertAlertRecordDO>()
                .eq(CertAlertRecordDO::getId, alertId)
                .ne(CertAlertRecordDO::getNotifyStatus, notifyStatus)
                .set(CertAlertRecordDO::getNotifyStatus, notifyStatus));
    }
}
