package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.framework.ip.core.Area;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public interface HrmRecruitPostMapper extends BaseMapperX<HrmRecruitPostDO> {

    String PORTAL_HR_PUBLISHER_EXISTS_SQL = """
            EXISTS (
                SELECT 1
                FROM system_users publisher_user
                JOIN system_user_role publisher_user_role
                  ON publisher_user_role.user_id = publisher_user.id
                 AND publisher_user_role.tenant_id = publisher_user.tenant_id
                 AND publisher_user_role.deleted = 0
                JOIN system_role publisher_role
                  ON publisher_role.id = publisher_user_role.role_id
                 AND publisher_role.tenant_id = publisher_user.tenant_id
                 AND publisher_role.deleted = 0
                 AND publisher_role.status = 0
                WHERE publisher_user.deleted = 0
                  AND publisher_user.status = 0
                  AND publisher_user.tenant_id = hrm_recruit_post.tenant_id
                  AND (CONCAT(publisher_user.id, '') = hrm_recruit_post.creator
                       OR publisher_user.username = hrm_recruit_post.creator)
                  AND (LOWER(publisher_role.code) = 'super_admin'
                       OR LOWER(publisher_role.code) LIKE '%hr%'
                       OR LOWER(publisher_role.name) LIKE '%hr%'
                       OR publisher_role.name LIKE '%人力%'
                       OR publisher_role.name LIKE '%人事%')
            )
            """;

    default long selectCountByPostTypeId(Long postTypeId) {
        return selectCount(new LambdaQueryWrapperX<HrmRecruitPostDO>()
                .eq(HrmRecruitPostDO::getPostTypeId, postTypeId));
    }

    default PageResult<HrmRecruitPostDO> selectPage(HrmRecruitPostPageReqVO reqVO) {
        return selectPage(reqVO, buildQueryWrapper(reqVO)
                .eqIfPresent("status", reqVO.getStatus())
                .orderByDesc("id"));
    }

    default PageResult<HrmRecruitPostDO> selectPortalPage(HrmRecruitPostPageReqVO reqVO) {
        return selectPage(reqVO, buildQueryWrapper(reqVO)
                .eqIfPresent("status", reqVO.getStatus())
                .apply(PORTAL_HR_PUBLISHER_EXISTS_SQL)
                .orderByDesc("id"));
    }

    default HrmRecruitPostDO selectPortalById(Long id) {
        return selectOne(new QueryWrapperX<HrmRecruitPostDO>()
                .eq("id", id)
                .apply(PORTAL_HR_PUBLISHER_EXISTS_SQL));
    }

    default List<Long> selectPortalHiringTenantIds() {
        QueryWrapperX<HrmRecruitPostDO> query = new QueryWrapperX<>();
        query.select("tenant_id");
        query.eq("status", cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        query.apply(PORTAL_HR_PUBLISHER_EXISTS_SQL);
        query.groupBy("tenant_id");
        return selectMaps(query).stream().map(item -> MapUtil.getLong(item, "tenant_id"))
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
    }

    default List<Map<String, Object>> selectPortalPostCountGroupByTenant(List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return List.of();
        }
        QueryWrapperX<HrmRecruitPostDO> query = new QueryWrapperX<>();
        query.select("tenant_id", "COUNT(id) AS post_count");
        query.in("tenant_id", tenantIds);
        query.eq("status", cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        query.apply(PORTAL_HR_PUBLISHER_EXISTS_SQL);
        query.groupBy("tenant_id");
        return selectMaps(query);
    }

    default PageResult<HrmRecruitPostDO> selectPortalPageByTenantId(Long tenantId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<HrmRecruitPostDO>()
                .eq(HrmRecruitPostDO::getTenantId, tenantId)
                .eq(HrmRecruitPostDO::getStatus,
                        cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum.PUBLISHED.getStatus())
                .apply(PORTAL_HR_PUBLISHER_EXISTS_SQL)
                .orderByDesc(HrmRecruitPostDO::getId));
    }

    default Map<Integer, Long> selectCountMapByStatus(HrmRecruitPostPageReqVO reqVO) {
        QueryWrapperX<HrmRecruitPostDO> query = buildQueryWrapper(reqVO);
        query.select("status", "COUNT(id) AS count").groupBy("status");
        return CollectionUtils.convertMap(selectMaps(query),
                record -> MapUtil.getInt(record, "status"),
                record -> MapUtil.getLong(record, "count"));
    }

    default List<HrmRecruitPostDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitPostDO>()
                .eq(HrmRecruitPostDO::getStatus, status)
                .orderByDesc(HrmRecruitPostDO::getId));
    }

    @SuppressWarnings("UnusedReturnValue")
    default int updateForEdit(HrmRecruitPostDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<HrmRecruitPostDO>()
                .eq(HrmRecruitPostDO::getId, updateObj.getId())
                .set(updateObj.getDeptId() == null, HrmRecruitPostDO::getDeptId, null)
                .set(updateObj.getAreaId() == null, HrmRecruitPostDO::getAreaId, null)
                .set(updateObj.getWorkAddress() == null, HrmRecruitPostDO::getWorkAddress, null)
                .set(updateObj.getLongitude() == null, HrmRecruitPostDO::getLongitude, null)
                .set(updateObj.getLatitude() == null, HrmRecruitPostDO::getLatitude, null)
                .set(updateObj.getRecruitNum() == null, HrmRecruitPostDO::getRecruitNum, null)
                .set(updateObj.getReason() == null, HrmRecruitPostDO::getReason, null)
                .set(updateObj.getWorkTime() == null, HrmRecruitPostDO::getWorkTime, null)
                .set(updateObj.getEducationRequire() == null, HrmRecruitPostDO::getEducationRequire, null)
                .set(updateObj.getMinSalary() == null, HrmRecruitPostDO::getMinSalary, null)
                .set(updateObj.getMaxSalary() == null, HrmRecruitPostDO::getMaxSalary, null)
                .set(updateObj.getSalaryUnit() == null, HrmRecruitPostDO::getSalaryUnit, null)
                .set(updateObj.getMinAge() == null, HrmRecruitPostDO::getMinAge, null)
                .set(updateObj.getMaxAge() == null, HrmRecruitPostDO::getMaxAge, null)
                .set(updateObj.getLatestEntryTime() == null, HrmRecruitPostDO::getLatestEntryTime, null)
                .set(updateObj.getOwnerEmployeeId() == null, HrmRecruitPostDO::getOwnerEmployeeId, null)
                .set(updateObj.getInterviewEmployeeIds() == null, HrmRecruitPostDO::getInterviewEmployeeIds, null)
                .set(updateObj.getDescription() == null, HrmRecruitPostDO::getDescription, null)
                .set(updateObj.getEmergencyLevel() == null, HrmRecruitPostDO::getEmergencyLevel, null)
                .set(updateObj.getPostTypeId() == null, HrmRecruitPostDO::getPostTypeId, null)
                .set(updateObj.getChannelId() == null, HrmRecruitPostDO::getChannelId, null));
    }

    private QueryWrapperX<HrmRecruitPostDO> buildQueryWrapper(HrmRecruitPostPageReqVO reqVO) {
        QueryWrapperX<HrmRecruitPostDO> query = new QueryWrapperX<HrmRecruitPostDO>()
                .likeIfPresent("post_name", reqVO.getPostName())
                .eqIfPresent("job_nature", reqVO.getJobNature())
                .inIfPresent("area_id", getAreaAndDescendantIds(reqVO.getAreaId()))
                .eqIfPresent("work_time", reqVO.getWorkTime())
                .eqIfPresent("education_require", reqVO.getEducationRequire())
                .eqIfPresent("dept_id", reqVO.getDeptId())
                .eqIfPresent("owner_employee_id", reqVO.getOwnerEmployeeId())
                .eqIfPresent("post_type_id", reqVO.getPostTypeId())
                .inIfPresent("post_type_id", reqVO.getPostTypeIds())
                .betweenIfPresent("create_time", reqVO.getCreateTime());
        if (Boolean.TRUE.equals(reqVO.getSalaryNegotiable())) {
            query.and(true, wrapper -> wrapper
                    .eq("min_salary", HrmRecruitPostDO.SALARY_NEGOTIABLE_VALUE)
                    .or()
                    .eq("max_salary", HrmRecruitPostDO.SALARY_NEGOTIABLE_VALUE));
        } else {
            if (reqVO.getSalaryMin() != null) {
                query.and(true, wrapper -> wrapper
                        .eq("min_salary", HrmRecruitPostDO.SALARY_NEGOTIABLE_VALUE)
                        .or()
                        .ge("max_salary", reqVO.getSalaryMin()));
            }
            if (reqVO.getSalaryMax() != null) {
                query.and(true, wrapper -> wrapper
                        .eq("max_salary", HrmRecruitPostDO.SALARY_NEGOTIABLE_VALUE)
                        .or()
                        .le("min_salary", reqVO.getSalaryMax()));
            }
        }
        return query;
    }

    /**
     * 按地区树展开筛选范围：省份包含其下市、区县；城市包含其下区县；区县仅匹配自身。
     */
    private List<Integer> getAreaAndDescendantIds(Integer areaId) {
        if (areaId == null) {
            return null;
        }
        Area area = AreaUtils.getArea(areaId);
        if (area == null) {
            return List.of(areaId);
        }
        List<Integer> areaIds = new ArrayList<>();
        collectAreaAndDescendantIds(area, areaIds);
        return areaIds;
    }

    private void collectAreaAndDescendantIds(Area area, List<Integer> areaIds) {
        if (area == null || area.getId() == null) {
            return;
        }
        areaIds.add(area.getId());
        if (area.getChildren() == null) {
            return;
        }
        for (Area child : area.getChildren()) {
            collectAreaAndDescendantIds(child, areaIds);
        }
    }

}
