#!/bin/bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=propertly
export DB_USER=rave
export DB_PASS=rave
export PORT=8080
export CORS_ORIGIN=http://localhost:5173

mvn compile exec:java -Dexec.mainClass=com.propertly.App -q
