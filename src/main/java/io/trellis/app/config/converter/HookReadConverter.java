package io.trellis.app.config.converter;

import io.trellis.common.model.Hook;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Reads Hook through Jackson so the @JsonTypeInfo on its polymorphic "config" field is honoured.
 */
@ReadingConverter
public class HookReadConverter implements Converter<Document, Hook> {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public Hook convert(Document source) {
        return objectMapper.convertValue(source, Hook.class);
    }
}
