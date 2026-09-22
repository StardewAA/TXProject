package cn.iocoder.txgy.module.ai.api.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCustomerServiceReplyRespDTO {

    private String reply;
    private String model;
    /** 最多三个、且必须来自请求上下文候选集的商品 SPU 编号。 */
    private List<Long> recommendedProductIds;
}
