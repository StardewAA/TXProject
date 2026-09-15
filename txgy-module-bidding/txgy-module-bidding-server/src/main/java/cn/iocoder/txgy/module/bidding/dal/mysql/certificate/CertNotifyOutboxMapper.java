package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知发件箱 Mapper（取可发行：PENDING/FAILED 且到重试时间且未超最大重试）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12：INDEX (send_status, next_retry_at)
 * 定时扫描待发；重试退避 1/5/30 分、2/6 时；DEAD=死信。
 */
@Mapper
public interface CertNotifyOutboxMapper extends BaseMapperX<CertNotifyOutboxDO> {

    /**
     * 取待发批次：状态 PENDING/FAILED、到 next_retry_at（或未排期）、未超最大重试；按主键升序限量。
     */
    default List<CertNotifyOutboxDO> selectDispatchable(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapper<CertNotifyOutboxDO>()
                .in(CertNotifyOutboxDO::getSendStatus, "PENDING", "FAILED")
                .and(w -> w.isNull(CertNotifyOutboxDO::getNextRetryAt)
                        .or()
                        .le(CertNotifyOutboxDO::getNextRetryAt, now))
                .apply("retry_count < max_retry")
                .orderByAsc(CertNotifyOutboxDO::getId)
                .last("LIMIT " + limit));
    }
}
