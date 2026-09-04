package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 企业资质主档 Mapper（UK 判重 + QUAL_EXPIRY 扫描候选）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（UK）＋§十五-到期预警复用（valid_to 并入扫描）。
 */
@Mapper
public interface EnterpriseQualificationMapper extends BaseMapperX<EnterpriseQualificationDO> {

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

    /** 未到期但进入预警窗口（valid_to ∈ (today, today+maxDays]）的资质主档（QUAL_EXPIRY 扫描源一）。 */
    default List<EnterpriseQualificationDO> selectExpiringCandidates(LocalDate today, int maxDays) {
        return selectList(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .gt(EnterpriseQualificationDO::getValidTo, today)
                .le(EnterpriseQualificationDO::getValidTo, today.plusDays(maxDays)));
    }
}
