package cn.iocoder.txgy.module.erp.service.recognition.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别结果的服务端校验结论。
 */
@Data
public class ErpRecognitionValidation {

    private boolean passed;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

}
