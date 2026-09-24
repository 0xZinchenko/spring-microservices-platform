FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -pl ${MODULE} -am -DskipTests \
    && cp ${MODULE}/target/${MODULE}-*.jar /build/app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home spring
USER spring
WORKDIR /app
COPY --from=build /build/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
