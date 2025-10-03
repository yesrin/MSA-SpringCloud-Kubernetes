# 🤔 개발 과정에서의 질문과 답변

## MSA 아키텍처 관련

### Q: Spring Cloud 없이도 MSA가 가능한데 왜 사용하나요?

**A:** Spring Cloud를 사용하는 이유는 다음과 같습니다:

1. **서비스 디스커버리 자동화**
   ```java
   // Spring Cloud 없으면
   RestTemplate.getForObject("http://user-service-1:8081/api/users/1", User.class);  // 하드코딩

   // Spring Cloud 있으면
   RestTemplate.getForObject("http://user-service/api/users/1", User.class);  // 자동 발견
   ```

2. **로드밸런싱 자동화**
   - 인스턴스 장애 시 자동 전환
   - 인스턴스 추가/제거 시 자동 감지

3. **개발 편의성**
   ```java
   @FeignClient(name = "user-service")  // 간단한 서비스 간 통신
   interface UserClient {
       @GetMapping("/api/users/{id}")
       User getUser(@PathVariable Long id);
   }
   ```

**결론**: 작은 MSA는 Spring Cloud 없어도 되지만, 서비스가 많아질수록 Spring Cloud의 자동화 기능이 큰 도움이 됩니다.

---

## Gradle 멀티모듈 관련

### Q: 멀티모듈에서 공통 설정은 어떻게 관리하나요?

**A:** `subprojects` 블록을 활용해서 공통 설정을 자동 적용합니다:

```groovy
subprojects {
    // 모든 하위 모듈에 자동 적용
    dependencies {
        compileOnly 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }
}
```

**장점**:
- 중복 설정 제거
- 새 서비스 추가 시 최소한의 설정만 필요
- 일관된 의존성 관리

### Q: 모든 서비스에서 공통 모듈을 꼭 사용해야 하나요?

**A:** 아닙니다! 필요한 모듈만 의존성을 추가하면 됩니다:

- **user-service**: `implementation project(':common')` ✅ (BaseEntity 사용)
- **order-service**: `implementation project(':common')` ✅ (BaseEntity 사용)
- **api-gateway**: 의존성 없음 ✅ (BaseEntity 안 씀)

**원칙**: 필요한 모듈만 의존성 추가, 불필요한 의존성은 추가하지 않음

---

## FeignClient vs RestTemplate

### Q: FeignClient를 왜 사용하나요? RestTemplate과 차이는?

**A:** 둘 다 MSA에서 서비스 간 통신에 사용되지만 편의성에 차이가 있습니다:

**RestTemplate (번거로움)**:
```java
val response = restTemplate.getForObject("http://user-service/api/users/$id", UserDto::class.java)
```

**FeignClient (간편함)**:
```java
@FeignClient(name = "user-service")
interface UserClient {
    @GetMapping("/api/users/{id}")
    UserDto getUserById(@PathVariable Long id);
}

// 사용
val user = userClient.getUserById(id)
```

**결론**: RestTemplate도 가능하지만 FeignClient가 더 선언적이고 간편합니다.

---

## 기술 선택 이유

### Q: Maven 대신 Gradle을 선택한 이유는?

**A:**
- **빌드 속도**: Gradle이 더 빠름
- **문법**: Groovy 문법이 XML보다 간결
- **현대적**: 요즘 Spring 프로젝트에서 더 많이 사용
- **유연성**: 복잡한 빌드 로직 구현 시 더 유연

### Q: Java 대신 Kotlin을 처음에 시도한 이유는?

**A:** Kotlin의 장점을 경험해보고 싶어서였지만, Java와 비교 학습을 위해 Java로 변경했습니다:

**Kotlin 장점**:
- data class로 boilerplate 코드 최소화
- null safety
- 간결한 문법

**Java를 최종 선택한 이유**:
- 두 언어의 차이점을 명확히 비교하기 위함
- Lombok 적용 전후 비교 가능