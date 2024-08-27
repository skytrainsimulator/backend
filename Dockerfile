FROM gradle:8.8.0-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon -D io.u11.skytrainsim.ignoreversion=true

FROM eclipse-temurin:21-alpine

RUN mkdir /app

COPY --from=build /home/gradle/src/build/libs/ /app/

WORKDIR /app

ENTRYPOINT ["java","-jar","/app/backend.jar"]
