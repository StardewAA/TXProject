package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;

/**
 * 资产要素 Mapper（净资产聚合：按统计年度取审计口径）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-5（净资产核查取审计值 stat_year）。
 */
@Mapper
public interface QualificationAssetMapper extends BaseMapperX<QualificationAssetDO> {

    /** 企业最新统计年度净资产合计（万元口径存值）。 */
    default BigDecimal sumLatestNetAsset(Long entId) {
        // 先取最新统计年度（净资产以申报前一年度审计报告为准，表15-5）
        Long latestYear = selectObjs(new QueryWrapper<QualificationAssetDO>()
                .select("MAX(stat_year) AS y")
                .eq("ent_id", entId)
                .eq("asset_type", "NET_ASSET")).stream()
                .filter(o -> o != null).findFirst().map(o -> Long.valueOf(String.valueOf(o))).orElse(null);
        if (latestYear == null) {
            return BigDecimal.ZERO;
        }
        Object sum = selectObjs(new QueryWrapper<QualificationAssetDO>()
                .select("COALESCE(SUM(value_amount),0) AS s")
                .eq("ent_id", entId)
                .eq("asset_type", "NET_ASSET")
                .eq("stat_year", latestYear)).stream().findFirst().orElse(BigDecimal.ZERO);
        return sum instanceof BigDecimal b ? b : BigDecimal.ZERO;
    }
}
