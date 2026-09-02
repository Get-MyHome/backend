package aichallenge.getmyhome.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
      .info(new Info()
        .title("GetMyHome API")
        .description("""
            ## 청약 판정 서비스 API

            ### 사용 흐름

            ```
            1. 공고 목록 조회  →  GET /complexes
            2. 공고 상세 조회  →  GET /complexes/{complexId}
            3. 대출 자격 조회  →  POST /financing-routes
               └ conditionToken 발급됨
            4-A. 조건 매칭 공고 조회  →  POST /complexes/matched (conditionToken 사용)
            4-B. 청약 판정 실행       →  POST /verdicts (conditionToken 또는 user 사용)
            5. 판정 결과 이메일 발송  →  POST /verdicts/{verdictId}/email
            ```

            ### 공통 응답 형식

            **성공** (HTTP 200)
            ```json
            { "success_code": "SUCCESS", "message": "요청에 성공했습니다.", "data": { ... } }
            ```

            **실패** (HTTP 4xx/5xx)
            ```json
            { "error_code": "VERDICT_001", "message": "...", "retryable": false }
            ```

            ### 금액 단위
            모든 금액 필드는 **만 원 정수** 입니다. (예: 40000 = 4억 원)

            ### 판정 상태 (VerdictStatus)
            | 값 | 의미 |
            |---|---|
            | `OK` | 자격 충족 / 자금 충분 |
            | `GAP` | 부족하지만 저축으로 해소 가능 |
            | `BLOCK` | 현재 조건으로는 불가 |
            | `HOLD` | 추가 정보 입력 필요 (판정 보류) |

            ### HOLD 종류 구분
            `holds` 배열에는 두 가지 출처의 HOLD가 함께 포함됩니다.
            | kind | 의미 | blocking |
            |---|---|---|
            | `null` | 백엔드 판정 HOLD — 추가 입력 필요 | 차단 |
            | `DOCUMENT_UNCERTAINTY` | AI 분석 HOLD — 공고문에서 확인 불가 | `true` (차단) |
            | `PERSONAL_REVIEW` | AI 분석 안내 — 금융기관 개인심사 필요 | `false` (참고) |

            ### AI 분석 검수 상태 (analysisReviewStatus)
            | 값 | 의미 |
            |---|---|
            | `AUTO_EXTRACTED` | 자동 추출 — 주택형 최고가 기준 보수적 판정 |
            | `REVIEWED` | 사람 검수 완료 |
            | `null` | AI 분석 없음 (단지 미선택 또는 분석 실패) |

            ### 빈 배열 응답 안내
            `verdicts`(구간별 판정)와 `route_comparisons`(대출 경로별 잔금 비교)가 빈 배열(`[]`)로 \
            반환될 수 있습니다. 이는 공고문 PDF 분석이 완료되지 않아 분양가 정보를 알 수 없는 경우입니다. \
            `holds` 배열의 `CRAWLER_FAILED` 또는 `COMPLEX_NOT_ANALYZED` 사유를 확인해 주세요.
            """)
        .version("v1"))
      .tags(List.of(
        new Tag().name("청약 공고").description("청약 공고 목록 조회 및 상세 조회"),
        new Tag().name("대출 자격 조회").description("사용자 조건 기반 6개 대출 상품 자격·한도·탈락 사유 조회"),
        new Tag().name("청약 공고 매칭").description("사용자 조건에 맞는 대출 가능 공고 필터링"),
        new Tag().name("청약 판정").description("자금 경로 + 청약 자격 + 구간(계약금/중도금/잔금) 종합 판정")
      ));
  }
}
