#!/bin/bash
# start-dev.sh - Запуск микросервисов для локальной разработки
set -e

echo "🚀 Starting infrastructure..."
docker compose up -d
sleep 5

echo "🧭 Starting Eureka Server..."
mvn spring-boot:run -pl eureka-server &
EUREKA_PID=$!
sleep 10

echo "🔍 Starting Fraud Service..."
mvn spring-boot:run -pl fraud &
sleep 5

echo "📩 Starting Notification Service..."
mvn spring-boot:run -pl notification &
sleep 5

echo "👤 Starting Customer Service (default profile)..."
mvn spring-boot:run -pl customer -Dspring.profiles.active=default
