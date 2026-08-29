package aichallenge.getmyhome.complex.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "청약 공고 상세 응답")
public record ComplexDetailResponse(
  @Schema(description = "공고 관리 번호", example = "2025000001")
  String complexId,
  @Schema(description = "단지명", example = "래미안 원베일리")
  String name,
  @Schema(description = "주택 상세 구분. 가능한 값: 민영, 국민, 민간사전청약 등", example = "민영")
  String houseType,
  @Schema(description = "공급지역명. 주의: 경기 지역은 '경기도'로 표기됨", example = "서울")
  String region,
  @Schema(description = "공급위치 주소", example = "서울특별시 서초구 반포동 1-1")
  String address,
  @Schema(description = "모집공고일 (YYYY-MM-DD)", example = "2026-08-01")
  String announcementDate,
  @Schema(description = "청약접수 종료일 (YYYY-MM-DD)", example = "2026-08-15")
  String applicationEndDate,
  @Schema(description = "대표 분양가 (첫 번째 주택형 기준, 만원 단위). 주택형 정보가 미등록된 공고이면 null", example = "120000")
  Integer salePrice,
  @Schema(description = "주택형(평형) 목록")
  List<UnitType> unitTypes,
  @Schema(description = "규제지역 구분. 가능한 값: 투기과열지구, 분양가상한제, null(해당없음)", example = "투기과열지구")
  String regulationZone,
  @Schema(description = "청약홈 공고문 URL (항상 값이 존재하며, 외부 링크)")
  String sourceUrl
) {

  @Schema(description = "주택형(평형) 정보")
  public record UnitType(
    @Schema(description = "주택형 번호", example = "01")
    String unitTypeId,
    @Schema(description = "주택형 타입 (전용면적 기준)", example = "084.9900A")
    String type,
    @Schema(description = "분양 최고가 (만원 단위). null일 수 있음", example = "85000")
    Integer salePrice,
    @Schema(description = "공급면적 (㎡)", example = "114.78")
    String supplyArea
  ) {
  }
}