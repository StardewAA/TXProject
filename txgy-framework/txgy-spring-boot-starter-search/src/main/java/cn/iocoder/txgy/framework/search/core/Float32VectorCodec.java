package cn.iocoder.txgy.framework.search.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Redis Vector Search 所需的 FLOAT32 little-endian 编码。 */
public final class Float32VectorCodec {

    private Float32VectorCodec() {
    }

    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("向量不能包含 NaN 或 Infinity");
            }
            buffer.putFloat(value);
        }
        return buffer.array();
    }

}
