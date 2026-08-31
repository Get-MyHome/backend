package aichallenge.getmyhome.complex.dto.req;

import aichallenge.getmyhome.complex.enums.HouseCategory;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

@Schema(description = "사용자 조건 기반 공고 매칭 요청. conditionToken 또는 user 중 하나는 필수")
public record MatchedComplexRequest(
    @Schema(description = "대출 자격 조회 시 발급받은 조건 토큰. user와 동시에 전달하면 토큰이 우선", example = "CT-a1b2c3d4")
    String conditionToken,

    @Schema(description = "사용자 조건 직접 전달 (토큰 없이 독립 호출 시 사용)")
    @Valid
    UserConditionRequest user,

    @Schema(description = "공급지역 필터", example = "서울")
    String region,

    @Schema(description = "주택 구분 필터. PUBLIC: 국민주택, PRIVATE: 민영주택")
    HouseCategory houseCategory,

    @Schema(description = "페이지 번호 (1부터 시작)", example = "1")
    Integer page,

    @Schema(description = "페이지당 항목 수", example = "20")
    Integer size
) {
    public int pageOrDefault() { return page != null ? page : 1; }
    public int sizeOrDefault() { return size != null ? size : 20; }
}