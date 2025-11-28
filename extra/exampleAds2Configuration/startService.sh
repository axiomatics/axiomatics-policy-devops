#!/bin/bash

# Source any variables from the control plane
set -a
FILE=etc/pod_labels
if [ -f "$FILE" ]; then
    source $FILE
fi
set +a

# Print commands as they are executed, and treat unset variables as an error
set -x
set -u

export build=`grep -w 'identity:' domain.yaml | awk -F': ' '{print $2}' | head -n1 | tr -d '\n'`
echo "Build id: ${build}"

# After jar directive, add any additional Spring Boot arguments if needed
java      \
           -jar access-decision-service.jar \
           --build=${build}
