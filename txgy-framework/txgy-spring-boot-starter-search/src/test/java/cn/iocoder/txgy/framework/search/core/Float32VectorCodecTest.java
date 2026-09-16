package cn.iocoder.txgy.framework.search.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class Float32VectorCodecTest {

    @Test
    void encodesLittleEndianFloat32() {
        byte[] encoded = Float32VectorCodec.encode(new float[]{1.0f, -2.5f});
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(buffer.getFloat()).isEqualTo(1.0f);
        assertThat(buffer.getFloat()).isEqualTo(-2.5f);
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Float32VectorCodec.encode(new float[]{Float.NaN}));
    }
}
