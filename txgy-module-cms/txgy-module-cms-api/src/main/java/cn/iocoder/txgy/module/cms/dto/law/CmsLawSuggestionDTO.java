package cn.iocoder.txgy.module.cms.dto.law;

import lombok.Data;

@Data
public class CmsLawSuggestionDTO {
    private String title;
    private String documentNo;
    private String issuingAuthority;
    private String lawType;
}
