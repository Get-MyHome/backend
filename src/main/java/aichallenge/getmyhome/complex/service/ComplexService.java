package aichallenge.getmyhome.complex.service;

import aichallenge.getmyhome.complex.client.ApplyhomeApiClient;
import aichallenge.getmyhome.complex.client.dto.ApplyhomeApiResponse;
import aichallenge.getmyhome.complex.client.dto.AptDetailData;
import aichallenge.getmyhome.complex.client.dto.AptDetailMdlData;
import aichallenge.getmyhome.complex.enums.HouseCategory;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse.*;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse.ComplexSummary;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.global.exception.GlobalErrorCode;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;
import aichallenge.getmyhome.verdict.dto.req.UserConditionRequest;
import aichallenge.getmyhome.verdict.dto.res.FinancingRouteDetailResponse;
import aichallenge.getmyhome.verdict.enums.VerdictStatus;
import aichallenge.getmyhome.verdict.rule.RuleProperties;
import aichallenge.getmyhome.verdict.rule.RuleVersion;
import aichallenge.getmyhome.verdict.service.FinancingRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 청약 공고 통합 서비스
 */
@Service
@RequiredArgsConstructor
public class ComplexService {

    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplyhomeApiClient applyhomeApiClient;
    private final CacheManager cacheManager;
    private final FinancingRouteService financingRouteService;
    private final RuleProperties ruleProperties;

    // ── 캐시 초기화 ──

    public void evictAllComplexCaches() {
        evictCache("complexList");
        evictCache("complexDetail");
    }

