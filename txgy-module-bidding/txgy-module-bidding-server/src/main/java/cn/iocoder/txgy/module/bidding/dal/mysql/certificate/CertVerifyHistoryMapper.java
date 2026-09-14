package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 验真历史 Mapper（只追加；幂等查重）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5：idempotent_key UNIQUE 防重复调用写入。
 */
@Mapper
public interface CertVerifyHistoryMapper extends BaseMapperX<CertVerifyHistoryDO> {

    /**
     * 幂等查重：同一 (cert_id, channel, 分钟) 已验真过则返回既有记录。
     */
    default CertVerifyHistoryDO selectByIdempotentKey(String idempotentKey) {
        return selectOne(new LambdaQueryWrapper<CertVerifyHistoryDO>()
                .eq(CertVerifyHistoryDO::getIdempotentKey, idempotentKey)
                .last("LIMIT 1"));
    }
}
