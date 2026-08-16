package com.chengde.smartcity.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 日期时间统一为 {@code yyyy-MM-dd HH:mm:ss}（空格分隔，无 ISO {@code T}）。
 * 反序列化兼容历史带 {@code T}、毫秒、时区后缀的入参。
 */
@Configuration
public class JacksonDateTimeConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_PATTERN);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonDateTimeCustomizer() {
        return builder -> {
            builder.simpleDateFormat(DATE_TIME_PATTERN);
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
        };
    }

    static final class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim()
                    .replace('T', ' ')
                    .replaceAll("\\.\\d+", "")
                    .replaceAll("Z$", "")
                    .replaceAll("[+-]\\d{2}:\\d{2}$", "")
                    .trim();
            if (text.length() >= 19) {
                text = text.substring(0, 19);
            } else if (text.length() == 16) {
                text = text + ":00";
            }
            try {
                return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                throw ctxt.weirdStringException(raw, LocalDateTime.class, e.getMessage());
            }
        }
    }
}
