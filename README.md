# Spring Cloud + Kubernetes 기반 MSA 실습 프로젝트

## 1. 프로젝트 개요
- 모놀리식 구조를 User/Order 서비스로 분리하여 MSA 환경 구성
- Spring Cloud Gateway를 사용하여 서비스 라우팅 및 API Gateway 패턴 구현
- Resilience4j를 통한 Circuit Breaker/Fallback 적용으로 장애 대응 경험
- Sleuth + Zipkin으로 분산 추적 환경 구축, 요청 흐름 분석
- Docker 컨테이너화 후 Kubernetes에 배포하여 클라우드 네이티브 환경 이해
- Spring Cloud와 Kubernetes 기능 비교 및 차이점 학습

## 2. 아키텍처 다이어그램
<img width="300" height="400" alt="image" src="https://github.com/user-attachments/assets/ba82f842-3671-4e7d-973e-1442d7c0e40d" />

- User 서비스 ↔ Order 서비스 간 REST 통신
- Gateway에서 서비스 라우팅
- Circuit Breaker 적용 시 Order 서비스 장애 시 fallback 동작
- Sleuth + Zipkin으로 요청 추적

## 3. 주요 기능 / 실습 내용
1. 서비스 분리: User, Order 서비스
2. API Gateway 라우팅 및 인증 (Spring Cloud Gateway)
3. 장애 대응: Resilience4j Circuit Breaker + Fallback
4. 분산 추적: Sleuth + Zipkin
5. Docker 컨테이너화
6. Kubernetes 배포: Deployment, Service, Ingress
7. Spring Cloud vs Kubernetes 기능 비교 학습

## 4. 실행 방법
1. 각 서비스 빌드
```bash
cd user-service && ./mvnw clean package
cd order-service && ./mvnw clean package
