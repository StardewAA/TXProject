package cn.iocoder.txgy.module.bidding.enums.certificate;

/**
 * 电子证照域字典类型常量（**字典是唯一权威来源**，规范 §5 三-7：代码型字段存 code，字典 key 前缀 bidding_）。
 *
 * <p>为什么单独一个类：此前证照域把「状态/类型/渠道」等同一概念**既写枚举又写字典**（9 组重复定义，
 * 见 2026-09-20 核查），前端去读枚举导致文档与实现不一致。本类集中登记字典 key，
 * 作为**唯一取值来源**；枚举仅保留代码内部的状态机判断用途（如 isTransitionAllowed），不再作为对外取值来源。
 *
 * <p>与库内 dict_type 一一对应（若改名需同步 sql/history 种子）。
 */
public interface CertDictTypes {

    /** 证照大类（身份类/资质类/业绩类/资信类） */
    String CERT_CATEGORY = "cert_category";
    /** 证照类型（营业执照/体系认证/…） */
    String CERT_TYPE = "cert_type";
    /** 证照类型 → 大类 归属映射（值=大类 code） */
    String CERT_TYPE_CATEGORY = "cert_type_category";
    /** 证照状态（正常/即将到期/已过期/暂扣/吊销/注销） */
    String CERT_STATUS = "cert_status";
    /** 员工证书类型（身份证/学历/职称/注册执业/…） */
    String PERSON_CERT_TYPE = "person_cert_type";
    /** 员工证书状态 */
    String PERSON_CERT_STATUS = "person_cert_status";
    /** 文件类别（原件/加注件/变更页/延续页/电子证照） */
    String FILE_CATEGORY = "file_category";
    /** 生命周期类型（变更/续期/注销吊销/年检） */
    String LIFECYCLE_TYPE = "lifecycle_type";
    /** 生命周期状态（待办/审核中/已通过/已驳回/已生效） */
    String LIFECYCLE_STATUS = "lifecycle_status";
    /** 吊销/注销/暂扣/恢复 类型 */
    String REVOKE_TYPE = "revoke_type";
    /** 验真状态（未验真/验真中/已验真/验真不通过） */
    String VERIFICATION_STATUS = "verification_status";
    /** 验真渠道（四库一平台/部级/省级/第三方） */
    String VERIFY_CHANNEL = "verify_channel";
    /** 预警类型（到期/年检/续期/资质到期） */
    String ALERT_TYPE = "alert_type";
    /** 通知渠道（邮件/短信/站内信/钉钉/Webhook） */
    String NOTIFY_CHANNEL = "notify_channel";
    /** 病毒扫描状态（UNKNOWN/PENDING/CLEAN/INFECTED） */
    String VIRUS_STATUS = "cert_virus_status";
    /** 资质序列（施工/勘察/设计/监理/检测） */
    String QUAL_SERIAL = "qual_serial";
    /** 资质状态 */
    String QUAL_STATUS = "qual_status";
    /** 变更类型（建档/更新/状态流转/吊销注销）——变更留痕读出口展示用 */
    String CHANGE_TYPE = "cert_change_type";
    /** 变更操作来源（手工/导入/OCR/接口同步/系统）——变更留痕读出口展示用 */
    String OP_SOURCE = "cert_op_source";
}
