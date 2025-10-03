# 🔍 Eureka Server - MSA 서비스 디스커버리

## 📋 목차
- [Eureka Server란?](#eureka-server란)
- [왜 필요한가?](#왜-필요한가)
- [아키텍처 구조](#아키텍처-구조)
- [구현 과정](#구현-과정)
- [Docker 환경 설정](#docker-환경-설정)
- [테스트 방법](#테스트-방법)

## Eureka Server란?

**Eureka Server**는 Netflix에서 개발한 **서비스 디스커버리 패턴**을 구현한 도구입니다.

### 핵심 기능
- **서비스 등록**: 각 마이크로서비스가 시작할 때 자신의 정보를 등록
- **서비스 발견**: 다른 서비스 호출 시 위치 정보 제공
- **헬스체크**: 주기적으로 서비스 상태 확인
- **로드밸런싱**: 여러 인스턴스 간 부하 분산

## 왜 필요한가?

### ❌ Eureka 없는 MSA의 문제점

```java
// 하드코딩된 서비스 주소
RestTemplate.getForObject("http://192.168.1.100:8081/api/users/1", User.class);
```

**문제점들**:
- IP 주소 하드코딩 → 서버 변경 시 코드 수정 필요
- 로드밸런싱 수동 구현 필요
- 서비스 장애 감지 어려움
- 새로운 인스턴스 추가 시 수동 설정

### ✅ Eureka 사용 시의 장점

```java
@FeignClient(name = "user-service")  // 서비스 이름만으로 호출
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

## 구현 과정

### 1. Eureka Server 설정

**의존성 추가** (`eureka-server/build.gradle`):
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
}
```

**메인 클래스** (`EurekaServerApplication.java`):
```java
@SpringBootApplication
@EnableEurekaServer  // Eureka Server 활성화
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

**설정 파일** (`application.yml`):
```yaml
server:
  port: 8761

eureka:
  client:
    register-with-eureka: false    # 자기 자신을 등록하지 않음
    fetch-registry: false          # 다른 서비스 정보를 가져오지 않음
  server:
    enable-self-preservation: false  # 개발환경에서는 비활성화
```

### 2. Eureka Client 설정

**의존성 추가** (각 서비스 `build.gradle`):
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

**설정 파일** (`application.yml`):
```yaml
spring:
  application:
    name: user-service  # Eureka에 등록될 서비스 이름

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

### 3. FeignClient 설정

**의존성 추가**:
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
}
```

**FeignClient 인터페이스**:
```java
@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/api/users/{id}")
    UserDto getUserById(@PathVariable Long id);
}
```

**메인 클래스에 활성화**:
```java
@SpringBootApplication
@EnableFeignClients  // FeignClient 활성화
public class OrderServiceApplication {
    // ...
}
```

## Docker 환경 설정

### Docker Compose 구성

```yaml
version: '3.8'
services:
  eureka-server:
    build: ./eureka-server
    ports: ["8761:8761"]
    networks: [msa-network]

  user-service:
    build: ./user-service
    ports: ["8081:8081"]
    environment:
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
    depends_on: [eureka-server]
    networks: [msa-network]
```

### 핵심 변경점
- **localhost** → **컨테이너 이름** (`eureka-server`)
- **Docker 네트워크**로 서비스 간 통신
- **환경변수**로 Eureka 주소 동적 설정

## 테스트 방법

### 1. 로컬 실행
```bash
# 실행 순서
1. EurekaServerApplication 실행 (8761)
2. UserServiceApplication 실행 (8081)
3. OrderServiceApplication 실행 (8082)
4. ApiGatewayApplication 실행 (8080)
```

### 2. Docker 실행
```bash
# JAR 빌드
./gradlew build

# Docker Compose 실행
docker-compose up --build

# 백그라운드 실행
docker-compose up -d --build
```

### 3. 확인 방법

**Eureka Dashboard**: http://localhost:8761
- 등록된 서비스 목록 확인
- 각 서비스의 상태 확인

**API 테스트**:
```bash
# API Gateway를 통한 서비스 호출
curl http://localhost:8080/api/users/1
curl http://localhost:8080/api/orders/1
```

### 4. 서비스 디스커버리 동작 확인

1. **서비스 등록 확인**: Eureka Dashboard에서 모든 서비스가 UP 상태인지 확인
2. **로드밸런싱 테스트**: 같은 서비스를 여러 번 실행하여 인스턴스 증가 확인
3. **장애 복구 테스트**: 서비스 중지 후 자동 제거되는지 확인

## 주요 설정 옵션

### Eureka Server
```yaml
eureka:
  server:
    enable-self-preservation: false    # Self Preservation 모드
    eviction-interval-timer-in-ms: 30000  # 제거 간격
```

### Eureka Client
```yaml
eureka:
  client:
    fetch-registry: true              # 서비스 목록 가져오기
    register-with-eureka: true        # 자신을 등록할지 여부
    registry-fetch-interval-seconds: 30  # 서비스 목록 갱신 간격
  instance:
    lease-renewal-interval-in-seconds: 30    # Heartbeat 간격
    lease-expiration-duration-in-seconds: 90  # 만료 시간
```

## 트러블슈팅

### 1. 서비스가 Eureka에 등록되지 않는 경우
- **Eureka Server 먼저 실행** 확인
- **application.name** 설정 확인
- **Eureka 주소** 정확성 확인

### 2. FeignClient 호출 실패
- **@EnableFeignClients** 어노테이션 확인
- **서비스 이름**이 Eureka에 등록된 이름과 일치하는지 확인
- **패키지 스캔 범위** 확인

### 3. Docker 환경에서 서비스 발견 실패
- **컨테이너 이름**으로 Eureka 주소 설정 확인
- **Docker 네트워크** 설정 확인
- **서비스 시작 순서** (`depends_on`) 확인

---

## 📚 학습 포인트

1. **서비스 디스커버리**는 MSA의 핵심 패턴
2. **@EnableEurekaClient**는 Spring Boot 2.0+ 이후 불필요 (자동 설정)
3. **Docker 환경**에서는 컨테이너 이름 사용
4. **로드밸런싱**은 Spring Cloud LoadBalancer가 자동 처리
5. **장애 격리**를 통한 시스템 안정성 향상