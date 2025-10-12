# Zipkin 분산 추적 (Distributed Tracing)

## 개요

### Zipkin이란?
마이크로서비스 간 **요청의 전체 흐름을 추적하고 시각화**하는 도구입니다.

```
User Request → Order Service → User Service
                [Trace ID: ABC]  [Trace ID: ABC]  ✅ 동일한 Trace로 연결
```

### 주요 기능
- 서비스 간 호출 추적
- 성능 병목 지점 파악
- 에러 발생 위치 추적
- 서비스 의존성 그래프

---

## 핵심 개념

### 1. Trace (추적)
하나의 사용자 요청 전체를 나타냅니다.

```
Trace ID: 68eb6934747c42b7
├─ Span 1: Order Service (19ms)
├─ Span 2: Order → User (Feign 호출, 5ms)
└─ Span 3: User Service (3ms)
```

### 2. Span (구간)
Trace 내에서 하나의 작업 단위입니다.

```json
{
  "traceId": "68eb6934747c42b7",
  "spanId": "237d7d4de82a0276",
  "parentSpanId": "68eb6934747c42b7",
  "name": "GET /api/users/{id}",
  "duration": 3576,
  "localEndpoint": {
    "serviceName": "user-service"
  }
}
```

### 3. Context Propagation (컨텍스트 전파)
서비스 간 호출 시 **HTTP 헤더로 Trace 정보를 전달**합니다.

```http
GET /api/users/1 HTTP/1.1
X-B3-TraceId: 68eb6934747c42b7237d7d4de82a0276
X-B3-SpanId: 237d7d4de82a0276
X-B3-ParentSpanId: 68eb6934747c42b7
```

---

## 구현 가이드

### 1. 의존성 추가

**root build.gradle**
```gradle
subprojects {
    dependencies {
        implementation 'io.micrometer:micrometer-tracing-bridge-brave'
        implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
    }
}
```

**order-service/build.gradle**
```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.openfeign:feign-micrometer'  // ⭐ 필수!
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

> ⚠️ **중요**: `feign-micrometer` 없으면 trace context가 전파되지 않습니다!

### 2. Config Server 설정

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% 샘플링 (개발), 운영은 0.1 권장
    propagation:
      type: b3  # ⭐ Zipkin B3 포맷 (필수)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

### 3. Docker Compose

```yaml
zipkin:
  image: openzipkin/zipkin:latest
  ports:
    - "9411:9411"
  environment:
    - STORAGE_TYPE=mem  # 개발용
```

### 4. OpenFeign Client

```java
@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/api/users/{id}")
    UserResponse getUserById(@PathVariable("id") Long id);
}
```

---

## 트러블슈팅

### 문제 1: Trace가 연결되지 않음 ⭐ 가장 흔함

**증상**
```bash
# Order Service와 User Service가 별도 Trace로 표시
{"traceId": "ABC", "services": ["order-service"]}
{"traceId": "XYZ", "services": ["user-service"]}
```

**원인**
Spring Boot 3.x + OpenFeign에서 trace context 자동 전파 안 됨

**해결**
```gradle
// 1. 의존성 추가
implementation 'io.github.openfeign:feign-micrometer'
```

```yaml
# 2. propagation 설정
management:
  tracing:
    propagation:
      type: b3
```

```bash
# 3. 재빌드
rm -rf order-service/build
docker-compose up -d --build order-service
```

**검증**
```bash
curl -s "http://localhost:9411/api/v2/traces?serviceName=order-service&limit=1" | \
  jq '.[0] | {traceId: .[0].traceId, services: [.[] | .localEndpoint.serviceName] | unique}'

# 결과: {"traceId": "...", "services": ["order-service", "user-service"]} ✅
```

### 문제 2: Docker 빌드 캐싱

**증상**
코드 수정 후 `docker-compose up -d --build` 해도 반영 안 됨

**해결**
```bash
rm -rf order-service/build && docker-compose up -d --build order-service
```

### 문제 3: Propagation Format 불일치

**해결**
모든 서비스에서 동일한 type 사용:
```yaml
management:
  tracing:
    propagation:
      type: b3  # 모든 서비스 통일
