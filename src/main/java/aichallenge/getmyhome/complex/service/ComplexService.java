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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexService {

    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplyhomeApiClient applyhomeApiClient;
    private final CacheManager cacheManager;
    private final FinancingRouteService financingRouteService;
    private final RuleProperties ruleProperties;

    // ── 캐시 워밍업 ──

    private static final int WARMUP_PAGE_SIZE = 20;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        try {
            log.info("공고 캐시 워밍업 시작");
            List<AptDetailData> data = getCachedComplexData();
            log.info("공고 목록 캐시 완료: {}건", data.size());

            // 지역별 첫 1페이지분의 분양가(MDL)를 미리 로드
            List<AptDetailData> warmupTarget = data.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            d -> d.subscrptAreaCodeNm() != null ? d.subscrptAreaCodeNm() : ""))
                    .values().stream()
                    .flatMap(list -> list.stream().limit(WARMUP_PAGE_SIZE))
                    .toList();

            Map<String, List<AptDetailMdlData>> mdlMap = fetchMdlData(warmupTarget);
            String updatedAt = LocalDateTime.now().format(KST_FORMATTER);
            for (AptDetailData d : warmupTarget) {
                List<AptDetailMdlData> mdlList = mdlMap.getOrDefault(d.houseManageNo(), List.of());
                preCacheDetail(d, mdlList, updatedAt);
            }
            log.info("분양가 캐시 워밍업 완료: {}건 ({}개 지역)", warmupTarget.size(),
                    data.stream().map(AptDetailData::subscrptAreaCodeNm).distinct().count());
        } catch (Exception e) {
            log.warn("공고 캐시 워밍업 실패 — 첫 요청 시 로드됩니다", e);
        }
    }

    // ── 캐시 초기화 ──

    public void evictAllComplexCaches() {
        evictCache("complexList");
        evictCache("complexDetail");
    }

    private void evictCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }

    /** 상세 캐시에서 분양가만 꺼낸다. 캐시 miss 시 null 반환. */
    private Integer getCachedSalePrice(String complexId) {
        Cache cache = cacheManager.getCache("complexDetail");
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(complexId);
        if (wrapper == null) return null;
        ComplexDetailResponse detail = (ComplexDetailResponse) wrapper.get();
        return detail != null ? detail.salePrice() : null;
    }

    // ── 공고 목록/상세 ──

    public ComplexListResponse getComplexes(String region, HouseCategory houseCategory,
                                            int page, int size) {
        List<AptDetailData> allData = getCachedComplexData();

        // 메모리에서 필터링
        List<AptDetailData> filtered = filterComplexData(allData, region, houseCategory);

        // 메모리에서 페이지네이션
        int total = filtered.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<AptDetailData> pageData = filtered.subList(fromIndex, toIndex);

        // 캐시에 없는 공고만 MDL 조회
        List<AptDetailData> uncached = pageData.stream()
                .filter(data -> getCachedSalePrice(data.houseManageNo()) == null)
                .toList();

        Map<String, List<AptDetailMdlData>> mdlMap = uncached.isEmpty()
                ? Map.of() : fetchMdlData(uncached);
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        List<ComplexSummary> pageItems = pageData.stream()
                .map(data -> {
                    // 1. 상세 캐시에서 분양가 조회
                    Integer salePrice = getCachedSalePrice(data.houseManageNo());
                    if (salePrice != null) {
                        return toSummary(data, salePrice);
                    }
                    // 2. 캐시 miss → MDL 결과에서 분양가 추출
                    List<AptDetailMdlData> mdlList = mdlMap.getOrDefault(data.houseManageNo(), List.of());
                    salePrice = mdlList.isEmpty() ? null : parseSalePrice(mdlList.get(0).lttotTopAmount());
                    preCacheDetail(data, mdlList, updatedAt);
                    return toSummary(data, salePrice);
                })
                .toList();

        return new ComplexListResponse(pageItems, total, page, size, updatedAt);
    }

    /**
     * 전체 공고 원본 데이터를 1회 조회 후 캐시.
     * 이후 필터링·페이지네이션·MDL 조회는 요청 시점에 처리한다.
     */
    @Cacheable(value = "complexList", key = "'ALL'")
    public List<AptDetailData> getCachedComplexData() {
        return fetchAllComplexData(null, null);
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

        // 1단계: 캐시된 전체 공고에서 필터링 (API 호출 없음)
        List<AptDetailData> allData = getCachedComplexData();
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);

        List<AptDetailData> filtered = filterComplexData(allData, region, houseCategory);

        // 2단계: 대출 매칭 필터링 (salePrice 없이 — 목록에서는 MDL 미조회)
        List<ComplexSummary> allMatched = new ArrayList<>();
        for (AptDetailData data : filtered) {
            List<FinancingRouteDetailResponse> routes =
                financingRouteService.evaluateWithReasons(user, null, rule);

            List<String> matchedNames = routes.stream()
                .filter(r -> r.status() == VerdictStatus.OK || r.status() == VerdictStatus.HOLD)
                .map(FinancingRouteDetailResponse::productName)
                .toList();

            if (matchedNames.isEmpty()) continue;

            allMatched.add(toSummary(data, null, matchedNames));
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

    private List<AptDetailData> filterComplexData(List<AptDetailData> allData,
                                                    String region, HouseCategory houseCategory) {
        String normalizedRegion = normalizeRegion(region);
        return allData.stream()
                .filter(data -> normalizedRegion == null
                        || normalizedRegion.equals(data.subscrptAreaCodeNm()))
                .filter(data -> houseCategory == null
                        || houseCategory.getHouseDtlSecd().equals(data.houseDtlSecd()))
                .toList();
    }

    /** 프론트에서 '경기도'로 전달 → API 데이터의 '경기'와 매칭 */
    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) return null;
        if ("경기도".equals(region)) return "경기";
        return region;
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice) {
        return toSummary(data, salePrice, null);
    }

    private ComplexSummary toSummary(AptDetailData data, Integer salePrice, List<String> matchedProductNames) {
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
                matchedProductNames
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