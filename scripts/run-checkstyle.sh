#!/bin/bash

# Execute the maven checkstyle verification
mvn checkstyle:checkstyle

# Capture maven's exit status
MAVEN_STATUS=$?

if [ $MAVEN_STATUS -ne 0 ]; then
    echo "Checkstyle failed! Blocking execution." >&2
    # Exit with code 2 to inform Kiro to block the agent sequence
    exit 2
fi

# Exit with code 0 to proceed
exit 0
