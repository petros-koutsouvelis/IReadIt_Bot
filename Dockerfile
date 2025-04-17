# Use a slim JDK
FROM eclipse-temurin:17-jdk-focal

# Create app directory
WORKDIR /app

# Copy the built JAR into the container
# (make sure you run `./gradlew build` beforehand)
COPY build/libs/IReadIt_Bot-1.0.jar app.jar

# Expose no ports (Discord bots don’t need one)
# ENV for Java memory tuning (optional)
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Run the JAR on startup
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]