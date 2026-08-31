package com.hiveapp.shared.money;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.math.BigDecimal;

/** Writes exact decimals as non-exponential JSON strings. */
final class ExactDecimalSerializer extends StdScalarSerializer<BigDecimal> {

    ExactDecimalSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeString(value.toPlainString());
    }
}
