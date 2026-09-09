package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MES 项目导入 Response VO
 *
 * @author OPENLAB BS
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MesProProjectImportRespVO {

    /** 创建成功的项目编码集合 */
    private List<String> createCodes;

    /** 更新成功的项目编码集合 */
    private List<String> updateCodes;

    /** 失败的项目编码集合（key 为编码或行号，value 为失败原因） */
    private Map<String, String> failureCodes;

}
