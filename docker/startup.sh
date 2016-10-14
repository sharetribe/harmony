#!/bin/bash

set -e

# wait for mysql port
echo "Waiting for MySQL connection..."
docker/wait-for-it/wait-for-it.sh -t 30 -s "$DB_HOST:$DB_PORT"

echo "Launching harmony-api via harmony.main.harmony-api"
exec java $JAVA_OPTS -cp target/sharetribe-harmony.jar clojure.main -m harmony.main.harmony-api
