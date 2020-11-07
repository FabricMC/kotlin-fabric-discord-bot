import ch.qos.logback.core.joran.spi.ConsoleTarget

def environment = System.getenv().getOrDefault("ENVIRONMENT", "production")

def defaultLevel = DEBUG

if (environment == "production") {
    defaultLevel = INFO
} else if (environment == "spam") {
    logger("com.gitlab.kordlib.gateway.DefaultGateway", TRACE)
    logger("net.fabricmc.bot.tags.TagParser", TRACE)
} else {
    // Silence warning about missing native PRNG
    logger("io.ktor.util.random", ERROR)

    // Hikari is quite loud in debug mode
    logger("com.zaxxer.hikari.HikariConfig", INFO)
    logger("com.zaxxer.hikari.pool.HikariPool", INFO)

    // JGit too
    logger("org.eclipse.jgit.internal.storage.file.FileSnapshot", INFO)
    logger("org.eclipse.jgit.internal.storage.file.PackFile", INFO)
    logger("org.eclipse.jgit.transport.PacketLineIn", INFO)
    logger("org.eclipse.jgit.transport.PacketLineOut", INFO)
    logger("org.eclipse.jgit.util.FS", INFO)
    logger("org.eclipse.jgit.util.SystemReader", INFO)
}

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS Z} | %5level | %40.40logger{40} | %msg%n"
    }

    target = ConsoleTarget.SystemErr
}

appender("FILE", FileAppender) {
    file = "output.log"

    encoder(PatternLayoutEncoder) {
        pattern = "%d{yyyy-MM-dd HH:mm:ss:SSS Z} | %5level | %40.40logger{40} | %msg%n"
    }
}

root(defaultLevel, ["CONSOLE", "FILE"])
