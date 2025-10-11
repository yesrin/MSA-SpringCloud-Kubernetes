# Circuit Breaker 실무 Q&A

> 작성일: 2025-10-11
> MSA 프로젝트에서 Circuit Breaker를 직접 구현하고 테스트하며 학습한 내용입니다.

---

## Q1. Circuit Breaker를 왜 도입했나요?

**A:** MSA 환경에서 한 서비스의 장애가 전체 시스템으로 전파되는 **연쇄 장애(Cascade Failure)**를 방지하기 위해 도입했습니다.

**실제 시나리오:**
- Order Service가 다운되면 User Service도 영향을 받을까?
- 답: Circuit Breaker 없으면 영향받음, 있으면 **격리됨**

**검증 방법:**
```bash
# Order Service 중지
docker-compose stop order-service

# User Service는 정상 동작 확인 ✅
curl http://localhost:8080/api/users  # 200 OK

# Order Service는 Fallback 응답 ✅
curl http://localhost:8080/api/orders # 503 + 친절한 메시지
```

**핵심 가치:** 부분 장애가 전체 장애로 확산되지 않도록 **서비스 격리**

---

## Q2. Circuit Breaker의 3가지 상태와 전환 조건을 설명해주세요.

**A:** CLOSED(정상) → OPEN(차단) → HALF_OPEN(테스트) 순으로 전환됩니다.

### 상태별 동작

**1. CLOSED (정상 상태)**
- 모든 요청 정상 처리
- 실패율 모니터링 중
- **전환 조건:** 실패율 50% 초과 시 → OPEN

**2. OPEN (차단 상태)**
- 모든 요청 즉시 차단, Fallback 반환
- 장애 서비스에 부하를 주지 않음
- **전환 조건:** 10초 대기 후 → HALF_OPEN

**3. HALF_OPEN (회복 테스트)**
- 제한된 수(3개)의 요청만 허용
- 서비스 복구 여부 테스트
- **전환 조건:**
  - 모두 성공 → CLOSED (복구 완료)
  - 하나라도 실패 → OPEN (재차단)

**실제 로그로 확인:**
```log
STATE_TRANSITION: CLOSED to OPEN (실패율 50% 초과)
STATE_TRANSITION: OPEN to HALF_OPEN (10초 경과)
STATE_TRANSITION: HALF_OPEN to CLOSED (복구 성공)
```

---

## Q3. Fallback 처리는 어떻게 구현했나요?

**A:** Spring Cloud Gateway의 `forward:` 방식으로 **Gateway 내부 Controller**를 호출하도록 구현했습니다.

**Config Server 설정:**
```yaml
# config-server/src/main/resources/config/api-gateway.yml
filters:
  - name: CircuitBreaker
    args:
      name: orderServiceCircuitBreaker
      fallbackUri: forward:/fallback/order-service
```

**FallbackController 구현:**
```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "order-service");
        response.put("message", "Order 서비스가 일시적으로 사용 불가능합니다.");
        response.put("status", "SERVICE_UNAVAILABLE");
        return ResponseEntity.status(503).body(response);
    }
}
```

**`forward:`의 장점:**
- 외부 HTTP 호출 없음 → **지연 최소화**
- 추가 실패 가능성 없음 → **안정성 보장**
- Gateway 내부 완결 → **단순한 아키텍처**

---

## Q4. Circuit Breaker 설정값은 어떻게 결정했나요?

**A:** 테스트 환경 특성을 고려하여 빠른 피드백이 가능하도록 설정했습니다.

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10              # 최근 10개 요청 기준
        minimumNumberOfCalls: 5            # 최소 5번 호출 후 판단
        failureRateThreshold: 50           # 실패율 50% 이상
        waitDurationInOpenState: 10000     # OPEN 상태 10초 유지
        permittedNumberOfCallsInHalfOpenState: 3
```

**설정 이유:**

| 설정 | 값 | 이유 |
|------|-----|------|
| `slidingWindowSize` | 10 | 작은 샘플로 빠른 감지 (테스트 환경) |
| `minimumNumberOfCalls` | 5 | 초기 트래픽 적어도 동작 가능 |
| `failureRateThreshold` | 50% | 절반 이상 실패 시 차단 (보수적) |
| `waitDurationInOpenState` | 10초 | 짧은 복구 대기 (빠른 테스트) |

**운영 환경에서는?**
- `slidingWindowSize`: 100 (더 많은 샘플)
- `minimumNumberOfCalls`: 10 (충분한 데이터 확보)
- `waitDurationInOpenState`: 60초 (여유있는 복구 시간)

---

## Q5. 실제로 장애 상황을 테스트한 결과는?

**A:** Order Service를 중지하고 다음을 확인했습니다:

### 테스트 시나리오
```bash
# 1. 정상 상태 확인
curl http://localhost:8080/api/orders  # 200 OK

# 2. Order Service 중지
docker-compose stop order-service

# 3. 여러 번 호출 (Circuit Breaker 활성화)
for i in {1..6}; do curl http://localhost:8080/api/orders; done
```

### 관찰 결과

**1차 호출 (CLOSED 상태):**
```log
WARN: No servers available for service: order-service
Event ERROR published
→ Fallback 응답 반환 (503)
```

**5-6차 호출 (임계값 도달):**
```log
Event FAILURE_RATE_EXCEEDED: Current failure rate: 50.0
STATE_TRANSITION: CLOSED to OPEN
```

**이후 호출 (OPEN 상태):**
- 서비스 호출 시도조차 하지 않음
- 즉시 Fallback 응답 반환
- **응답 시간 단축** (실패 대기 시간 없음)

**핵심 발견:** Circuit Breaker가 OPEN되면 불필요한 호출을 막아 **전체 시스템 부하 감소**

---

## Q6. User Service는 왜 영향을 받지 않았나요?

**A:** **서비스별로 독립적인 Circuit Breaker 인스턴스**를 사용하기 때문입니다.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      orderServiceCircuitBreaker:  # Order 전용
        baseConfig: default
      userServiceCircuitBreaker:   # User 전용
        baseConfig: default
```

