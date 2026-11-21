#!/bin/sh
set -e
cd "$(dirname "$0")"
docker compose pull backend frontend
docker compose up -d --wait --wait-timeout 120 backend frontend
