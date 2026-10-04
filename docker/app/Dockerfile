# 애플리케이션 서버 공용 이미지 - 모듈 이름(MODULE)만 바꿔 7개 서비스를 모두 만든다
# 빌드 단계는 MODULE과 무관해서, 여러 서비스를 함께 빌드해도 BuildKit이 한 번만 실행하고 결과를 공유한다
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY . .
# Gradle 배포판과 의존성은 캐시 마운트에 남겨 다음 빌드에서 다시 내려받지 않도록 함
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=builder /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
