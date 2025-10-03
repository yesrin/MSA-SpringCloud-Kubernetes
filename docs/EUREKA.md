# 🔍 Eureka Server - MSA 서비스 디스커버리

## 🤔 왜 Eureka가 필요한가?

MSA에서 서비스들이 서로를 찾고 통신하기 위한 **전화번호부** 역할을 합니다.

### ❌ Eureka 없는 MSA의 문제점

```java
// FeignClient에 URL 하드코딩
@FeignClient(name = "user-service", url = "http://192.168.1.100:8081")
interface UserClient {
    @GetMapping("/api/users/{id}")
    User getUser(@PathVariable Long id);
}
```

**문제점들**:
- **IP 은주소 하드코딩** → 서버 변경 시 코드 수정 필요
- **단일 인스턴스만 호출** → 로드밸런싱 불가능
- **장애 감지 불가** → 서버 죽어도 계속 호출 시도
- **수동 인스턴스 관리** → 새 서버 추가 시 코드 수정

### ✅ Eureka 사용 시의 장점

```java
// 서비스 이름만으로 동적 호출
@FeignClient(name = "user-service")  // URL 없음!
interface UserClient {
    @GetMapping("/api/users/{id}")
    User getUser(@PathVariable Long id);
}
```

**장점들**:
- **동적 서비스 발견**: IP 변경 시 자동 감지
- **자동 로드밸런싱**: 여러 인스턴스 간 자동 분산
- **장애 격리**: 죽은 서비스 자동 제거
- **확장성**: 새 인스턴스 추가 시 자동 감지

## 아키텍처 구조

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Gateway   │    │  Order Service  │    │  User Service   │
│     (8080)      │    │     (8082)      │    │     (8081)      │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │              register│/discover      register│
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                         ┌───────▼───────┐
                         │ Eureka Server │
                         │    (8761)     │
                         └───────────────┘
```

### 동작 과정
1. **서비스 시작**: 각 서비스가 Eureka에 자신을 등록
2. **서비스 호출**: FeignClient가 Eureka에서 대상 서비스 주소 조회
3. **로드밸런싱**: 여러 인스턴스 중 하나 선택하여 호출
4. **헬스체크**: 30초마다 heartbeat 전송

## 🚀 빠른 시작

### 1. Eureka Server
```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

### 2. 각 서비스 설정
```yaml
spring:
  application:
    name: user-service
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### 3. FeignClient 사용
```java
@FeignClient(name = "user-service")  // URL 하드코딩 없음!
interface UserClient {
    @GetMapping("/api/users/{id}")
    User getUser(@PathVariable Long id);
}
```

## 🐳 Docker 실행

```bash
# JAR 빌드 후 Docker로 실행
./gradlew build
docker-compose up --build
```

**핵심**: Docker에서는 `localhost` → `컨테이너이름` 변경 필요

## 🔍 확인 방법

- **Eureka Dashboard**: http://localhost:8761
- **API 테스트**: `curl http://localhost:8080/api/users/1`

## 장애 처리와 복원력

### 1. 개별 서비스 장애 시
```java
// ❌ 나쁜 예: 전체 실패
public Order createOrder(OrderRequest request) {
    User user = userClient.getUser(request.getUserId());  // User Service 죽으면 전체 실패
    return new Order(user, request);
}

// ✅ 좋은 예: 장애 격리
public Order createOrder(OrderRequest request) {
    try {
        User user = userClient.getUser(request.getUserId());
        return createOrderWithUser(user, request);
    } catch (FeignException e) {
        // 기본값으로 주문 처리 (나중에 사용자 정보 보완)
        return createOrderWithoutUser(request);
    }
}
```

### 2. Eureka Server 장애 시
**Eureka Client의 안전장치**:
- **로컬 캐시**: 서비스 주소 목록을 메모리에 저장
- **캐시 동작**: Eureka 죽어도 마지막 주소로 계속 호출 가능
- **제한사항**: 새 서비스 등록/IP 변경 감지 불가

```
정상 시: Order Service → Eureka → "user-service는 192.168.1.100:8081에 있어"
장애 시: Order Service → Eureka ❌ → "마지막 기억한 주소 사용: 192.168.1.100:8081"
```

### 3. 프론트엔드의 MSA 대응
```javascript
// ✅ 각 영역을 독립적으로 처리
try {
    const userData = await fetch('/api/users/1');
    renderUserSection(userData);
} catch (error) {
    renderUserErrorFallback(); // 사용자 정보 영역만 에러 표시
}

try {
    const orderData = await fetch('/api/orders/1');
    renderOrderSection(orderData);
} catch (error) {
    renderOrderErrorFallback(); // 주문 정보 영역만 에러 표시
}
```

## 📚 핵심 포인트

- **@EnableEurekaClient** 불필요 (Spring Boot 2.0+ 자동 설정)
- **장애 격리**: 한 서비스 죽어도 다른 서비스는 정상 동작
- **Eureka 캐시**: Eureka Server 죽어도 마지막 주소로 계속 통신 가능
- **프론트엔드**: 각 API를 독립적으로 처리하여 부분 장애 허용