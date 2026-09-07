package cn.iocoder.txgy.module.infra.api.file.dto;

import lombok.Data;

@Data
public class FileMetadataRespDTO {
    private Long id;
    private String name;
    private String url;
    private String type;
    private Long size;
}
