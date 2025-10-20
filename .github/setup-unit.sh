#!/bin/bash
set -e

COMPOSE_FILE="docker-compose-ci.yml"

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH."
    echo "   Please install Docker to run this script."
    exit 1
fi

# Check if Docker Compose is available and determine version
if docker compose version &> /dev/null; then
    # Docker Compose v2 (integrated with Docker CLI)
    DC_CMD="docker compose"
    echo "✅ Using Docker Compose v2"
elif command -v docker-compose &> /dev/null; then
    # Docker Compose v1 (standalone)
    DC_CMD="docker-compose"
    echo "✅ Using Docker Compose v1"
else
    echo "❌ Docker Compose is not installed or not in PATH."
    echo "   Please install Docker Compose to run this script."
    exit 1
fi

echo "🧹 Cleaning up any existing containers..."
$DC_CMD -f $COMPOSE_FILE down -v --remove-orphans || true

echo "🚀 Starting ActiveMQ Artemis..."
$DC_CMD -f $COMPOSE_FILE up -d activemq

echo "⏳ Waiting for ActiveMQ Artemis to be ready..."
timeout=60
elapsed=0
while ! curl -f -s http://localhost:8161/console/ > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Timeout waiting for ActiveMQ Artemis to start"
        echo "   Check logs with: $DC_CMD -f $COMPOSE_FILE logs activemq"
        exit 1
    fi
    echo "   Waiting for ActiveMQ console... (${elapsed}s/${timeout}s)"
    sleep 5
    elapsed=$((elapsed + 5))
done

echo "✅ ActiveMQ Artemis is ready!"
echo ""
echo "📋 Connection Details:"
echo "   JMS Broker URL: tcp://localhost:61616"
echo "   Web Console:    http://localhost:8161/console/"
echo "   Username:       admin"
echo "   Password:       admin"
echo ""
echo "🔧 Useful commands:"
echo "   View logs:      $DC_CMD -f $COMPOSE_FILE logs -f activemq"
echo "   Stop services:  $DC_CMD -f $COMPOSE_FILE down"
echo "   Run tests:      ./gradlew test"
echo ""
