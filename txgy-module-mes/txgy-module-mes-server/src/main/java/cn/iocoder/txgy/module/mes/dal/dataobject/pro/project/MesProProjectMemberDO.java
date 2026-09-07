package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.mes.enums.DictTypeConstants;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * MES 项目成员 DO
 *
 * 记录项目的参与成员及其项目内角色，一个项目下可有多个成员。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_project_member")
@KeySequence("mes_pro_project_member_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProProjectMemberDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 成员用户编号（关联系统用户，可为空）
     */
    private Long userId;
    /**
     * 成员姓名
     */
    private String userName;
    /**
     * 项目角色
     *
     * 1 项目经理、2 生产负责、3 技术负责、4 质检负责、5 其他
     * 字典 {@link DictTypeConstants#MES_PRO_PROJECT_MEMBER_ROLE}
     */
    private Integer role;
    /**
     * 备注
     */
    private String remark;

}
