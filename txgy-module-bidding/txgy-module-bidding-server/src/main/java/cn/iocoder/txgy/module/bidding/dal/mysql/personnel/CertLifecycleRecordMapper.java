package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.CertLifecycleRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 证件/资质生命周期记录 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2（Mapper 分层、条件拼装走 Wrapper）；§5 六-2（按业务键 + 状态建索引）；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程按业务对象与状态检索）。
 * <p>设计遵循/针对：全部查询条件都命中 DDL 索引列（`idx_lifecycle_biz`、`idx_lifecycle_group`），
 * 不做全表扫描；轨迹查询按 applied_at 倒序 + id 升序，保证同一次变更的多字段行顺序稳定。
 * <p>业务目标：按业务对象/类型/状态检索生命周期台账，支撑"这张证改过什么、年检过没过"的一键追溯。
 */
@Mapper
public interface CertLifecycleRecordMapper extends BaseMapperX<CertLifecycleRecordDO> {

    /** 幂等查重（同对象同类型同申请单号只允许一条，防接口重试重复发起）。 */
    default CertLifecycleRecordDO selectByIdempotentKey(String idempotentKey) {
        return selectOne(new LambdaQueryWrapper<CertLifecycleRecordDO>()
                .eq(CertLifecycleRecordDO::getIdempotentKey, idempotentKey)
                .last("LIMIT 1"));
    }

    /** 某业务对象的生命周期记录（按申请时间倒序；一次变更多字段时同组多行）。 */
    default List<CertLifecycleRecordDO> selectByBiz(String bizDomain, Long bizId) {
        return selectList(new LambdaQueryWrapper<CertLifecycleRecordDO>()
                .eq(CertLifecycleRecordDO::getBizDomain, bizDomain)
                .eq(CertLifecycleRecordDO::getBizId, bizId)
                .orderByDesc(CertLifecycleRecordDO::getAppliedAt)
                .orderByAsc(CertLifecycleRecordDO::getId));
    }

    /** 按类型与状态检索（管理端待办/年检列表）。 */
    default List<CertLifecycleRecordDO> selectByTypeAndStatus(String lifecycleType, String status) {
        return selectList(new LambdaQueryWrapper<CertLifecycleRecordDO>()
                .eq(lifecycleType != null, CertLifecycleRecordDO::getLifecycleType, lifecycleType)
                .eq(status != null, CertLifecycleRecordDO::getStatus, status)
                .orderByDesc(CertLifecycleRecordDO::getAppliedAt)
                .last("LIMIT 500"));
    }

    /** 某变更组下的全部字段变更行（一次申请改了哪些字段）。 */
    default List<CertLifecycleRecordDO> selectByChangeGroup(String changeGroupId) {
        return selectList(new LambdaQueryWrapper<CertLifecycleRecordDO>()
                .eq(CertLifecycleRecordDO::getChangeGroupId, changeGroupId)
                .orderByAsc(CertLifecycleRecordDO::getId));
    }
}
