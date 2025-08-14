#------------------ Build Aşaması ------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests

#------------------ Runtime Aşaması ------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Mutlak JAR dosyasına göre kopyalama (wildcard kullanılmaz)
COPY --from=build /workspace/target/generic-communication-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]