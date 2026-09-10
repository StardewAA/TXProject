package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 证照文件版本 Mapper（只追加；类别内版本号由服务计算，DB 唯一约束 uk_cert_cat_version 兜底并发）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三、§18.2；租户条件由平台拦截器按 TenantBaseDO 自动拼接。
 */
@Mapper
public interface CertFileVersionMapper extends BaseMapperX<CertFileVersionDO> {

    /**
     * 同证全部文件版本（只追加历史回查；按类别、版本倒序）。
     */
    default List<CertFileVersionDO> listAllByCertId(Long certId) {
        return selectList(new LambdaQueryWrapper<CertFileVersionDO>()
                .eq(CertFileVersionDO::getCertId, certId)
                .orderByDesc(CertFileVersionDO::getFileCategory)
                .orderByDesc(CertFileVersionDO::getVersion));
    }

    /**
     * 同证同哈希判重（UK uk_cert_file_hash；防重复上传，开发规范 §5 三-5/六-1）。
     */
    default CertFileVersionDO selectByCertIdAndHash(Long certId, String fileHash) {
        return selectOne(new LambdaQueryWrapper<CertFileVersionDO>()
                .eq(CertFileVersionDO::getCertId, certId)
                .eq(CertFileVersionDO::getFileHash, fileHash)
                .last("LIMIT 1"));
    }

    /**
     * 取同证同类别当前最大版本号（无则 null → 新版本从 1 起；v3.3 §三 版本号语义）。
     */
    default Integer selectMaxVersion(Long certId, String fileCategory) {
        CertFileVersionDO last = selectOne(new LambdaQueryWrapper<CertFileVersionDO>()
                .eq(CertFileVersionDO::getCertId, certId)
                .eq(CertFileVersionDO::getFileCategory, fileCategory)
                .orderByDesc(CertFileVersionDO::getVersion)
                .last("LIMIT 1"));
        return last == null ? null : last.getVersion();
    }

    /**
     * 取该证当前生效文件行（任一类别；验真 file 校验用，v3.3 §三 is_current 语义）。
     */
    default CertFileVersionDO selectAnyCurrentByCertId(Long certId) {
        return selectOne(new LambdaQueryWrapper<CertFileVersionDO>()
                .eq(CertFileVersionDO::getCertId, certId)
                .eq(CertFileVersionDO::getIsCurrent, true)
                .orderByDesc(CertFileVersionDO::getVersion)
                .last("LIMIT 1"));
    }

    /**
     * 取同证同类别当前生效版本行（自查/详情只取 is_current=TRUE 行，v3.3 §三）。
     */
    default List<CertFileVersionDO> selectCurrentByCategory(Long certId, String fileCategory) {
        return selectList(new LambdaQueryWrapper<CertFileVersionDO>()
                .eq(CertFileVersionDO::getCertId, certId)
                .eq(CertFileVersionDO::getFileCategory, fileCategory)
                .eq(CertFileVersionDO::getIsCurrent, Boolean.TRUE));
    }
}
