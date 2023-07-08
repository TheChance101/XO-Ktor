FROM openjdk:11 as builder

COPY . .

RUN ./gradlew buildFatJar

FROM openjdk:11

COPY --from=builder /build/libs/xo-ktor.jar ./xo-ktor.jar

CMD ["java", "-jar", "xo-ktor.jar"]