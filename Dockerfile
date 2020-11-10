FROM gradle:6.6.1-jdk14 as BUILD

COPY . .
RUN gradle build

FROM openjdk:15-jdk-slim

COPY --from=BUILD /home/gradle/build/libs/discord-bot-*-all.jar /usr/local/lib/discord-bot.jar

RUN mkdir /git
ENV BOT_GIT_DIRECTORY /git

ENTRYPOINT ["java", "-Xms2G", "-Xmx2G", "-jar", "/usr/local/lib/discord-bot.jar"]
