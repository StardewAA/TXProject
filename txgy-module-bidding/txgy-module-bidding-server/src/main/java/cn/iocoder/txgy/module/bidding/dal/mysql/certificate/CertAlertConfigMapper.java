package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警配置 Mapper（租户隔离由平台拦截器实施）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九：cert_type IS NULL=全局配置行（租户内唯一），
 * 差异化配置按 cert_type+alert_type 定位。
 */
@Mapper
public interface CertAlertConfigMapper extends BaseMapperX<CertAlertConfigDO> {

    /**
     * 取租户全局配置行（cert_type IS NULL；不存在返回 null → 服务层用平台默认常量）。
     */
    default CertAlertConfigDO selectGlobal(String alertType) {
        return selectOne(new LambdaQueryWrapper<CertAlertConfigDO>()
                .isNull(CertAlertConfigDO::getCertType)
                .eq(CertAlertConfigDO::getAlertType, alertType)
                .orderByAsc(CertAlertConfigDO::getId)
                .last("LIMIT 1"));
    }
}
