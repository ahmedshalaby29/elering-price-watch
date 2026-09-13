package com.elering.pricewatch.mapper;

import com.elering.pricewatch.domain.entity.HourlyPrice;
import com.elering.pricewatch.dto.response.HourlyPriceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MapStruct mapper between {@link HourlyPrice} entities and {@link HourlyPriceDto} DTOs.
 *
 * <p>The {@code localHourLabel} field is derived by converting the UTC {@code hourStart}
 * to Estonian local time (Europe/Tallinn) and formatting it as "HH:mm–HH:mm".
 */
@Mapper(componentModel = "spring")
public interface HourlyPriceMapper {

    ZoneId TALLINN_TZ = ZoneId.of("Europe/Tallinn");
    DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(target = "localHourLabel", source = "hourStart", qualifiedByName = "toLocalHourLabel")
    HourlyPriceDto toDto(HourlyPrice entity);

    List<HourlyPriceDto> toDtoList(List<HourlyPrice> entities);

    @Named("toLocalHourLabel")
    default String toLocalHourLabel(OffsetDateTime hourStart) {
        if (hourStart == null) {
            return null;
        }
        var local = hourStart.atZoneSameInstant(TALLINN_TZ);
        var end = local.plusHours(1);
        return local.format(HOUR_FMT) + "–" + end.format(HOUR_FMT);
    }
}
