#!/bin/bash

echo "Launching harmony-api via harmony.main.harmony-api"
exec java $JAVA_OPTS -cp target/sharetribe-harmony.jar clojure.main -m harmony.main.harmony-api
