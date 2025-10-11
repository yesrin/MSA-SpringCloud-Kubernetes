# Resilience4j 패턴 가이드

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
- **Retry**: 실패한 요청 재시도
- **Rate Limiter**: 요청 속도 제한
- **Time Limiter**: 타임아웃 설정
- **Bulkhead**: 동시 호출 수 제한

이 프로젝트에서는 **Circuit Breaker**, **Fallback**, **Time Limiter**를 주로 사용합니다.

---

## 2. Circuit Breaker 패턴

### 개념
Circuit Breaker는 전기 회로의 차단기(Circuit Breaker)에서 유래한 패턴입니다.
장애가 발생한 서비스로의 호출을 차단하여 **연쇄 장애(Cascade Failure)**를 방지합니다.

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

#### CLOSED (닫힘 - 정상)
- 모든 요청이 정상적으로 전달됨
- 실패율 모니터링 중
- 실패율이 임계값(50%)을 초과하면 → **OPEN**

#### OPEN (열림 - 차단)
- **모든 요청 즉시 차단**
- Fallback 응답 반환
- 대기 시간(10초) 경과 후 → **HALF_OPEN**
- 목적: 장애 서비스에 부하를 주지 않고 회복 시간 제공

#### HALF_OPEN (반열림 - 테스트)
- 제한된 수(3개)의 요청만 허용
- 서비스 복구 여부 테스트
- 모든 테스트 성공 → **CLOSED**
- 하나라도 실패 → **OPEN**

### 실제 동작 흐름

#### 정상 상태
```
┌─────────┐         ┌──────────────┐         ┌──────────────┐
│ Client  │────────>│ API Gateway  │────────>│Order Service │
└─────────┘         │              │         └──────────────┘
                    │ Circuit:     │              ↓
                    │ CLOSED ✅    │         정상 응답
                    └──────────────┘              ↓
                           ↓                      ↓
                    클라이언트에게 <────────────────┘
                    정상 응답 전달
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
- ✅ 캐싱, 커스터마이징 용이

### 실제 응답 예시

#### 정상 응답
```bash
$ curl http://localhost:8080/api/orders
[
  {
    "id": 1,
    "userId": 1,
    "productName": "맥북 프로",
    "quantity": 1,
    "price": 2500000.00,
    "status": "PENDING"
  }
]
```

#### Fallback 응답 (Order Service 장애 시)
```bash
$ curl http://localhost:8080/api/orders
{
  "service": "order-service",
  "message": "Order 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.",
  "status": "SERVICE_UNAVAILABLE",
  "timestamp": "2025-10-11T11:30:43.707"
}
```

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

### 전체 설정 위치

**Config Server:**
```
config-server/
└── src/main/resources/
    ├── application.yml           # Config Server 자체 설정
    ├── application-docker.yml
    └── config/
        ├── api-gateway.yml       # 👈 Circuit Breaker 설정 여기!
        ├── user-service.yml
        └── order-service.yml
```

**API Gateway:**
```
api-gateway/
└── src/main/
    ├── java/com/example/gateway/
    │   └── FallbackController.java  # 👈 Fallback 컨트롤러
    └── resources/
        ├── application.yml
        └── application-docker.yml   # 👈 Config Server import 설정
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

# 예상 결과:
# {
#   "service": "order-service",
#   "message": "Order 서비스가 일시적으로 사용 불가능합니다...",
#   "status": "SERVICE_UNAVAILABLE"
# }

# 3. 여러 번 호출하여 Circuit Breaker OPEN 유도
for i in {1..6}; do
  echo "요청 $i:"
  curl http://localhost:8080/api/orders
  echo -e "\n"
done
```

### 5.4 서비스 격리 확인

Order Service가 죽어도 User Service는 정상 동작해야 합니다.

```bash
# Order Service 중지 상태에서
docker-compose stop order-service

# User Service는 정상 동작 ✅
curl http://localhost:8080/api/users

# Order Service는 Fallback ✅
curl http://localhost:8080/api/orders
```

이것이 **MSA의 핵심 장점**입니다!

### 5.5 복구 테스트

```bash
# 1. Order Service 재시작
docker-compose start order-service

# 2. 서비스 완전히 시작될 때까지 대기
sleep 15

# 3. API 호출 (HALF_OPEN 상태로 전환)
curl http://localhost:8080/api/orders

# 4. 여러 번 호출하여 CLOSED로 복구 확인
for i in {1..5}; do
  echo "복구 테스트 $i:"
  curl http://localhost:8080/api/orders
  echo -e "\n"
  sleep 1
done
```

---

## 6. 로그 분석

### 6.1 Circuit Breaker 상태 전환 로그

#### CLOSED → OPEN
```log
2025-10-11T11:34:00.760Z DEBUG --- CircuitBreakerStateMachine :
Event ERROR published: CircuitBreaker 'orderServiceCircuitBreaker'
recorded an error: 'NotFoundException: 503 SERVICE_UNAVAILABLE
"Unable to find instance for order-service"'

