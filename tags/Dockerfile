FROM gradle:6.6.1-jdk14 as BUILD

COPY . .
RUN gradle build

FROM openjdk:15-jdk-slim

COPY --from=BUILD /home/gradle/build/libs/Tags-*-all.jar /usr/local/lib/Tags.jar

RUN mkdir /tags
WORKDIR /tags

ENTRYPOINT ["java", "-jar", "/usr/local/lib/Tags.jar"]
