package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 人员配置 Mapper（达标核查按角色计数）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（reg_valid_to NULL 即不满足 → 仅计有效在册）。
 */
@Mapper
public interface QualificationStaffConfigMapper extends BaseMapperX<QualificationStaffConfigDO> {

    /**
     * 按角色统计“有效在册”人数（在职且注册未过期，供 15-6 要素聚合）。
     */
    default List<Map<String, Object>> selectActiveCountByRole(Long qualId, LocalDate asOf) {
        return selectMaps(new QueryWrapper<QualificationStaffConfigDO>()
                .select("staff_role AS role_key", "COUNT(*) AS cnt")
                .eq("qual_id", qualId)
                .eq("is_full_time", 1)
                .and(w -> w.isNull("reg_valid_to").or().ge("reg_valid_to", asOf))
                .groupBy("staff_role"));
    }
}
