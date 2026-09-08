package cn.iocoder.txgy.module.cms.dto.law;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CmsLawSearchReqDTOTest {

    @Test
    void requiresAQueryAndBoundsPageSize() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            CmsLawSearchReqDTO request = new CmsLawSearchReqDTO();
            assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setKeyword("劳动合同");
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setPageSize(101);
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }

}
