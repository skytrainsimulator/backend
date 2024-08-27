package io.u11.skytrainsim.backend.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.module.SimpleSerializers
import org.locationtech.jts.geom.Point
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.temporal.ChronoField

@Component
class CustomJacksonModule : SimpleModule() {
    override fun setupModule(context: SetupContext) {
        val serializers = SimpleSerializers()
        serializers.addSerializer(Point::class.java, PointSerializer())
        serializers.addSerializer(OffsetDateTime::class.java, OffsetDateTimeSerializer())

        context.addSerializers(serializers)
    }
}

class PointSerializer : JsonSerializer<Point>() {
    override fun serialize(
        value: Point,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeStartObject()
        gen.writeNumberField("lng", value.x)
        gen.writeNumberField("lat", value.y)
        gen.writeEndObject()
    }
}

class OffsetDateTimeSerializer : JsonSerializer<OffsetDateTime>() {
    override fun serialize(
        value: OffsetDateTime,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeNumber((value.toEpochSecond() * 1000) + value.get(ChronoField.MILLI_OF_SECOND))
    }
}
