# 1) Build stage: compile & create fat JAR with Shadow
FROM gradle:7.5.1-jdk17 AS builder
WORKDIR /home/gradle/project

# Copy everything and build
COPY --chown=gradle:gradle . .
# Run the shadowJar task, which produces build/libs/<name>-all.jar
RUN gradle clean shadowJar --no-daemon

# 2) Runtime stage: slim JRE with your fat JAR
FROM eclipse-temurin:17-jdk-focal
WORKDIR /app

# Copy the “all” jar from the builder
COPY --from=builder /home/gradle/project/build/libs/*-all.jar app.jar

# Run it
ENTRYPOINT ["java", "-jar", "app.jar"]