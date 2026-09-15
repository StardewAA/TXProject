package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStandardDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

/**
 * 资质标准库 Mapper（按生效区间取现行版本）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-2（std_version + effective_from/to）。
 */
@Mapper
public interface QualificationStandardMapper extends BaseMapperX<QualificationStandardDO> {

    /**
     * 取 asOf 当日仍在生效且最新发布的标准（effective_from 最晚优先）。
     */
    default QualificationStandardDO selectCurrentStandard(String qualSerial, String qualCategory, String qualGrade,
                                                          LocalDate asOf) {
        return selectOne(new LambdaQueryWrapper<QualificationStandardDO>()
                .eq(QualificationStandardDO::getQualSerial, qualSerial)
                .eq(QualificationStandardDO::getQualCategory, qualCategory)
                .eq(QualificationStandardDO::getQualGrade, qualGrade)
                .le(QualificationStandardDO::getEffectiveFrom, asOf)
                .and(w -> w.isNull(QualificationStandardDO::getEffectiveTo)
                        .or().ge(QualificationStandardDO::getEffectiveTo, asOf))
                .orderByDesc(QualificationStandardDO::getEffectiveFrom)
                .last("LIMIT 1"));
    }

    /** 同适用范围同版本判重。 */
    default QualificationStandardDO selectByScopeVersion(String qualSerial, String qualCategory, String qualGrade,
                                                         String stdVersion) {
        return selectOne(new LambdaQueryWrapper<QualificationStandardDO>()
                .eq(QualificationStandardDO::getQualSerial, qualSerial)
                .eq(QualificationStandardDO::getQualCategory, qualCategory)
                .eq(QualificationStandardDO::getQualGrade, qualGrade)
                .eq(QualificationStandardDO::getStdVersion, stdVersion)
                .last("LIMIT 1"));
    }
}
