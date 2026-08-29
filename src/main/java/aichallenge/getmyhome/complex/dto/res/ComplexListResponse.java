package aichallenge.getmyhome.complex.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "청약 공고 목록 응답")
public record ComplexListResponse(
  @Schema(description = "공고 목록") List<ComplexSummary> items,
  @Schema(description = "전체 공고 건수", example = "150") int total,
  @Schema(description = "현재 페이지 번호 (1부터 시작)", example = "1") int page,
  @Schema(description = "페이지당 항목 수", example = "20") int size,
  @Schema(description = "데이터 업데이트 시간 (한국 시간 기준, yyyy-MM-dd HH:mm:ss)", example = "2026-08-29 14:30:00")
  String updatedAt
) {

  @Schema(description = "청약 공고 요약 정보")
  public record ComplexSummary(
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
    @Schema(description = "입주예정월 (YYYY년MM월 또는 YYYYMM)", example = "2028년06월")
    String expectedMoveIn,
    @Schema(description = "대표 분양가 (첫 번째 주택형 기준, 만원 단위). 주택형 정보가 미등록된 공고이면 null", example = "120000")
    Integer salePrice,
    @Schema(description = "판정 가능 여부 (현재 항상 true)")
    boolean isJudgeable
  ) {
  }
}