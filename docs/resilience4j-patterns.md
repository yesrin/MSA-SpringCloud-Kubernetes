# Resilience4j 패턴 가이드

> 구현 가이드 문서 | Circuit Breaker & Fallback 상세 설명

## 목차
1. [Resilience4j 소개](#1-resilience4j-소개)
2. [Circuit Breaker 패턴](#2-circuit-breaker-패턴)
3. [Fallback 패턴](#3-fallback-패턴)
4. [프로젝트 설정 구조](#4-프로젝트-설정-구조)
5. [실제 동작 테스트](#5-실제-동작-테스트)
6. [로그 분석](#6-로그-분석)

---

## 1. Resilience4j 소개

Resilience4j는 Netflix Hystrix에서 영감을 받은 경량 장애 허용(fault tolerance) 라이브러리입니다.

### 주요 모듈
- **Circuit Breaker**: 장애 감지 및 연쇄 장애 방지
- **Time Limiter**: 타임아웃 설정
- **Retry**: 실패한 요청 재시도
- **Rate Limiter**: 요청 속도 제한
- **Bulkhead**: 동시 호출 수 제한

이 프로젝트에서는 **Circuit Breaker**, **Fallback**, **Time Limiter**를 사용합니다.

---

## 2. Circuit Breaker 패턴

### 개념
Circuit Breaker는 전기 회로의 차단기에서 유래한 패턴으로, 장애가 발생한 서비스로의 호출을 차단하여 **연쇄 장애(Cascade Failure)**를 방지합니다.

### 상태 전환

```
                    실패율 임계값 초과
        ┌─────────────────────────────────┐
        │                                 │
        ↓                                 │
   ┌─────────┐                      ┌─────────┐
   │ CLOSED  │                      │  OPEN   │
   │  정상   │                      │  차단   │
   └─────────┘                      └─────────┘
        ↑                                 │
        │                                 │
        │    모든 테스트 성공              │  대기 시간 경과
        │                                 ↓
        │                           ┌──────────┐
        └───────────────────────────│HALF_OPEN │
                                    │ 테스트중 │
                                    └──────────┘
```

### 각 상태 설명

**CLOSED (닫힘 - 정상)**
- 모든 요청이 정상적으로 전달됨
- 실패율 모니터링 중
- 실패율이 임계값(50%)을 초과하면 → **OPEN**

**OPEN (열림 - 차단)**
- 모든 요청 즉시 차단, Fallback 응답 반환
- 대기 시간(10초) 경과 후 → **HALF_OPEN**
- 목적: 장애 서비스에 부하를 주지 않고 회복 시간 제공

**HALF_OPEN (반열림 - 테스트)**
- 제한된 수(3개)의 요청만 허용
- 모든 테스트 성공 → **CLOSED**, 하나라도 실패 → **OPEN**

### 실제 동작 흐름

#### 정상 상태
```
┌─────────┐         ┌──────────────┐         ┌──────────────┐
│ Client  │────────>│ API Gateway  │────────>│Order Service │
└─────────┘         │ Circuit:     │         └──────────────┘
                    │ CLOSED ✅    │              ↓
                    └──────────────┘         정상 응답 전달
```

#### 장애 상태
```
┌─────────┐         ┌──────────────────────────────────┐         ┌──────────────┐
│ Client  │────────>│      API Gateway                 │─── X ───│Order Service │
└─────────┘         │                                  │         │   (DOWN) ❌  │
     ↑              │  1) Order Service 호출 시도      │         └──────────────┘
     │              │     ❌ 실패!                      │
     │              │                                  │
     │              │  2) Circuit Breaker 감지         │
     │              │     "실패율 50% 초과!"           │
     │              │     상태: CLOSED → OPEN 🔴       │
     │              │                                  │
     │              │  3) Fallback 트리거              │
     │              │     forward:/fallback/order-service
     │              │          ↓                       │
     │              │     ┌────────────────────┐      │
     │              │     │FallbackController  │      │
     │              │     │  .orderService     │      │
     │              │     │  Fallback()        │      │
     │              │     └────────────────────┘      │
     │              │          ↓                       │
     │              │     친절한 에러 메시지 생성       │
     └──────────────┴──────────────────────────────────┘
           503 SERVICE_UNAVAILABLE
```

---

## 3. Fallback 패턴

### Fallback이란?
주 서비스가 실패했을 때 **대체 응답**을 제공하는 패턴입니다.

### forward: 방식의 동작 원리

#### Config Server 설정
**파일 위치:** `config-server/src/main/resources/config/api-gateway.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCircuitBreaker
                fallbackUri: forward:/fallback/order-service  # 👈 핵심!
            - RewritePath=/api/orders/(?<segment>.*), /$\{segment}
```

#### API Gateway에서 설정 Import
**파일 위치:** `api-gateway/src/main/resources/application-docker.yml`

```yaml
spring:
  application:
    name: api-gateway
  config:
    import: optional:configserver:http://config-server:8888  # 👈 Config Server에서 가져옴
```

#### FallbackController (API Gateway 프로젝트 내부)
**파일 위치:** `api-gateway/src/main/java/com/example/gateway/FallbackController.java`

```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/order-service")  // 👈 /fallback/order-service 경로
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        log.warn("Circuit breaker activated for service: order-service");

        Map<String, Object> response = new HashMap<>();
        response.put("service", "order-service");
        response.put("message", "Order 서비스가 일시적으로 사용 불가능합니다.");
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
```

### forward vs 외부 호출 비교

```
외부 HTTP 호출 (❌ 비효율적)      내부 forward (✅ 효율적)
─────────────────────────      ─────────────────────
Gateway                        Gateway
  ↓                              ↓
HTTP 요청 (네트워크)            직접 메서드 호출
  ↓                              ↓
다른 서버의 API                 자신의 Controller
  ↓                              ↓
추가 지연 & 실패 가능성          즉시 응답
```

**forward의 장점:**
- ✅ 네트워크 호출 없음 (빠름)
- ✅ 추가 실패 가능성 없음
- ✅ Gateway 내부에서 완결

---

## 4. 프로젝트 설정 구조

### 설정 파일 흐름

```
1. Config Server 시작
   └─ config-server/src/main/resources/config/*.yml 로드

2. API Gateway 시작
   ├─ application-docker.yml 읽기
   ├─ spring.config.import로 Config Server에 접속
   └─ Config Server에서 api-gateway.yml 가져오기

3. Circuit Breaker 설정 적용
   └─ Resilience4j 초기화
```

### Circuit Breaker 설정 상세

**파일:** `config-server/src/main/resources/config/api-gateway.yml`

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true           # 헬스체크 노출
        slidingWindowSize: 10                   # 최근 10개 요청 기준
        minimumNumberOfCalls: 5                 # 최소 5번 호출 후 동작
        failureRateThreshold: 50                # 실패율 50% 이상
        waitDurationInOpenState: 10000          # OPEN 상태 10초 유지
        permittedNumberOfCallsInHalfOpenState: 3 # HALF_OPEN에서 3번 테스트
        slowCallDurationThreshold: 2000         # 2초 이상이면 느린 호출
        slowCallRateThreshold: 50               # 느린 호출 50% 이상
    instances:
      orderServiceCircuitBreaker:
        baseConfig: default
      userServiceCircuitBreaker:
        baseConfig: default

  timelimiter:
    configs:
      default:
        timeoutDuration: 3s                     # 3초 타임아웃
```

### 설정 값 설명

| 설정 | 현재값 | 설명 |
|------|--------|------|
| `slidingWindowSize` | 10 | 실패율 계산에 사용할 최근 요청 수 |
| `minimumNumberOfCalls` | 5 | Circuit Breaker 작동 전 최소 호출 수 |
| `failureRateThreshold` | 50 | OPEN으로 전환되는 실패율 (%) |
| `waitDurationInOpenState` | 10s | OPEN 상태 유지 시간 |
| `permittedNumberOfCallsInHalfOpenState` | 3 | HALF_OPEN에서 허용할 요청 수 |
| `slowCallDurationThreshold` | 2s | 느린 호출로 간주되는 시간 |
| `timeoutDuration` | 3s | 요청 타임아웃 |

---

## 5. 실제 동작 테스트

### 5.1 전체 서비스 시작

```bash
# 1. 전체 빌드
./gradlew clean build

# 2. Docker Compose로 실행
docker-compose up -d

# 3. 서비스 상태 확인
docker-compose ps
```

### 5.2 정상 동작 확인

```bash
# User 생성
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"홍길동","email":"hong@example.com"}'

# Order 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productName":"맥북","quantity":1,"price":2500000}'

# 조회
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/users
```

### 5.3 Circuit Breaker 테스트

```bash
# 1. Order Service 중지
docker-compose stop order-service

# 2. Order API 호출 (Fallback 응답 확인)
curl http://localhost:8080/api/orders

# 3. 여러 번 호출하여 Circuit Breaker OPEN 유도
for i in {1..6}; do
  echo "요청 $i:"
  curl http://localhost:8080/api/orders
  echo -e "\n"
done
```

**예상 결과:**
```json
{
  "service": "order-service",
  "message": "Order 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.",
  "status": "SERVICE_UNAVAILABLE",
  "timestamp": "2025-10-11T11:30:43.707"
}
```

### 5.4 서비스 격리 확인

Order Service가 죽어도 User Service는 정상 동작합니다. **이것이 MSA의 핵심 장점!**

```bash
# Order Service 중지 상태에서
curl http://localhost:8080/api/users  # ✅ 정상 동작
curl http://localhost:8080/api/orders # ✅ Fallback 응답
```

### 5.5 복구 테스트

```bash
# 1. Order Service 재시작
docker-compose start order-service

# 2. 서비스 완전히 시작될 때까지 대기
sleep 15

# 3. 여러 번 호출하여 CLOSED로 복구 확인
for i in {1..5}; do
  echo "복구 테스트 $i:"
  curl http://localhost:8080/api/orders
  sleep 1
done
```

---

## 6. 로그 분석

### 6.1 Circuit Breaker 상태 전환 로그

**CLOSED → OPEN**
```log
Event ERROR published: CircuitBreaker 'orderServiceCircuitBreaker'
recorded an error: 'NotFoundException: 503 SERVICE_UNAVAILABLE'

Event FAILURE_RATE_EXCEEDED: Current failure rate: 50.0

Event STATE_TRANSITION: CircuitBreaker changed state from CLOSED to OPEN
```

**OPEN → HALF_OPEN**
```log
Event STATE_TRANSITION: CircuitBreaker changed state from OPEN to HALF_OPEN
```

**HALF_OPEN → CLOSED**
```log
Event STATE_TRANSITION: CircuitBreaker changed state from HALF_OPEN to CLOSED
```

### 6.2 유용한 로그 명령어

```bash
# Circuit Breaker 상태 변화 추적
docker-compose logs api-gateway | grep "STATE_TRANSITION"

# 에러 발생 추적
docker-compose logs api-gateway | grep "ERROR published"

# 특정 서비스 Circuit Breaker 추적
docker-compose logs api-gateway | grep "orderServiceCircuitBreaker"

# 실시간 로그 모니터링
docker-compose logs -f api-gateway | grep -i "circuit"

# Fallback 활성화 확인
docker-compose logs api-gateway | grep "No servers available"
```

---

## 7. 참고 문서

- **[Circuit-Breaker-QNA.md](./Circuit-Breaker-QNA.md)** - 면접 대비 Q&A (실무 경험 중심)
- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Spring Cloud Circuit Breaker](https://spring.io/projects/spring-cloud-circuitbreaker)
