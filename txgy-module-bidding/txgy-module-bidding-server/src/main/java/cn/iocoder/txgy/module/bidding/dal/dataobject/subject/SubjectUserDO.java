package cn.iocoder.txgy.module.bidding.dal.dataobject.subject;
// [依据] “管理端支持管理用户”（需求）：为已授权主体绑定其企业(租户)下的经办/操作账号（AdminUser）。
//       用户真实性由 system AdminUserApi 校验（validateUser + 归属租户核验）；存昵称快照免联查。
//       绑定只作为经办人的操作资格，不代表“信息完善/投标资格”（后者看档案 completeFlag + 授权状态）。

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主体经办用户绑定（G 轨，平台管理端 + 企业自维护读）。表：bidding_subject_user（DDL 见 sql/2026-09-09-subject.sql）。
 */
@TableName("bidding_subject_user")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectUserDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 主体所属企业租户 */
    private Long tenantId;
    /** 主体类型 */
    private String subjectType;
    /** 经办用户 id（system AdminUser） */
    private Long userId;
    /** 用户昵称快照（AdminUserApi.getUser(id).nickname 授权时点取值） */
    private String userNickname;
    /** 经办角色：OPERATOR（经办/投标操作人，默认）/ CONTACT（联系人） */
    private String userRole;
    /** 启用开关（停用=解绑保留历史） */
    private Boolean enabled;
    /** 备注 */
    private String remark;
}
