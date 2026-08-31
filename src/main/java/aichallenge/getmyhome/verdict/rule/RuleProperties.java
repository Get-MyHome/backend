package aichallenge.getmyhome.verdict.rule;

import aichallenge.getmyhome.global.exception.BaseException;
import aichallenge.getmyhome.verdict.exception.VerdictErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/** verdict.rules 하위의 전체 규칙 설정을 바인딩 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "verdict.rules")
public class RuleProperties {

  private String defaultVersion;          // rule_version 미지정 시 사용할 기본 버전
  private Map<String, RuleVersion> versions;  // 버전별 규칙 파라미터 세트

  /**
   * rule_version으로 해당 버전의 규칙 세트를 조회.
   * null이면 defaultVersion 사용.
   * 존재하지 않는 버전이면 예외.
   */
  public RuleVersion resolve(String ruleVersion) {
    String version = ruleVersion != null ? ruleVersion : defaultVersion;
    RuleVersion rule = versions.get(version);
    if (rule == null) {
      throw BaseException.of(VerdictErrorCode.INVALID_RULE_VERSION,
              "지원하지 않는 규칙 버전입니다: " + version);
    }
    return rule;
  }
}