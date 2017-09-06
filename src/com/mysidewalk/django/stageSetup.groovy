#!/usr/bin/env groovy

void call() {
  writeFile (
    file: '.env',
    text: """
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}
ENVFILE=${ENVFILE}
IMAGE=${IMAGE}
IMAGE_BASE=${IMAGE_BASE}
SERVICE=${SERVICE}
"""
          )
          writeFile (
            file: 'docker-compose.yml',
            text: dockerComposeFile ?: """
version: '2'
services:
  ${SERVICE}:
    build: .
    depends_on:
      - postgres
    env_file:
      - ${ENVFILE}
    environment:
      REUSE_DB: 1
    hostname: ${SERVICE}
    image: ${IMAGE}
  etcd2env:
    image: ${IMAGE_BASE}/etcd2env
  postgres:
    hostname: postgres
    image: ${IMAGE_BASE}/postgres:9.5
    networks:
      default:
        aliases:
         - db
    volumes_from:
      - postgres-data
  postgres-data:
    image: ${IMAGE_BASE}/postgres-data:development
""",
  )
  sh "touch ${ENVFILE}"
  script {
    if (ENVIRONMENT in [environment.EDGE, environment.PROD, environment.STAGE]) {
    sh """
      docker-compose run --rm etcd2env \
        sh -c 'python generate_env_vars.py ${ETCD_HOST} ${SERVICE} ${ENVIRONMENT} && python generate_env_vars.py ${ETCD_HOST} jenkins ${ENVIRONMENT}' \
        > ${ENVFILE}
    """
    }
  }
  sh 'sh /etc/auth-gcr.sh'
  sh 'docker pull gcr.io/mindmixer-sidewalk/python:onbuild'
  // Ignore pull failures for local-only images
  sh 'docker-compose pull --ignore-pull-failures --parallel'
  script {
    if (TESTABLE || DEPLOYABLE) {
      sh "docker-compose up -d postgres"
    }
  }
