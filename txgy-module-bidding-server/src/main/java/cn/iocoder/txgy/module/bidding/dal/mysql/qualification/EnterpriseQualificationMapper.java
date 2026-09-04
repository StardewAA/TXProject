package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 企业资质主档 Mapper（UK 判重；租户条件平台拦截器自动拼接）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（UK：tenant+serial+category+grade+cert_no）。
 */
@Mapper
public interface EnterpriseQualificationMapper extends BaseMapperX<EnterpriseQualificationDO> {

    /**
     * 同企业同序列/类别/等级证书编号判重（建档幂等）。
     */
    default EnterpriseQualificationDO selectByCertNo(Long entId, String qualSerial, String qualCategory,
                                                    String qualGrade, String certNo) {
        return selectOne(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .eq(EnterpriseQualificationDO::getEntId, entId)
                .eq(EnterpriseQualificationDO::getQualSerial, qualSerial)
                .eq(EnterpriseQualificationDO::getQualCategory, qualCategory)
                .eq(EnterpriseQualificationDO::getQualGrade, qualGrade)
                .eq(EnterpriseQualificationDO::getCertNo, certNo)
                .last("LIMIT 1"));
    }
}
