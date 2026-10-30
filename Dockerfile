# ---- 1. Build the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- 2. Install PayPal's MCP server once, at a pinned version, using Node's official image
FROM node:22-slim AS mcp
WORKDIR /mcp
RUN npm install --omit=dev --no-audit --no-fund @paypal/mcp@1.8.1

# ---- 3. Runtime: Java 21 + just the Node binary + the installed MCP server + the jar
FROM eclipse-temurin:21-jre
WORKDIR /app
# Node's binary needs the C++ runtime library; usually already present, this makes sure.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libstdc++6 \
 && rm -rf /var/lib/apt/lists/*
COPY --from=mcp /usr/local/bin/node /usr/local/bin/node
COPY --from=mcp /mcp/node_modules /app/mcp/node_modules
COPY --from=build /app/target/*.jar app.jar
RUN useradd --system --uid 1001 proofpay && chown -R proofpay /app
USER proofpay
# Start the pre-installed server directly: no npx, no download.
ENV PAYPAL_MCP_COMMAND="node /app/mcp/node_modules/@paypal/mcp/dist/index.js --tools=all"
# Java and the Node MCP server share Render's 512 MB.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]