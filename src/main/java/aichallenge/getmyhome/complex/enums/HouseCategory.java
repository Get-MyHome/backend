package aichallenge.getmyhome.complex.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "주택 구분 필터")
@Getter
@RequiredArgsConstructor
public enum HouseCategory {

    @Schema(description = "공공주택 (국민주택)")
    PUBLIC("03"),

    @Schema(description = "민간주택 (민영주택)")
    PRIVATE("01");

    private final String houseDtlSecd;
}