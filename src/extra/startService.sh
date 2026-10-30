#!/bin/bash

# Source any variables from the control plane
set -a
FILE=etc/pod_labels
if [ -f "$FILE" ]; then
    . $FILE
fi
set +a

# Print commands as they are executed, and treat unset variables as an error
set -x
set -u

# After jar directive, add any additional Spring Boot arguments if needed
java      \
           -jar access-decision-service.jar
