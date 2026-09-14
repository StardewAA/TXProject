package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

/**
 * 工程业绩 Mapper（按资质聚合计数供 15-6 核查）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-4（qual_id 聚合、completion_date 年限窗口）。
 */
@Mapper
public interface QualificationPerformanceMapper extends BaseMapperX<QualificationPerformanceDO> {

    /** 统计自 sinceDate 起已竣工验收且总承包（GENERAL）的业绩条数。 */
    default Long countRecentGeneralByQual(Long qualId, LocalDate sinceDate) {
        return selectCount(new LambdaQueryWrapper<QualificationPerformanceDO>()
                .eq(QualificationPerformanceDO::getQualId, qualId)
                .eq(QualificationPerformanceDO::getRole, "GENERAL")
                .ge(QualificationPerformanceDO::getCompletionDate, sinceDate));
    }

    /** 已录入四库一平台的业绩条数（线上可核验，15-4）。 */
    default Long countPlatformVerifiedByQual(Long qualId) {
        return selectCount(new LambdaQueryWrapper<QualificationPerformanceDO>()
                .eq(QualificationPerformanceDO::getQualId, qualId)
                .eq(QualificationPerformanceDO::getIsPlatformVerified, true));
    }
}
