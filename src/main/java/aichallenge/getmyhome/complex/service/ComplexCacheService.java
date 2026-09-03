package aichallenge.getmyhome.complex.service;

import aichallenge.getmyhome.complex.client.ApplyhomeApiClient;
import aichallenge.getmyhome.complex.client.dto.ApplyhomeApiResponse;
import aichallenge.getmyhome.complex.client.dto.AptDetailData;
import aichallenge.getmyhome.complex.client.dto.AptDetailMdlData;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse.UnitType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexCacheService {

    private static final int FETCH_PAGE_SIZE = 100;
    private static final long MDL_CALL_DELAY_MS = 200;
    private static final List<String> EXCLUDED_HOUSE_MANAGE_NOS = List.of();
    private static final DateTimeFormatter KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplyhomeApiClient applyhomeApiClient;
    private final CacheManager cacheManager;

    @Cacheable(value = "complexList", key = "'ALL'")
    public List<AptDetailData> getCachedComplexData() {
        return fetchAllComplexData();
    }

    /** 서버 기동 시 공고 목록 + 분양가 캐시 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        try {
            log.info("캐시 워밍업 시작");
            List<AptDetailData> data = refreshComplexListCache();
            warmUpDetailCache(data);
        } catch (Exception e) {
            log.warn("캐시 워밍업 실패 — 첫 요청 시 로드됩니다", e);
        }
    }

    /** 24시간마다 캐시 갱신 — 새 데이터를 먼저 조회한 뒤 캐시 교체 */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000, initialDelay = 24 * 60 * 60 * 1000)
    public void scheduledRefresh() {
        try {
            log.info("캐시 갱신 스케줄 시작");
            List<AptDetailData> data = refreshComplexListCache();
            warmUpDetailCache(data);
            log.info("캐시 갱신 스케줄 완료");
        } catch (Exception e) {
            log.warn("캐시 갱신 실패 — 기존 캐시 유지", e);
        }
    }

    /** 공고 목록 캐시 갱신 */
    private List<AptDetailData> refreshComplexListCache() {
        List<AptDetailData> freshData = fetchAllComplexData();
        Cache cache = cacheManager.getCache("complexList");
        if (cache != null) {
            cache.put("ALL", freshData);
        }
        log.info("공고 목록 캐시 완료: {}건", freshData.size());
        return freshData;
    }

    /** 전체 공고의 분양가(MDL)를 순차 조회하여 캐시에 미리 저장 */
    private void warmUpDetailCache(List<AptDetailData> allData) {
        log.info("분양가 캐시 워밍업 시작: {}건", allData.size());
        Map<String, List<AptDetailMdlData>> mdlMap = fetchMdlData(allData);
        String updatedAt = LocalDateTime.now().format(KST_FORMATTER);
        for (AptDetailData d : allData) {
            preCacheDetail(d, mdlMap.getOrDefault(d.houseManageNo(), List.of()), updatedAt);
        }
        log.info("분양가 캐시 워밍업 완료: {}건", allData.size());
    }

    // ── ComplexService에서도 사용하는 공용 메서드 ──

    public Map<String, List<AptDetailMdlData>> fetchMdlData(List<AptDetailData> dataList) {
        Map<String, List<AptDetailMdlData>> result = new HashMap<>();

        for (AptDetailData data : dataList) {
            try {
                ApplyhomeApiResponse<AptDetailMdlData> mdlResponse =
                        applyhomeApiClient.getAptDetailMdl(1, 100, data.houseManageNo(), data.pblancNo());
                result.put(data.houseManageNo(), safeData(mdlResponse));
            } catch (Exception e) {
                log.warn("MDL 조회 실패: houseManageNo={}, error={}", data.houseManageNo(), e.getMessage());
                result.put(data.houseManageNo(), List.of());
            }

            try {
                Thread.sleep(MDL_CALL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return result;
    }

    public void preCacheDetail(AptDetailData data, List<AptDetailMdlData> mdlList, String updatedAt) {
        Cache cache = cacheManager.getCache("complexDetail");
        if (cache == null || cache.get(data.houseManageNo()) != null) {
            return;
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

    public ComplexDetailResponse buildDetailResponse(AptDetailData data, List<AptDetailMdlData> mdlList) {
        List<UnitType> unitTypes = mdlList.stream()
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
    }

    public Integer getCachedSalePrice(String complexId) {
        Cache cache = cacheManager.getCache("complexDetail");
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(complexId);
        if (wrapper == null) return null;
        ComplexDetailResponse detail = (ComplexDetailResponse) wrapper.get();
        return detail != null ? detail.salePrice() : null;
    }

    // ── 내부 유틸 ──

    public Integer parseSalePrice(String lttotTopAmount) {
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

    private String mapRegulationZone(AptDetailData data) {
        if ("Y".equals(data.specltRdnEarthAt())) return "투기과열지구";
        if ("Y".equals(data.parcprcUlsAt())) return "분양가상한제";
        return null;
    }

    private <T> List<T> safeData(ApplyhomeApiResponse<T> response) {
        return response != null && response.data() != null ? response.data() : List.of();
    }

    private List<AptDetailData> fetchAllComplexData() {
        // 넉넉하게 최근 60일치를 가져온 뒤, 가장 최근 마감 공고 기준 30일 이내로 필터링
        String since = LocalDate.now().minusDays(60).toString();

        ApplyhomeApiResponse<AptDetailData> firstResponse =
                applyhomeApiClient.getAptDetail(1, FETCH_PAGE_SIZE, null, null, null, null,
                        null, null, null, null, null, since, null);

        List<AptDetailData> result = new ArrayList<>(safeData(firstResponse));

        int total = firstResponse.matchCount();
        log.info("공공데이터 API 최근 60일 공고 수: {}건", total);
        for (int pg = 2; pg * FETCH_PAGE_SIZE - FETCH_PAGE_SIZE < total; pg++) {
            ApplyhomeApiResponse<AptDetailData> nextResponse =
                    applyhomeApiClient.getAptDetail(pg, FETCH_PAGE_SIZE, null, null, null, null,
                            null, null, null, null, null, since, null);
            result.addAll(safeData(nextResponse));
        }

        // 제외 대상 필터링
        result = result.stream()
                .filter(d -> !EXCLUDED_HOUSE_MANAGE_NOS.contains(d.houseManageNo()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // 가장 최근 마감된 공고의 접수종료일 찾기
        LocalDate today = LocalDate.now();
        LocalDate latestClosedDate = result.stream()
                .map(d -> parseDate(d.rceptEndde()))
                .filter(d -> d != null && d.isBefore(today))
                .max(LocalDate::compareTo)
                .orElse(today);

        // 최근 마감일 - 30일 이후의 공고만 유지
        LocalDate cutoff = latestClosedDate.minusDays(30);
        List<AptDetailData> filtered = result.stream()
                .filter(d -> {
                    LocalDate endDate = parseDate(d.rceptEndde());
                    return endDate != null && !endDate.isBefore(cutoff);
                })
                .sorted((a, b) -> {
                    LocalDate dateA = parseDate(a.rceptEndde());
                    LocalDate dateB = parseDate(b.rceptEndde());
                    if (dateA == null && dateB == null) return 0;
                    if (dateA == null) return 1;
                    if (dateB == null) return -1;
                    return dateB.compareTo(dateA);
                })
                .toList();

        log.info("최근 마감 공고일: {}, 기준일(−30일): {}, 필터링 후 공고 수: {}건",
                latestClosedDate, cutoff, filtered.size());
        return filtered;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}