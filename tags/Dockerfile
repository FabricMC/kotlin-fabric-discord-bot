FROM openjdk:15-jdk-slim

COPY build/libs/tags-*-all.jar /usr/local/lib/tags.jar

RUN mkdir /tags
WORKDIR /tags

ENTRYPOINT ["java", "-jar", "/usr/local/lib/tags.jar"]
