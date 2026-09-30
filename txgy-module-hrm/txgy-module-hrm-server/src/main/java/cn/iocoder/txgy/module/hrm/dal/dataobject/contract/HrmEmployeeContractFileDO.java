package cn.iocoder.txgy.module.hrm.dal.dataobject.contract;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * HRM 合同文件台账 DO
 *
 * <p>用于区分「系统生成稿」与「签署件」，并记录生成时的模板与关键字段快照，支持版本溯源。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employee_contract_file")
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class HrmEmployeeContractFileDO extends BaseDO {

    /** 文件类型：系统生成稿 */
    public static final Integer FILE_TYPE_GENERATED = 1;
    /** 文件类型：签署件 */
    public static final Integer FILE_TYPE_SIGNED = 2;

    @TableId
    private Long id;
    /** 合同编号 */
    private Long contractId;
    /** 员工编号 */
    private Long employeeId;
    /** 文件类型：1 系统生成稿，2 签署件 */
    private Integer fileType;
    /** 版本号 */
    private Integer version;
    /** 展示文件名（员工姓名-合同类型） */
    private String fileName;
    /** 文件地址 */
    private String fileUrl;
    /** 生成稿使用的合同模板编号 */
    private Long templateId;
    /** 生成时的关键字段快照 JSON */
    private String renderSnapshot;
    /** 签署件对应的签署记录编号 */
    private Long signRecordId;

}
