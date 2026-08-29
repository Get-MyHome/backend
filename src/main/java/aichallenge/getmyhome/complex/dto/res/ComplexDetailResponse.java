package aichallenge.getmyhome.complex.dto.res;

import java.util.List;

/**
 * GET /complexes/{complexId} 응답 — 청약 공고 상세
 */
public record ComplexDetailResponse(
  String complexId,
  String name,
  String houseType,
  String region,
  String address,
  String announcementDate,
  String applicationEndDate,
  Integer salePrice,
  List<UnitType> unitTypes,
  String regulationZone,
  String sourceUrl
) {

  public record UnitType(
    String unitTypeId,
    String type,
    Integer salePrice,
    String supplyArea
  ) {
  }
}