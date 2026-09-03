package aichallenge.getmyhome.complex.controller;

import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexDetailResponse.UnitType;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse;
import aichallenge.getmyhome.complex.dto.res.ComplexListResponse.ComplexSummary;
import aichallenge.getmyhome.complex.service.ComplexService;
import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComplexController.class)
@WithMockUser
class ComplexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplexService complexService;

    // ═══════════════════════════════════════
    // GET /complexes - 공고 목록 조회
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("GET /complexes - 목록 조회")
    class GetComplexes {

        @Test
        @DisplayName("파라미터 없이 전체 조회")
        void noParams() throws Exception {
            ComplexListResponse response = new ComplexListResponse(
                List.of(new ComplexSummary(
                    "2025000001", "래미안 원베일리", "민영", "서울",
                    "서울특별시 서초구", "2026-08-01", "2026-08-15",
                    "2028년06월", 120000, "OPEN", true, null
                )),
                1, 1, 20, "2026-09-01 14:30:00"
            );
            when(complexService.getComplexes(isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(response);

            mockMvc.perform(get("/complexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].complex_id").value("2025000001"))
                .andExpect(jsonPath("$.data.items[0].name").value("래미안 원베일리"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1));
        }

        @Test
        @DisplayName("지역 필터 적용")
        void withRegion() throws Exception {
            ComplexListResponse response = new ComplexListResponse(List.of(), 0, 1, 20, "2026-09-01 14:30:00");
            when(complexService.getComplexes(eq("서울"), isNull(), eq(1), eq(20)))
                .thenReturn(response);

            mockMvc.perform(get("/complexes").param("region", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        }

        @Test
        @DisplayName("주택 구분 필터 적용")
        void withHouseCategory() throws Exception {
            ComplexListResponse response = new ComplexListResponse(List.of(), 0, 1, 20, "2026-09-01 14:30:00");
            when(complexService.getComplexes(isNull(), any(), eq(1), eq(20)))
                .thenReturn(response);

            mockMvc.perform(get("/complexes").param("houseCategory", "PUBLIC"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("잘못된 houseCategory → 400")
        void invalidHouseCategory() throws Exception {
            mockMvc.perform(get("/complexes").param("houseCategory", "INVALID"))
                .andExpect(status().isNotFound()); // MethodArgumentTypeMismatchException → 404
        }

        @Test
        @DisplayName("페이지네이션 파라미터")
        void pagination() throws Exception {
            ComplexListResponse response = new ComplexListResponse(List.of(), 0, 2, 10, "2026-09-01 14:30:00");
            when(complexService.getComplexes(isNull(), isNull(), eq(2), eq(10)))
                .thenReturn(response);

            mockMvc.perform(get("/complexes")
                    .param("page", "2")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10));
        }
    }

    // ═══════════════════════════════════════
    // GET /complexes/{complexId} - 상세 조회
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("GET /complexes/{complexId} - 상세 조회")
    class GetComplexDetail {

        @Test
        @DisplayName("정상 조회")
        void success() throws Exception {
            ComplexDetailResponse response = new ComplexDetailResponse(
                "2025000001", "래미안 원베일리", "민영", "서울",
                "서울특별시 서초구 반포동", "2026-08-01", "2026-08-15",
                120000,
                List.of(new UnitType("01", "084.9900A", 120000, "114.78")),
                "투기과열지구",
                "https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancDetail.do?houseManageNo=2025000001&pblancNo=2025000001",
                "2026-09-01 14:30:00"
            );
            when(complexService.getComplexDetail(eq("2025000001"))).thenReturn(response);

            mockMvc.perform(get("/complexes/2025000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complex_id").value("2025000001"))
                .andExpect(jsonPath("$.data.unit_types").isArray())
                .andExpect(jsonPath("$.data.unit_types[0].unit_type_id").value("01"))
                .andExpect(jsonPath("$.data.regulation_zone").value("투기과열지구"))
                .andExpect(jsonPath("$.data.source_url").exists());
        }

        @Test
        @DisplayName("존재하지 않는 complexId → 404")
        void notFound() throws Exception {
            when(complexService.getComplexDetail(eq("nonexistent")))
                .thenThrow(BaseException.of(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR, "해당 공고를 찾을 수 없습니다."));

            mockMvc.perform(get("/complexes/nonexistent"))
                .andExpect(status().isNotFound());
        }
    }
}
