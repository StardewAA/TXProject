package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 多岗合规校验结论（表 personnel_multi_post_check，只追加）。
 *
 * <p>规范出处：用户业务口径"兼任两个及以上岗位的，必须取得另一岗位的考核合格证书方可上岗"；
 * 《电子资质七大模块技术方案.md》§1.1(f)/§1.3。
 * <p>为什么结论也落库而不是只返回：上岗合规是事后追责的依据（谁在什么时候批准了无证兼任），
 * 只返回不落库会导致"当时校验过但查不到结果"。
 * <p>业务目标：多岗兼任的合规性可校验、可留痕、可追责。
 */
@TableName("personnel_multi_post_check")
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonnelMultiPostCheckDO extends TenantBaseDO {

    private Long id;
    private Long personId;
    private String primaryPost;
    private String secondaryPost;
    private String requiredCertType;
    private String requiredCertSubType;
    /** 命中的员工证书主键（校验通过时） */
    private Long matchedCertId;
    /** 结论 code：PASS/FAIL */
    private String checkResult;
    private String failReason;
    private LocalDateTime checkedAt;
    private String ruleVersion;
}