**실제 확인:**
```bash
# Order Service 죽은 상태에서
curl http://localhost:8080/api/orders  # ❌ Fallback
curl http://localhost:8080/api/users   # ✅ 정상

# User Service Circuit Breaker 상태: CLOSED 유지
# Order Service Circuit Breaker 상태: OPEN
```

**이것이 MSA의 핵심 장점:** Fault Isolation (장애 격리)

---

## Q7. Config Server에서 설정을 관리하는 이유는?

**A:** 중앙 집중식 설정 관리로 **코드 재배포 없이 설정 변경**이 가능합니다.

**핵심 장점:**
- 모든 서비스 설정을 한 곳(`config-server/src/main/resources/config/`)에서 관리
- Circuit Breaker 임계값 조정 시 Config Server만 재시작
- 환경별 설정 분리 (Docker, Kubernetes 등)

---

## Q8. 로그만 보고 Circuit Breaker 상태를 파악하는 방법은?

**A:** 특정 키워드로 grep하여 상태 변화를 추적합니다.

```bash
# 상태 전환 추적
docker-compose logs api-gateway | grep "STATE_TRANSITION"

# 실패율 초과 확인
docker-compose logs api-gateway | grep "FAILURE_RATE_EXCEEDED"

# 에러 발생 추적
docker-compose logs api-gateway | grep "Event ERROR"

# 특정 서비스 Circuit Breaker만 보기
docker-compose logs api-gateway | grep "orderServiceCircuitBreaker"
```

**실시간 모니터링:**
```bash
# 실시간 로그 + 필터링
docker-compose logs -f api-gateway | grep -E "circuit|STATE|ERROR"
```

**운영 환경에서는:**
- ELK Stack (Elasticsearch + Logstash + Kibana)
- Prometheus + Grafana
- `/actuator/circuitbreakers` 엔드포인트 모니터링

---

## Q9. Circuit Breaker가 동작하지 않는다면 어떻게 디버깅하나요?

**A:** 단계별로 확인합니다.

### 체크리스트

**1. 최소 호출 수 충족했는가?**
```yaml
minimumNumberOfCalls: 5  # 5번 이상 호출했나?
```
```bash
# 충분히 호출
for i in {1..10}; do curl http://localhost:8080/api/orders; done
```

**2. 실패율이 임계값을 넘었는가?**
```yaml
failureRateThreshold: 50  # 50% 이상 실패했나?
```

**3. Config Server 설정이 반영되었는가?**
```bash
# Config Server 재시작
docker-compose restart config-server

# API Gateway도 재시작 (중요!)
docker-compose restart api-gateway

# 로그 확인
docker-compose logs config-server | grep "api-gateway.yml"
```

**4. Fallback 설정이 올바른가?**
```yaml
fallbackUri: forward:/fallback/order-service  # forward: 오타 없나?
```

**5. 로그 레벨 확인**
```yaml
logging:
  level:
    io.github.resilience4j: DEBUG  # DEBUG 레벨로 상세 로그
```

---

## Q10. 이 구현에서 개선할 점이 있다면?

**A:** 다음과 같은 개선이 가능합니다.

**1. Retry 패턴 추가**
```yaml
# 일시적 오류에 대한 재시도
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1000
```
- Circuit Breaker 전에 Retry로 일시적 오류 복구 시도
- 순서: Retry → Circuit Breaker → Fallback

**2. Rate Limiter 추가**
```yaml
# 과도한 트래픽 제한
resilience4j:
  ratelimiter:
    configs:
      default:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
```
- DDoS 공격이나 트래픽 급증 대응

**3. Fallback 응답 개선**
```json
{
  "service": "order-service",
  "message": "주문 서비스 점검 중입니다.",
  "retryAfter": "2025-10-11T12:00:00",  // 복구 예정 시간
  "supportContact": "support@example.com",
  "alternativeAction": "잠시 후 다시 시도하거나 고객센터로 문의해주세요."
}
```

**4. 모니터링 강화**
- Actuator Endpoints 활용
  ```bash
  curl http://localhost:8080/actuator/circuitbreakers
  curl http://localhost:8080/actuator/health
  ```
- Prometheus Metrics 노출
- Grafana Dashboard 구성

**5. 운영 환경 설정 분리**
```yaml
# 테스트: 빠른 감지
slidingWindowSize: 10
waitDurationInOpenState: 10s

# 운영: 안정적 판단
slidingWindowSize: 100
waitDurationInOpenState: 60s
```

---

## 핵심 요약 (면접 대비)

### Circuit Breaker를 한 문장으로 설명한다면?
> "장애가 발생한 서비스로의 호출을 자동으로 차단하여 연쇄 장애를 방지하고, 시스템 전체의 안정성을 높이는 패턴입니다."

### 가장 중요한 배운 점 3가지
1. **서비스 격리**: 한 서비스의 장애가 전체로 전파되지 않음
2. **Fallback의 중요성**: 사용자에게 친절한 에러 메시지 제공
3. **설정의 중요성**: 환경에 맞는 임계값 설정이 핵심

### 실무에서 바로 적용 가능한 점
- Config Server를 통한 중앙 설정 관리
- 로그 기반 모니터링 및 디버깅
- 장애 시나리오 테스트 자동화

---

## 참고 자료

- [resilience4j-patterns.md](./resilience4j-patterns.md) - 상세 구현 가이드
- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