    private void evictCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }

    // ── 공고 목록/상세 ──

    public ComplexListResponse getComplexes(String region, HouseCategory houseCategory,
                                            int page, int size) {
        List<ComplexSummary> allItems = getCachedComplexSummaries();

        // 메모리에서 필터링
        List<ComplexSummary> filtered = allItems.stream()
                .filter(item -> region == null || region.isBlank() || region.equals(item.region()))
                .filter(item -> houseCategory == null || houseCategory.getDisplayName().equals(item.houseType()))
                .toList();

        // 메모리에서 페이지네이션
        int total = filtered.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<ComplexSummary> pageItems = filtered.subList(fromIndex, toIndex);

        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);
        return new ComplexListResponse(pageItems, total, page, size, updatedAt);
    }

    /**
     * 전체 공고 목록을 1회 조회 후 캐시.
     * 이후 필터링·페이지네이션은 메모리에서 처리한다.
     */
    @Cacheable(value = "complexList", key = "'ALL'")
    public List<ComplexSummary> getCachedComplexSummaries() {
        List<AptDetailData> allData = fetchAllComplexData(null, null);

        Map<String, List<AptDetailMdlData>> mdlMap = fetchMdlData(allData);
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        return allData.stream()
                .map(data -> {
                    List<AptDetailMdlData> mdlList = mdlMap.getOrDefault(data.houseManageNo(), List.of());
                    Integer salePrice = mdlList.isEmpty() ? null : parseSalePrice(mdlList.get(0).lttotTopAmount());

                    preCacheDetail(data, mdlList, updatedAt);

                    return toSummary(data, salePrice);
                })
                .toList();
    }

    @Cacheable(value = "complexDetail", key = "#complexId")
    public ComplexDetailResponse getComplexDetail(String complexId) {
        // 1. 청약홈 API — 기본 공고 정보
        ApplyhomeApiResponse<AptDetailData> detailResponse =
                applyhomeApiClient.getAptDetail(1, 1, complexId, null, null, null, null, null, null,
                        null, null, null, null);

        List<AptDetailData> detailData = safeData(detailResponse);
        if (detailData.isEmpty()) {
            throw BaseException.of(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR, "해당 공고를 찾을 수 없습니다.");
        }

        AptDetailData detail = detailData.get(0);

        // 2. 청약홈 API — 주택형(평형) 정보
        ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                applyhomeApiClient.getAptDetailMdl(1, 100, complexId, detail.pblancNo());

        List<UnitType> unitTypes = safeData(mdlResponse).stream()
                .map(mdl -> new UnitType(
                        mdl.modelNo(),
                        mdl.houseTy(),
                        parseSalePrice(mdl.lttotTopAmount()),
                        mdl.suplyAr()
                ))
                .toList();

        Integer representativeSalePrice = unitTypes.isEmpty() ? null : unitTypes.get(0).salePrice();
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        return new ComplexDetailResponse(
                detail.houseManageNo(),
                detail.houseNm(),
                detail.houseDtlSecdNm(),
                detail.subscrptAreaCodeNm(),
                detail.hssplyAdres(),
                detail.rcritPblancDe(),
                detail.rceptEndde(),
                representativeSalePrice,
                unitTypes,
                mapRegulationZone(detail),
                buildSourceUrl(detail),
                updatedAt
        );
    }

    // ── 대출 매칭 공고 조회 ──

    private static final int MATCHED_FETCH_SIZE = 100;

    public ComplexListResponse getMatchedComplexes(String conditionToken,
                                                    UserConditionRequest userDirect,
                                                    String region, HouseCategory houseCategory,
                                                    int page, int size) {
        // 토큰 우선, 없으면 직접 전달된 user 사용
        UserConditionRequest user = null;
        if (conditionToken != null && !conditionToken.isBlank()) {
            user = financingRouteService.getCondition(conditionToken);
            if (user == null) {
                throw BaseException.of(VerdictErrorCode.CONDITION_TOKEN_EXPIRED);
            }
        } else {
            user = userDirect;
        }
        if (user == null) {
            throw BaseException.of(GlobalErrorCode.BAD_REQUEST,
                "conditionToken 또는 user 중 하나는 필수입니다.");
        }

        RuleVersion rule = ruleProperties.resolve(null);

        // 1단계: 청약홈 API 1회 호출로 전체 공고 조회 (MDL 조회 없이)
        List<AptDetailData> allData = fetchAllComplexData(region, houseCategory);
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        // 2단계: 대출 매칭 필터링 (salePrice 없이 — 주택가격 상한 체크 건너뜀)
        List<ComplexSummary> allMatched = new ArrayList<>();
        for (AptDetailData data : allData) {
            List<FinancingRouteDetailResponse> routes =
                financingRouteService.evaluateWithReasons(user, null, rule);

            List<String> matchedNames = routes.stream()
                .filter(r -> r.status() == VerdictStatus.OK || r.status() == VerdictStatus.HOLD)
                .map(FinancingRouteDetailResponse::productName)
                .toList();

            if (matchedNames.isEmpty()) continue;

            allMatched.add(new ComplexSummary(
                data.houseManageNo(), data.houseNm(), data.houseDtlSecdNm(),
                data.subscrptAreaCodeNm(), data.hssplyAdres(),
                data.rcritPblancDe(), data.rceptEndde(), data.mvnPrearngeYm(),
                null, true, matchedNames
            ));
        }

        // 3단계: 직접 페이지네이션
        int matchedTotal = allMatched.size();
        int fromIndex = Math.min((page - 1) * size, matchedTotal);
        int toIndex = Math.min(fromIndex + size, matchedTotal);
        List<ComplexSummary> pageItems = allMatched.subList(fromIndex, toIndex);

        return new ComplexListResponse(pageItems, matchedTotal, page, size, updatedAt);
    }

    /**
     * 매칭용 공고 데이터 조회 — MDL 없이 청약홈 API 1회 호출.
     * 전체 건수가 MATCHED_FETCH_SIZE를 초과하면 페이징으로 추가 조회한다.
     */
    private List<AptDetailData> fetchAllComplexData(String region, HouseCategory houseCategory) {
        String normalizedRegion = (region != null && !region.isBlank()) ? region : null;
        String houseDtlSecd = houseCategory != null ? houseCategory.getHouseDtlSecd() : null;

        ApplyhomeApiResponse<AptDetailData> firstResponse =
                applyhomeApiClient.getAptDetail(1, MATCHED_FETCH_SIZE, null, null, null, houseDtlSecd,
                        normalizedRegion, null, null, null, null, null, null);

        List<AptDetailData> result = new ArrayList<>(safeData(firstResponse));

        // 100건 초과 시 추가 페이지 조회
        int total = firstResponse.matchCount();
        for (int pg = 2; pg * MATCHED_FETCH_SIZE - MATCHED_FETCH_SIZE < total; pg++) {
            ApplyhomeApiResponse<AptDetailData> nextResponse =
                    applyhomeApiClient.getAptDetail(pg, MATCHED_FETCH_SIZE, null, null, null, houseDtlSecd,
                            normalizedRegion, null, null, null, null, null, null);
            result.addAll(safeData(nextResponse));
        }

        return result;
    }

    // ── 내부 유틸 ──

    private Map<String, List<AptDetailMdlData>> fetchMdlData(List<AptDetailData> dataList) {
        Map<String, CompletableFuture<List<AptDetailMdlData>>> futures = new java.util.HashMap<>();

        for (AptDetailData data : dataList) {
            futures.put(data.houseManageNo(), CompletableFuture.supplyAsync(() -> {
                try {
                    ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                            applyhomeApiClient.getAptDetailMdl(1, 100, data.houseManageNo(), data.pblancNo());
                    return safeData(mdlResponse);
                } catch (Exception e) {
                    return List.of();
                }
            }));
        }

        Map<String, List<AptDetailMdlData>> result = new java.util.HashMap<>();
        futures.forEach((key, future) -> result.put(key, future.join()));
        return result;
    }

    private void preCacheDetail(AptDetailData data, List<AptDetailMdlData> mdlList, String updatedAt) {
        Cache cache = cacheManager.getCache("complexDetail");
        if (cache == null || cache.get(data.houseManageNo()) != null) {
            return; // 이미 캐시에 있으면 덮어쓰지 않음
        }

        List<UnitType> unitTypes = mdlList.stream()
                .map(mdl -> new UnitType(
                        mdl.modelNo(),
                        mdl.houseTy(),
                        parseSalePrice(mdl.lttotTopAmount()),
                        mdl.suplyAr()
                ))
                .toList();

        Integer representativeSalePrice = unitTypes.isEmpty() ? null : unitTypes.get(0).salePrice();

        ComplexDetailResponse detail = new ComplexDetailResponse(
                data.houseManageNo(),
                data.houseNm(),
                data.houseDtlSecdNm(),
                data.subscrptAreaCodeNm(),
                data.hssplyAdres(),
                data.rcritPblancDe(),
                data.rceptEndde(),
                representativeSalePrice,
                unitTypes,
                mapRegulationZone(data),
                buildSourceUrl(data),
                updatedAt
        );

        cache.put(data.houseManageNo(), detail);
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice) {
        return new ComplexSummary(
                data.houseManageNo(),
                data.houseNm(),
                data.houseDtlSecdNm(),
                data.subscrptAreaCodeNm(),
                data.hssplyAdres(),
                data.rcritPblancDe(),
                data.rceptEndde(),
                data.mvnPrearngeYm(),
                salePrice,
                true,
                null
        );
    }

    private Integer parseSalePrice(String lttotTopAmount) {
        if (lttotTopAmount == null || lttotTopAmount.isBlank()) return null;
        try {
            return Integer.parseInt(lttotTopAmount.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildSourceUrl(AptDetailData data) {
        String url = data.pblancUrl();
        if (url != null && !url.isBlank() && !url.equals("https://www.applyhome.co.kr")) {
            return url;
        }
        return "https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancDetail.do"
                + "?houseManageNo=" + data.houseManageNo()
                + "&pblancNo=" + data.pblancNo();
    }

    private <T> List<T> safeData(ApplyhomeApiResponse<T> response) {
        return response.data() != null ? response.data() : List.of();
    }

    private String mapRegulationZone(AptDetailData data) {
        if ("Y".equals(data.specltRdnEarthAt())) return "투기과열지구";
        if ("Y".equals(data.parcprcUlsAt())) return "분양가상한제";
        return null;
    }
}