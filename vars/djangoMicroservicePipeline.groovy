#!/usr/bin/env groovy

/**
 *  Pipeline for building, testing, releasing, and pre/deploying a django microservice Docker image.
 *
 *  Dependencies: curl, docker, docker-compose, gcloud-sdk, git, jq, make, pssh, xargs
 *  Jenkins Plugins: ansiColor, Slack Notification Plugin
 *
 *  Resources:
 *    https://jenkins.io/user-handbook.pdf
 *    https://jenkins.io/doc/book/pipeline/
 *    https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki
 */


def call(String githubProject, String serviceName){
  SERVICE = $serviceName

  // Docker/GCR constants
  IMAGE_BASE = 'gcr.io/mindmixer-sidewalk'
  IMAGE_BASE_SERVICE = "${IMAGE_BASE}/${SERVICE}"

  // Environment constants
  EDGE = 'edge'
  LOCAL = 'local'
  MASTER = 'master'
  PROD = 'prod'
  STAGE = 'stage'

  // Environment Maps
  BRANCH_TO_ENVIRONMENT = [
    (EDGE): EDGE,
    (MASTER): STAGE,
  ]
  ENVIRONMENT_TO_RELEASE = [
    (EDGE): 'gamma',
    (PROD): 'latest',
    (STAGE): 'beta',
  ]

  // Deployment Types
  ABANDON_PREDEPLOY = 'ABANDON_PREDEPLOY'
  EDGE_DEPLOY = 'EDGE_DEPLOY'
  PROD_DEPLOY = 'PROD_DEPLOY'
  PROD_PRED
  pipeline {
    agent any
    environment {
      COMPOSE_PROJECT_NAME = ''
      ENVFILE = 'envfile'
      ENVIRONMENT = ''
      ETCD_HOST = 'config-1.c.mindmixer-sidewalk.internal'
      GCE_INSTANCES = null
      IMAGE = ''
      IMAGE_RELEASE = ''
      IMAGE_RELEASE_PRE = ''
      // Workflow Flags
      BUILDABLE = false
      TESTABLE = false
      RELEASABLE = false
      PREDEPLOYABLE = false
      DEPLOYABLE = false
    }
    parameters {
      choice(
        name: 'ACTION',
        choices: 'none\nABANDON_PREDEPLOY\nEDGE_DEPLOY\nPROD_PREDEPLOY\nPROD_DEPLOY',
        description: """ABANDON_PREDEPLOY
    1) Removes git tag "latest-prerelease"
    2) Removes GCR tags "latest-prerelease"
    3) Removes "latest-prerelease" image from GCE instances
    Must be on branch "master" to run this deployment.

  EDGE_DEPLOY
    1) Pulls "gamma-prerelease" docker images on edge GCE instance
    2) Promotes GCR docker images labeled "gamma-prerelease" to "gamma"
    3) Adds git tag "gamma" to "gamma-prerelease" tagged commit
    4) Restarts edge GCE services to use new "gamma" docker images
    Must be on branch "edge" to run this deployment.

  PROD_PREDEPLOY
    1) Promotes GCR docker images labeled "beta" to "latest-prerelease"
    2) Adds git tag "latest-prerelease" to "beta" tagged commit
    3) Adds GCR tag of "TAG" to docker images
    4) Adds git tag of "TAG" with "TAG_MESSAGE" to "latest-prerelease" tagged commit
    5) Pulls "latest-prerelease" docker images on prod GCE instances
    6) Locks stage by preventing new image builds of master until PROD_DEPLOY or ABANDON_PREDEPLOY have been run
    Must be on branch "master" to run this deployment.

  PROD_DEPLOY
    1) Promotes GCR docker images labeled "latest-prerelease" to "latest"
    2) Restarts prod GCE services to use new "latest" docker images
    Must be on branch "master" to run this deployment.""",
      )
      string(
        name: 'TAG',
        defaultValue: 'none',
        description: 'Git and GCR Docker image tag name. (e.g. 2017-01-30-v1) Only used by PROD_PREDEPLOY.',
      )
      text(
        name: 'TAG_MESSAGE',
        defaultValue: 'release candidate',
        description: 'Git tag message. (e.g. "This is the first release of reports.") Only used by PROD_PREDEPLOY.',
      )
    }
    options {
      buildDiscarder(logRotator(numToKeepStr: '20'))
      disableConcurrentBuilds()
      skipDefaultCheckout()
    }
    stages {
      stage('Setup') {
        steps {
          script {
            COMPOSE_PROJECT_NAME = "${SERVICE}_${env.BRANCH_NAME.toLowerCase()}_${env.BUILD_ID}"
            ENVIRONMENT = getEnvironment()
            IMAGE = "${SERVICE}:${env.BRANCH_NAME.toLowerCase()}_${env.BUILD_ID}"
            if (params.ACTION == PROD_PREDEPLOY) {
              // Set prod images to 'beta' for testing against prod environment db w/o rebuilding
              IMAGE = "${IMAGE_BASE_SERVICE}:beta"
            }
            IMAGE_RELEASE = ENVIRONMENT_TO_RELEASE[ENVIRONMENT] ?: env.BRANCH_NAME.toLowerCase()
            IMAGE_RELEASE_PRE = "${IMAGE_RELEASE}-prerelease"
            // Workflow Flags (can't reference params in environment block)
            BUILDABLE = params.ACTION != ABANDON_PREDEPLOY && (
              ENVIRONMENT == LOCAL
              || ENVIRONMENT == STAGE
              || ENVIRONMENT == EDGE && params.ACTION != EDGE_DEPLOY
              || ENVIRONMENT == PROD && params.ACTION == PROD_PREDEPLOY
            )
            TESTABLE = params.ACTION != ABANDON_PREDEPLOY && (
              ENVIRONMENT == LOCAL
              || ENVIRONMENT == STAGE
              || ENVIRONMENT == EDGE && params.ACTION != EDGE_DEPLOY
              || ENVIRONMENT == PROD && params.ACTION == PROD_PREDEPLOY
            )
            RELEASABLE = params.ACTION != ABANDON_PREDEPLOY && (
              ENVIRONMENT == STAGE
              || ENVIRONMENT == EDGE && params.ACTION != EDGE_DEPLOY
              || ENVIRONMENT == PROD && params.ACTION == PROD_PREDEPLOY
            )
            PREDEPLOYABLE = params.ACTION != ABANDON_PREDEPLOY && (
              ENVIRONMENT == STAGE
              || ENVIRONMENT == EDGE && params.ACTION == EDGE_DEPLOY
              || ENVIRONMENT == PROD && params.ACTION == PROD_PREDEPLOY
            )
            DEPLOYABLE = params.ACTION != ABANDON_PREDEPLOY && (
              ENVIRONMENT == STAGE
              || ENVIRONMENT == EDGE && params.ACTION == EDGE_DEPLOY
              || ENVIRONMENT == PROD && params.ACTION == PROD_DEPLOY
            )
            // Input validation
            if (params.ACTION == ABANDON_PREDEPLOY && env.BRANCH_NAME != MASTER) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "master" to abandon a prod pre-deploy.')
            }
            else if (params.ACTION == EDGE_DEPLOY && env.BRANCH_NAME != EDGE) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "edge" to deploy to edge environment.')
            }
            else if (params.ACTION in [PROD_DEPLOY, PROD_PREDEPLOY] && env.BRANCH_NAME != MASTER) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "master" to pre-deploy or deploy to prod.')
            }
            else if (params.TAG in [EDGE, MASTER]) {
              currentBuild.result = 'ABORTED'
              error('TAG may not be an environment branch name.')
            }
            // Environment State Evaluation
            if (env.BRANCH_NAME == MASTER && isStageLocked() && !(params.ACTION in [ABANDON_PREDEPLOY, PROD_DEPLOY, PROD_PREDEPLOY])) {
              currentBuild.result = 'ABORTED'
              error('Stage is now locked while pre-deploy testing is in progress. Ask QA for more information.')
            }
            checkout scm
            sh "git config user.name jenkins"
            sh "git remote set-url origin git@github.com:mysidewalk/${SERVICE}.git"
            sh 'git fetch --tags'
            if (params.ACTION == EDGE_DEPLOY && env.BRANCH_NAME == EDGE) {
              sh "git checkout gamma-prerelease"
            }
            else if (params.ACTION == PROD_PREDEPLOY) {
              sh "git checkout beta"
            }
            else if (params.ACTION == PROD_DEPLOY) {
              sh "git checkout latest-prerelease"
            }
            if (params.ACTION != 'none') {
              currentBuild.displayName += " - ${params.ACTION}"
            }
          }
          writeFile file: '.env', text: "COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}"
          writeFile (
            file: 'docker-compose.yml',
            text: """
  version: '2'
  services:
    etcd2env:
      image: ${IMAGE_BASE}/etcd2env
    mongo:
      image: mongo:3
      environment:
        SHELL: bash
        TERM: xterm
      expose:
        - "27017"
      hostname: mongo
    postgres:
      image: gcr.io/mindmixer-sidewalk/postgres:9.5
      environment:
        SHELL: bash
        TERM: xterm
      hostname: postgres
      networks:
        default:
          aliases:
           - db
      volumes_from:
        - postgres-data
    postgres-data:
      image: gcr.io/mindmixer-sidewalk/postgres-data:development
    ${SERVICE}:
      build: .
      depends_on:
        - postgres
        - mongo
      env_file:
        - ${ENVFILE}
      environment:
        REUSE_DB: 1
        SHELL: bash
        TERM: xterm
      expose:
        - "9003"
      hostname: ${SERVICE}
      image: ${IMAGE}
  """,
          )
          sh "touch ${ENVFILE}"
          script {
            if (ENVIRONMENT in [EDGE, PROD, STAGE]) {
              sh """
                docker-compose run --rm etcd2env \
                  sh -c 'python generate_env_vars.py ${ETCD_HOST} ${SERVICE} ${ENVIRONMENT} && python generate_env_vars.py ${ETCD_HOST} jenkins ${ENVIRONMENT}' \
                  > ${ENVFILE}
              """
            }
            if (PREDEPLOYABLE || DEPLOYABLE || params.ACTION == ABANDON_PREDEPLOY) {
              GCE_INSTANCES = parseEnvfile("GCE_${SERVICE}").tokenize(' ').toSet()
              pssh(GCE_INSTANCES, "sudo /etc/auth-gcr.sh")
            }
            if (params.ACTION == ABANDON_PREDEPLOY && isStageLocked()) {
              gitRemoveTag('latest-prerelease')
              imageDelete("${IMAGE_BASE_SERVICE}:latest-prerelease")
              pssh(GCE_INSTANCES, "sudo docker rmi ${IMAGE_BASE_SERVICE}:latest-prerelease || true")
              slackSend channel: '#developers', color: 'good', message: "Stage ${SERVICE} pipeline is now unlocked."
            }
          }
          sh 'sh /etc/auth-gcr.sh'
          sh 'docker pull gcr.io/mindmixer-sidewalk/python:onbuild'
          // Ignore pull failures for local-only images
          sh 'docker-compose pull --ignore-pull-failures --parallel'
        }
      }
      stage('Build Image') {
        when { expression { return BUILDABLE } }
        steps {
          script {
            if (params.ACTION in [EDGE_DEPLOY, PROD_PREDEPLOY]) {
              echo 'Skipping "${SERVICE}" docker image build'
            }
            else {
              echo 'Building "${SERVICE}" docker image'
              sh 'docker-compose build ${SERVICE}'
            }
          }
        }
      }
      stage('Test') {
        when { expression { return TESTABLE } }
        steps {
          parallel(
            unit: {
              sh "docker run --rm ${IMAGE} python manage.py test --settings=settings.unittesting"
            },
            integration: {
              sh 'make testintegration'
            }
          )
        }
      }
      stage('Release Docker Image to GCR') {
        when { expression { return RELEASABLE } }
        steps {
          script {
            if (ENVIRONMENT in [EDGE, STAGE]) {
              imagePush(IMAGE, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
              gitAddTag("${IMAGE_RELEASE_PRE}", 'passed CI')
            }
            if (ENVIRONMENT == PROD && params.ACTION == PROD_PREDEPLOY) {
              imagePush(IMAGE, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
              gitAddTag("${IMAGE_RELEASE_PRE}", 'release candidate')
            }
            if (ENVIRONMENT == PROD && params.TAG != 'none') {
              imagePush("${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}", "${IMAGE_BASE_SERVICE}:${params.TAG}")
              gitAddTag(params.TAG, params.TAG_MESSAGE)
              currentBuild.displayName += " - ${params.TAG}"
            }
          }
        }
      }
      stage('Predeploy Prerelease Image to GCE') {
        when { expression { return PREDEPLOYABLE } }
        steps {
          script {
            psshPullImage(GCE_INSTANCES, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
          }
        }
      }
      stage('Deploy: Promote Prerelease Images') {
        when { expression { return DEPLOYABLE } }
        steps {
          imagePush("${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}", "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE}")
          gitAddTag("${IMAGE_RELEASE}", 'ready for deploy')
        }
      }
      stage('Deploy: GCE Update Service') {
        when { expression { return DEPLOYABLE } }
        steps {
          script {
            gceUpdateService(GCE_INSTANCES, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE}", "${SERVICE}")
          }
        }
      }
    }
    post {
      always {
        sh 'make clean || true'
        script {
          if (!(params.ACTION in [EDGE_DEPLOY, PROD_DEPLOY, PROD_PREDEPLOY])) {
            sh "docker rmi ${IMAGE} || true"
          }
        }
        sh 'sudo chown -R jenkins *'
        deleteDir()
        imageDeleteUntagged(IMAGE_BASE_SERVICE)
      }
      failure {
        script {
          if (currentBuild.previousBuild?.result != 'FAILURE' && ENVIRONMENT in [EDGE, PROD, STAGE]) {
            slackSend channel: '#developers', color: 'danger', message: "${currentBuild.fullDisplayName} Failed. (<${env.RUN_DISPLAY_URL}|Open>)"
          }
        }
      }
      success {
        script {
          def message
          if (params.ACTION == PROD_PREDEPLOY) {
            message = "Stage ${SERVICE} pipeline is now locked while pre-deploy testing of tag '${TAG}' is in progress."
            message += " Please hold PR merges to ${SERVICE} until notified that stage ${SERVICE} pipeline is unlocked."
          }
          else if (params.ACTION in [EDGE_DEPLOY, PROD_DEPLOY]) {
            message = "Deployment of ${SERVICE} was successful to ${ENVIRONMENT}."
            if (params.ACTION == PROD_DEPLOY) {
              message += " Stage ${SERVICE} pipeline is now unlocked."
            }
          }
          else if (BUILDABLE && currentBuild.previousBuild?.result != 'SUCCESS' && ENVIRONMENT in [EDGE, PROD, STAGE]) {
            message = "${currentBuild.fullDisplayName} Back to normal."
          }
          if (message) {
            message += " (<${env.RUN_DISPLAY_URL}|Open>)"
            slackSend channel: '#developers', color: 'good', message: message
          }
        }
      }
    }
  }
}
