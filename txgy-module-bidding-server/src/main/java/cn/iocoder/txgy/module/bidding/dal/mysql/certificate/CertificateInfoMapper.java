package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 证照台账 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（Mapper 继承 BaseMapperX）；租户条件由平台 TenantDatabaseInterceptor
 * 按 TenantBaseDO 自动拼接（§5 五-1），本类不做手写租户过滤。
 */
@Mapper
public interface CertificateInfoMapper extends BaseMapperX<CertificateInfoDO> {

    /**
     * 按证书编号查询（租户内唯一，UK uk_cert_no；查重用于建档幂等，开发规范 §4.5/§5 六-1）。
     */
    default CertificateInfoDO selectByCertNo(String certNo) {
        return selectOne(new LambdaQueryWrapper<CertificateInfoDO>()
                .eq(CertificateInfoDO::getCertNo, certNo)
                .last("LIMIT 1"));
    }
}
