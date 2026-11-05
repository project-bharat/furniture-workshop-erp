FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server/ ./
ENV PORT=8080
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/app/bin/server"]
