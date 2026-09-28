package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 验真历史 Mapper（只追加；幂等查重）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5：idempotent_key UNIQUE 防重复调用写入。
 */
@Mapper
public interface CertVerifyHistoryMapper extends BaseMapperX<CertVerifyHistoryDO> {

    /**
     * 滑动窗口内的最近一次验真（同证同渠道）——验真幂等的**主判定**。
     *
     * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5；配置键
     * {@code cert.verify.idempotent.window.seconds}（默认 60）。
     * <p>为什么不用"按分钟取整的幂等键"做主判定：按分钟取整**不等于** 60 秒窗口——
     * 10:00:59 与 10:01:00 只差 1 秒却落在不同分钟桶里，会各调一次外部网关（配额与一致性都受损）。
     * 滑动窗口（verified_at ≥ now - 窗口秒数）才是配置键声明的语义。
     * <p>业务目标：窗口内重复验真直接复用既有结论，既省外部网关配额，也保证同一时刻结论一致。
     *
     * <p>为什么按"幂等键前缀"而不是 (cert_id, verify_channel) 判别：**验真与验文件两条路径的
     * verify_channel 相同**（都是渠道 code），只靠这两列会把"验信息"与"验文件"的结论互相串用；
     * 故幂等键把业务种类放在**键首**（{@code INFO:} / {@code FILE:}），前缀之间不互相包含，
     * 用 likeRight 前缀即可精确隔离两类验真。
     * <p>升级影响：改造前写入的旧格式键（{@code certId:channel:...}）不再命中窗口查询——
     * 最坏结果是上线后对同一证照多调一次网关，不影响正确性。
     */
    default CertVerifyHistoryDO selectLatestWithinWindow(String idempotentKeyPrefix, LocalDateTime windowStart) {
        return selectOne(new LambdaQueryWrapper<CertVerifyHistoryDO>()
                .likeRight(CertVerifyHistoryDO::getIdempotentKey, idempotentKeyPrefix)
                .ge(CertVerifyHistoryDO::getVerifiedAt, windowStart)
                .orderByDesc(CertVerifyHistoryDO::getVerifiedAt)
                .last("LIMIT 1"));
    }

    /**
     * 幂等查重（按幂等键）：仅用于**并发兜底**——两个请求同时发现窗口内无记录时，
     * 由唯一键 uk_verify_idem 决定谁先落库，后到者按此键取回既有记录返回。
     */
    default CertVerifyHistoryDO selectByIdempotentKey(String idempotentKey) {
        return selectOne(new LambdaQueryWrapper<CertVerifyHistoryDO>()
                .eq(CertVerifyHistoryDO::getIdempotentKey, idempotentKey)
                .last("LIMIT 1"));
    }
}
