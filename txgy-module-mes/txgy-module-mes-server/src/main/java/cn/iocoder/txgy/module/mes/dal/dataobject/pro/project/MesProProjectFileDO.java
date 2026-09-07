package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
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
 * MES 项目附件 DO
 *
 * 只记录项目与文件的关联关系，文件实体由 infra 文件服务统一管理，
 * 避免 mes 模块引入 infra 依赖。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_project_file")
@KeySequence("mes_pro_project_file_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProProjectFileDO extends BaseDO {

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
     * infra 文件编号
     */
    private Long fileId;
    /**
     * 文件名称
     */
    private String fileName;
    /**
     * 文件访问地址
     */
    private String fileUrl;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 备注
     */
    private String remark;

}
