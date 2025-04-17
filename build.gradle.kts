plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1" // use a working version
}

application.mainClass = "org.passtoast.discordbot.Main"
group = "org.passtoast.discordbot.Bot"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.3.2")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

application {
    mainClass.set("org.passtoast.discordbot.Main") // Update with your real main class
}

// Optional: ensures UTF-8 encoding
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}