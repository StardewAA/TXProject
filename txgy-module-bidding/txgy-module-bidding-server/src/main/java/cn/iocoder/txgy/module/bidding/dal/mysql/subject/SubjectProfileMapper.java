package cn.iocoder.txgy.module.bidding.dal.mysql.subject;
// [依据] 模块开发规范 §3.4（Mapper 层规约：继承 BaseMapperX，条件用 LambdaQueryWrapperX 风格由 Service 组装）

import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SubjectProfileMapper extends BaseMapperX<SubjectProfileDO> {

    /**
     * 清除营业执照影像（契约 B1）：**手写 UPDATE 显式 SET NULL**。
     *
     * <p><b>为什么不用 {@code updateById}</b>：MyBatis-Plus 默认字段策略为 {@code NOT_NULL}，
     * 值为 {@code null} 的字段**不会出现在 UPDATE 语句里** —— 这正是"移除点了不生效、
     * 接口却返回成功"的根因（详见 {@link SubjectProfileService#clearOwnLicenseImage} 的说明）。
     * 要表达"清空"这一语义，只有显式写 SQL。
     *
     * <p><b>先例</b>：{@code BidReviewReportMapper#clearGeneratedFilesByTaskId} 用同一手法清空
     * Word/PDF 指针，其注释记录了同一坑（"updateById 不覆盖 null 字段"）。本方法沿用该模式，
     * 使模块内"清空字段"有统一做法，避免下一个人再摸索一遍。
     *
     * <p><b>租户安全</b>：WHERE 只用主键 id —— 调用方（Service）已通过 {@code findOwnOne()}
     * 在**当前租户上下文**内查到本租户唯一档案，拿到的是本租户的 id，不存在跨租户删改。
     * （框架租户拦截器亦会自动为 UPDATE 追加 tenant_id 条件，双保险。）
     *
     * @param id 档案主键（本租户唯一档案）
     * @return 影响行数（0=档案不存在；1=已清空）
     */
    @Update("UPDATE bidding_subject_profile SET license_file_id = NULL, license_file_name = NULL "
            + "WHERE id = #{id} AND deleted = b'0'")
    int clearLicenseImageById(@Param("id") Long id);
}