2025-10-11T11:34:00.760Z DEBUG --- CircuitBreakerStateMachine :
Event FAILURE_RATE_EXCEEDED published: CircuitBreaker
'orderServiceCircuitBreaker' exceeded failure rate threshold.
Current failure rate: 50.0

2025-10-11T11:34:00.765Z DEBUG --- CircuitBreakerStateMachine :
Event STATE_TRANSITION published: CircuitBreaker
'orderServiceCircuitBreaker' changed state from CLOSED to OPEN
```

#### OPEN → HALF_OPEN
```log
2025-10-11T11:36:38.782Z DEBUG --- CircuitBreakerStateMachine :
Event STATE_TRANSITION published: CircuitBreaker
'orderServiceCircuitBreaker' changed state from OPEN to HALF_OPEN
```

#### HALF_OPEN → CLOSED
```log
2025-10-11T11:37:00.123Z DEBUG --- CircuitBreakerStateMachine :
Event STATE_TRANSITION published: CircuitBreaker
'orderServiceCircuitBreaker' changed state from HALF_OPEN to CLOSED
```

### 6.2 서비스 장애 로그

```log
2025-10-11T11:30:43.691Z WARN --- RoundRobinLoadBalancer :
No servers available for service: order-service

2025-10-11T11:30:43.697Z DEBUG --- CircuitBreakerStateMachine :
Event ERROR published: CircuitBreaker 'orderServiceCircuitBreaker'
recorded an error
```

### 6.3 정상 호출 로그

```log
2025-10-11T11:31:06.077Z DEBUG --- CircuitBreakerStateMachine :
CircuitBreaker 'userServiceCircuitBreaker' succeeded:

2025-10-11T11:31:06.077Z DEBUG --- CircuitBreakerStateMachine :
Event SUCCESS published: CircuitBreaker 'userServiceCircuitBreaker'
recorded a successful call. Elapsed time: 9 ms
```

### 6.4 유용한 로그 명령어

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

# 전체 흐름 확인 (Circuit Breaker + Fallback)
docker-compose logs api-gateway | grep -E "circuit|fallback|ERROR" | tail -50
```

---

## 7. 설정 변경 방법

### Config Server 설정 변경 후 반영

```bash
# 1. config-server/src/main/resources/config/api-gateway.yml 수정

# 2. Config Server 재시작
docker-compose restart config-server

# 3. API Gateway 재시작 (설정 다시 로드)
docker-compose restart api-gateway

# 4. 변경 사항 확인
docker-compose logs api-gateway | grep "resilience4j"
```

### 테스트용 설정 예시

빠른 테스트를 위한 설정:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 5          # 5개로 줄임
        minimumNumberOfCalls: 2       # 2번만 호출해도 동작
        failureRateThreshold: 50
        waitDurationInOpenState: 5000 # 5초로 줄임
        permittedNumberOfCallsInHalfOpenState: 2
```

---

## 8. 트러블슈팅

### 문제 1: Circuit Breaker가 동작하지 않음

**증상:**
- 서비스가 죽어도 계속 에러만 발생
- Fallback이 동작하지 않음

**원인:**
`minimumNumberOfCalls` 설정값보다 적게 호출했을 수 있습니다.

**해결:**
```bash
# minimumNumberOfCalls가 5라면, 최소 5번 이상 호출
for i in {1..6}; do
  curl http://localhost:8080/api/orders
done
```

### 문제 2: Config Server 설정이 반영되지 않음

**증상:**
- 설정 파일을 수정했는데 변경사항이 적용되지 않음

**해결:**
```bash
# 1. Config Server 재시작
docker-compose restart config-server

# 2. API Gateway 재시작 (반드시 필요!)
docker-compose restart api-gateway

# 3. 로그 확인
docker-compose logs config-server | grep "api-gateway.yml"
```

### 문제 3: HALF_OPEN 상태에서 복구되지 않음

**원인:**
- HALF_OPEN에서 하나라도 실패하면 다시 OPEN으로 돌아갑니다.
- 서비스가 완전히 시작되지 않았을 수 있습니다.

**해결:**
```bash
# 1. 서비스가 완전히 시작될 때까지 충분히 대기
sleep 20

# 2. Health Check 확인
curl http://localhost:8082/actuator/health  # Order Service 직접 확인

# 3. Eureka에 등록되었는지 확인
curl http://localhost:8761  # Eureka 대시보드
```

---

## 9. 참고 자료

- [Resilience4j 공식 문서](https://resilience4j.readme.io/)
- [Spring Cloud Circuit Breaker](https://spring.io/projects/spring-cloud-circuitbreaker)
- [Spring Cloud Config](https://spring.io/projects/spring-cloud-config)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
