#!/bin/bash

# LOG="/var/log/app/app.log"
# ERROR_LOG="/var/log/app/app-error.log"

# exec >> "$LOG"
# exec 2>> "$ERROR_LOG"

echo "Launching harmony-api via harmony.main.harmony-api"
exec java $JAVA_OPTS -cp target/sharetribe-harmony.jar clojure.main -m harmony.main.harmony-api