```

---

## 활용

### Zipkin UI
**접속**: http://localhost:9411

```
1. Service Name: order-service 선택
2. Trace 클릭 → Span 상세 확인
   ├─ order-service: 19ms
   ├─ http get (Feign): 5ms
   └─ user-service: 3ms
```

### API 조회
```bash
# 최근 Trace 조회
curl -s "http://localhost:9411/api/v2/traces?serviceName=order-service&limit=10" | jq

# 특정 Trace ID 조회
curl -s "http://localhost:9411/api/v2/trace/${TRACE_ID}" | jq
```

### 로그에서 Trace ID 확인
```
INFO [order-service,68eb6934747c42b7,237d7d4de82a0276]
                    └─ Trace ID ────┘ └─ Span ID ───┘
```

---

## 면접 대비 Q&A

### Q1. Zipkin vs Prometheus 차이는?

**A:**
- **Zipkin (Tracing)**: "무슨 일이 일어났나?" → 요청 흐름 추적, 성능 병목 분석
- **Prometheus (Metrics)**: "얼마나 일어났나?" → CPU/메모리/요청 수 모니터링

실전에서는 둘 다 사용합니다.

### Q2. Spring Cloud Sleuth vs Micrometer Tracing?

**A:**
- **Sleuth**: Spring Boot 2.x 시대의 분산 추적
- **Micrometer Tracing**: Spring Boot 3.x 표준

Spring Boot 3.x로 마이그레이션 시:
- 의존성: `spring-cloud-starter-sleuth` → `micrometer-tracing-bridge-brave`
- OpenFeign 통합: **`feign-micrometer` 필수 추가**

### Q3. B3 vs W3C Propagation?

**A:**
| 특징 | B3 (Zipkin) | W3C |
|------|-------------|-----|
| 헤더 | `X-B3-TraceId` | `traceparent` |
| 표준 | Zipkin 표준 | W3C 국제 표준 |
| 권장 | 기존 Zipkin 사용 중 | 새 프로젝트 |

### Q4. OpenFeign에서 trace context 자동 전파 안 되는 이유?

**A:**
Spring Boot 3.x에서는 Micrometer Tracing과 OpenFeign 통합이 자동으로 안 됩니다.

**해결**: `feign-micrometer` 의존성 명시적 추가 필수
```gradle
implementation 'io.github.openfeign:feign-micrometer'
```

이게 없으면 OpenFeign이 B3 헤더를 자동으로 추가하지 않습니다.

### Q5. 모든 요청을 추적하면 성능 영향은?

**A:**
- Trace ID 생성: 매우 낮음 (~1μs)
- HTTP 헤더 추가: 매우 낮음
- Zipkin 전송: 낮음 (비동기)

**Sampling 전략**:
```yaml
# 개발
probability: 1.0  # 100%

# 운영 (높은 트래픽)
probability: 0.1  # 10%
```

에러 발생 시 항상 100% 샘플링됩니다 (Brave 기본 동작).

### Q6. 서비스 간 통신 패턴은?

**A:**
```
External Client
    ↓ (인증 필요)
API Gateway (인증/인가/라우팅)
    ↓
Order Service
    ↓ (내부 통신, Service Discovery)
User Service ✅ 직접 호출 (일반적)
```

**패턴 선택**:
- **외부 → 서비스**: API Gateway 필수
- **서비스 → 서비스**: 직접 호출 (Netflix, Amazon, Uber 방식)

API Gateway를 다시 경유하면 성능 오버헤드와 복잡도가 증가합니다.

---

## 참고 자료

- [Zipkin 공식 사이트](https://zipkin.io/)
- [Micrometer Tracing 문서](https://micrometer.io/docs/tracing)
- [Spring Boot Actuator - Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OpenFeign #812 - Micrometer Tracing 통합 이슈](https://github.com/spring-cloud/spring-cloud-openfeign/issues/812)

---

> 📝 **작성**: 2025-10-12
> 🏷️ **태그**: Zipkin, Distributed Tracing, Micrometer, OpenFeign, Spring Boot 3.x
