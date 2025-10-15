# Spring Cloud + Kubernetes 기반 MSA 실습 프로젝트

## 1. 프로젝트 개요
- 모놀리식 구조를 User/Order 서비스로 분리하여 MSA 환경 구성
- Spring Cloud Gateway를 사용하여 서비스 라우팅 및 API Gateway 패턴 구현
- Resilience4j를 통한 Circuit Breaker/Fallback 적용으로 장애 대응 경험
- Micrometer Tracing + Zipkin으로 분산 추적 환경 구축, 서비스 간 호출 흐름 시각화
- OpenFeign을 통한 마이크로서비스 간 동기 통신 구현
- Docker 컨테이너화 후 Kubernetes에 배포하여 클라우드 네이티브 환경 이해
- Spring Cloud와 Kubernetes 기능 비교 및 차이점 학습

## 2. 아키텍처 다이어그램
<img width="300" height="400" alt="image" src="https://github.com/user-attachments/assets/ba82f842-3671-4e7d-973e-1442d7c0e40d" />

- User 서비스 ↔ Order 서비스 간 REST 통신 (OpenFeign)
- Gateway에서 서비스 라우팅
- Circuit Breaker 적용 시 Order 서비스 장애 시 fallback 동작
- Micrometer Tracing + Zipkin으로 요청 추적 (동일 Trace ID로 서비스 간 호출 연결)

## 3. 기술 스택

### Backend
- **Java 21**, **Spring Boot 3.1.5**
- **Spring Cloud 2022.0.4**: Gateway, Config, OpenFeign, Eureka
- **Resilience4j**: Circuit Breaker, Fallback
- **Micrometer Tracing + Brave**: 분산 추적
- **Zipkin**: 트레이싱 서버
- **MySQL**: Database per Service 패턴

### Infra
- **Docker**: 컨테이너화
- **Docker Compose**: 로컬 개발 환경
- **Kubernetes**: 프로덕션 배포 (예정)

## 4. 주요 기능 / 구현 내용

### 1) 마이크로서비스 아키텍처
- User Service, Order Service 분리
- Database per Service (각 서비스 독립 DB)
- RESTful API 설계 (쿼리 파라미터 활용)

### 2) API Gateway (Spring Cloud Gateway)
- 단일 진입점을 통한 라우팅
- 인증/인가 처리

### 3) 서비스 간 통신
- **OpenFeign**: 선언적 HTTP Client로 동기 통신
- Service Discovery (Eureka)를 통한 동적 라우팅

### 4) 장애 대응 (Resilience4j)
- **Circuit Breaker**: 장애 전파 차단
- **Fallback**: User Service 장애 시 기본값 반환
- 설정: 10번 중 50% 실패 시 Circuit Open

### 5) 분산 추적 (Micrometer + Zipkin) ⭐ 최근 구현
- **Trace ID/Span ID**: 서비스 간 요청 흐름 추적
- **B3 Propagation**: OpenFeign 호출 시 trace context 자동 전파
- **Zipkin UI**: 서비스 의존성 그래프 및 성능 병목 지점 시각화
- 트러블슈팅: Spring Boot 3.x + OpenFeign 통합 이슈 해결 (`feign-micrometer`)

### 6) 설정 관리 (Config Server)
- 중앙화된 설정 관리
- 환경별 설정 분리 (dev, prod)

## 5. 최근 개선사항

### Order-User 서비스 간 통신 구현 (2025.10.12)
**배경**: 기존에는 각 서비스가 독립적으로 동작했으나, 실제 비즈니스 시나리오에서는 서비스 간 데이터 통합이 필요

**구현 내용**:
- Order Service에서 User Service 호출하여 주문+사용자 정보 통합 응답
- RESTful API 개선: `GET /orders?userId=1` (쿼리 파라미터 활용)
- Circuit Breaker로 User Service 장애 시 fallback 처리

**기술적 도전 과제**:
1. **Trace Context 전파 이슈**: Spring Boot 3.x + OpenFeign 조합에서 trace context가 자동 전파되지 않는 문제 발견
   - 해결: `feign-micrometer` 의존성 추가 + B3 propagation 설정
   - 결과: Zipkin에서 Order → User 호출이 동일 Trace ID로 연결되어 추적 가능

2. **Docker 빌드 캐싱 이슈**: 코드 변경사항이 컨테이너에 반영되지 않는 문제
   - 해결: 빌드 아티팩트 삭제 후 재빌드 (`rm -rf build && docker-compose up --build`)

**성과**:
- Zipkin UI에서 3개 span으로 서비스 간 호출 시각화 (order-service 2개, user-service 1개)
- 평균 응답 시간: 19ms (Order), 3ms (User Service)

## 6. 실행 방법

### Docker Compose로 전체 시스템 실행
```bash
# 전체 빌드 및 실행
docker-compose up -d --build

# 특정 서비스만 재빌드
docker-compose up -d --build order-service

# 로그 확인
docker-compose logs -f order-service

# 종료
docker-compose down
```

### 접속 URL
- **Eureka Dashboard**: http://localhost:8761
- **Zipkin UI**: http://localhost:9411
- **Order Service**: http://localhost:8082/orders?userId=1
- **User Service**: http://localhost:8081/api/users/1
- **API Gateway**: http://localhost:8080

### API 테스트 예시
```bash
# 주문 생성
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "productName": "Laptop", "quantity": 2, "price": 1500000}'

# 주문+사용자 정보 조회 (분산 추적 확인 가능)
curl http://localhost:8082/orders?userId=1 | jq

# Zipkin에서 Trace 확인
curl http://localhost:9411/api/v2/traces?serviceName=order-service | jq
```

## 7. 관련 문서
- [Circuit Breaker 가이드](docs/resilience4j-patterns.md)
- [Circuit Breaker Q&A](docs/Circuit-Breaker-QNA.md)
- [Zipkin 분산 추적 가이드](docs/Zipkin-Distributed-Tracing.md)
- [Eureka Service Discovery](docs/EUREKA.md)
