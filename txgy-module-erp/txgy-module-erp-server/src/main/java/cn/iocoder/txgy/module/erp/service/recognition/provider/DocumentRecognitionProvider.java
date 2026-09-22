package cn.iocoder.txgy.module.erp.service.recognition.provider;

/**
 * 单据识别服务适配器。业务服务不依赖具体 OCR 厂商 SDK。
 */
public interface DocumentRecognitionProvider {

    String getType();

    String recognize(String sourceFileUrl, String bizType);

}
