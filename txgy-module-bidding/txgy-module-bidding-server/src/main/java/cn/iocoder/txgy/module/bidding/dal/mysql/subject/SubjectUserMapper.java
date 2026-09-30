package cn.iocoder.txgy.module.bidding.dal.mysql.subject;
// [依据] 模块开发规范 §3.4（Mapper 层规约：继承 BaseMapperX，条件用 LambdaQueryWrapperX 风格由 Service 组装）

import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectUserDO;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SubjectUserMapper extends BaseMapperX<SubjectUserDO> {

    /**
     * 按用户查「他被授权为经办人的企业（租户）」列表。
     *
     * <p>为什么要关掉租户过滤（2026-09-20 新增）：个人端账号（尤其"个人端注册"产生的账号）
     * 往往属于平台默认租户 tenant_id=0，而经办人授权行是**企业租户**写的；
     * 若跟随当前租户过滤，这个查询对个人端账号永远返回空 —— 「个人端提交的记录归到企业」就无从谈起。
     * 这里按"用户"这一维度跨租户查，授权行本身即证据（谁把该用户加为自己的经办人）。
     */
    default List<SubjectUserDO> selectListByUserId(Long userId) {
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapperX<SubjectUserDO>()
                .eq(SubjectUserDO::getUserId, userId)
                .eq(SubjectUserDO::getEnabled, true)
                .orderByAsc(SubjectUserDO::getTenantId)
                .orderByAsc(SubjectUserDO::getId)));
    }
}
