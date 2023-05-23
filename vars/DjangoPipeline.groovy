#!/usr/bin/env groovy

/**
 *  Pipeline for building, testing, releasing, and pre/deploying a django microservice Docker image.
 *
 *  Dependencies: curl, docker, docker-compose, gcloud-sdk, git, jq, make, pssh, xargs
 *  Jenkins Plugins: Slack Notification Plugin
 *
 *  Resources:
 *    https://jenkins.io/user-handbook.pdf
 *    https://jenkins.io/doc/book/pipeline/
 *    https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki
 */


void call(String serviceName, String dockerComposeFile='') {
  COMPOSE_PROJECT_NAME = ''
  ENVFILE = 'envfile'
  ENVIRONMENT = ''
  ENVIRONMENT_TO_RELEASE = [
    (environment.EDGE): 'gamma',
    (environment.PROD): 'latest',
    (environment.STAGE): 'beta',
  ]
  ETCD_HOST = 'config-1.c.mindmixer-sidewalk.internal'
  GCE_INSTANCES = ''
  IMAGE = ''
  IMAGE_BASE = 'gcr.io/mindmixer-sidewalk'
  IMAGE_BASE_SERVICE = "${IMAGE_BASE}/${serviceName}"
  IMAGE_RELEASE = ''
  IMAGE_RELEASE_PRE = ''
  SERVICE = serviceName
  // Workflow Flags
  BUILDABLE = false
  TESTABLE = false
  RELEASABLE = false
  PREDEPLOYABLE = false
  DEPLOYABLE = false

  pipeline {
    agent any
    parameters {
      choice(
        name: parameter.ACTION,
        choices: "none\n${deploymentType.ABANDON_PREDEPLOY}\n${deploymentType.EDGE_DEPLOY}\n${deploymentType.PROD_PREDEPLOY}\n${deploymentType.PROD_DEPLOY}",
        description: """${deploymentType.ABANDON_PREDEPLOY_DESCRIPTION}
${deploymentType.EDGE_DEPLOY_DESCRIPTION}
${deploymentType.PROD_PREDEPLOY_DESCRIPTION}
${deploymentType.PROD_DEPLOY_DESCRIPTION}""",
      )
      string(
        name: parameter.TAG,
        defaultValue: parameter.TAG_DEFAULT_VALUE,
        description: parameter.TAG_DESCRIPTION,
      )
      text(
        name: parameter.TAG_MESSAGE,
        defaultValue: parameter.TAG_MESSAGE_DEFAULT_VALUE,
        description: parameter.TAG_MESSAGE_DESCRIPTION,
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
            ENVIRONMENT = getEnvironment(params.ACTION, env.BRANCH_NAME.toLowerCase())
            if (params.ACTION == deploymentType.PROD_PREDEPLOY) {
              // Set prod images to 'beta' for testing against prod environment db w/o rebuilding image
              IMAGE = "${IMAGE_BASE_SERVICE}:beta"
            }
            else if (params.ACTION == deploymentType.PROD_DEPLOY) {
              // Set prod images to 'latest-prerelease' migrating prod environment db w/o rebuilding image
              IMAGE = "${IMAGE_BASE_SERVICE}:latest-prerelease"
            }
            else {
              IMAGE = "${SERVICE}:${env.BRANCH_NAME.toLowerCase()}_${env.BUILD_ID}"
            }
            IMAGE_RELEASE = ENVIRONMENT_TO_RELEASE[ENVIRONMENT] ?: env.BRANCH_NAME.toLowerCase()
            IMAGE_RELEASE_PRE = "${IMAGE_RELEASE}-prerelease"
            // Workflow Flags (can't reference params in environment block)
            BUILDABLE = params.ACTION != deploymentType.ABANDON_PREDEPLOY && (
              ENVIRONMENT == environment.LOCAL
              || ENVIRONMENT == environment.STAGE
              || ENVIRONMENT == environment.EDGE && params.ACTION != deploymentType.EDGE_DEPLOY
              || ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_PREDEPLOY
            )
            TESTABLE = params.ACTION != deploymentType.ABANDON_PREDEPLOY && (
              ENVIRONMENT == environment.LOCAL
              || ENVIRONMENT == environment.STAGE
              || ENVIRONMENT == environment.EDGE && params.ACTION != deploymentType.EDGE_DEPLOY
              || ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_PREDEPLOY
            )
            RELEASABLE = params.ACTION != deploymentType.ABANDON_PREDEPLOY && (
              ENVIRONMENT == environment.STAGE
              || ENVIRONMENT == environment.EDGE && params.ACTION != deploymentType.EDGE_DEPLOY
              || ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_PREDEPLOY
            )
            PREDEPLOYABLE = params.ACTION != deploymentType.ABANDON_PREDEPLOY && (
              ENVIRONMENT == environment.STAGE
              || ENVIRONMENT == environment.EDGE && params.ACTION == deploymentType.EDGE_DEPLOY
              || ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_PREDEPLOY
            )
            DEPLOYABLE = params.ACTION != deploymentType.ABANDON_PREDEPLOY && (
              ENVIRONMENT == environment.STAGE
              || ENVIRONMENT == environment.EDGE && params.ACTION == deploymentType.EDGE_DEPLOY
              || ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_DEPLOY
            )
            // Input validation
            if (params.ACTION == deploymentType.ABANDON_PREDEPLOY && env.BRANCH_NAME != branch.MASTER) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "master" to abandon a prod pre-deploy.')
            }
            else if (params.ACTION == deploymentType.EDGE_DEPLOY && env.BRANCH_NAME != branch.EDGE) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "edge" to deploy to edge environment.')
            }
            else if (params.ACTION in [deploymentType.PROD_DEPLOY, deploymentType.PROD_PREDEPLOY] && env.BRANCH_NAME != branch.MASTER) {
              currentBuild.result = 'ABORTED'
              error('Must be on branch "master" to pre-deploy or deploy to prod.')
            }
            else if (params.TAG in [branch.EDGE, branch.MASTER]) {
              currentBuild.result = 'ABORTED'
              error('TAG may not be an environment branch name.')
            }
            // Environment State Evaluation
            if (env.BRANCH_NAME == branch.MASTER && isStageLocked(IMAGE_BASE_SERVICE) && !(params.ACTION in [deploymentType.ABANDON_PREDEPLOY, deploymentType.PROD_DEPLOY, deploymentType.PROD_PREDEPLOY])) {
              currentBuild.result = 'ABORTED'
              error('Stage is now locked while pre-deploy testing is in progress. Ask QA for more information.')
            }
            checkout scm
            sh "git config user.name jenkins"
            sh "git remote set-url origin git@github.com:mysidewalk/${SERVICE}.git"
            sh 'git fetch --tags'
            if (params.ACTION == deploymentType.EDGE_DEPLOY && env.BRANCH_NAME == branch.EDGE) {
              sh "git checkout gamma-prerelease"
            }
            else if (params.ACTION == deploymentType.PROD_PREDEPLOY) {
              sh "git checkout beta"
            }
            else if (params.ACTION == deploymentType.PROD_DEPLOY) {
              sh "git checkout latest-prerelease"
            }
            if (params.ACTION != 'none') {
              currentBuild.displayName += " - ${params.ACTION}"
            }
          }
          script {
            env.COMPOSE_PROJECT_NAME = COMPOSE_PROJECT_NAME
            env.ENVFILE = ENVFILE
            env.IMAGE = IMAGE
            env.IMAGE_BASE = IMAGE_BASE
            env.SERVICE = SERVICE
          }
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
    image: ${IMAGE_BASE}/postgres:14
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
            if (PREDEPLOYABLE || DEPLOYABLE || params.ACTION == deploymentType.ABANDON_PREDEPLOY) {
              GCE_INSTANCES = parseEnvfile("GCE_${SERVICE.toUpperCase()}", ENVFILE).tokenize(' ').toSet()
              pssh(GCE_INSTANCES, "sudo /opt/bin/auth-gcr.sh")
            }
            if (params.ACTION == deploymentType.ABANDON_PREDEPLOY && isStageLocked(IMAGE_BASE_SERVICE)) {
              gitRemoveTag('latest-prerelease')
              imageDelete("${IMAGE_BASE_SERVICE}:latest-prerelease")
              pssh(GCE_INSTANCES, "sudo docker rmi ${IMAGE_BASE_SERVICE}:latest-prerelease || true")
              slackSend channel: '#developers', color: 'good', message: "Stage ${SERVICE} pipeline is now unlocked."
            }
          }
          sh 'sh /opt/bin/auth-gcr.sh'
          sh 'docker pull gcr.io/mindmixer-sidewalk/python:onbuild'
          // Ignore pull failures for local-only images
          sh 'docker-compose pull --ignore-pull-failures --parallel'
          script {
            if (TESTABLE || DEPLOYABLE) {
              sh "docker-compose up -d postgres"
            }
          }
        }
      }
      stage('Build Image') {
        when { expression { return BUILDABLE } }
        steps {
          script {
            if (params.ACTION in [deploymentType.EDGE_DEPLOY, deploymentType.PROD_PREDEPLOY]) {
              echo "Skipping ${SERVICE} docker image build"
            }
            else {
              echo "Building ${SERVICE} docker image"
              sh "docker-compose build ${SERVICE}"
            }
          }
        }
      }
      stage('Test') {
        when { expression { return TESTABLE } }
        steps {
          parallel(
            unit: {
              sh 'make testunit'
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
            if (ENVIRONMENT in [environment.EDGE, environment.STAGE]) {
              imagePush(IMAGE, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
              gitAddTag("${IMAGE_RELEASE_PRE}", 'passed CI')
            }
            if (ENVIRONMENT == environment.PROD && params.ACTION == deploymentType.PROD_PREDEPLOY) {
              imagePush(IMAGE, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
              gitAddTag("${IMAGE_RELEASE_PRE}", 'release candidate')
            }
            if (ENVIRONMENT == environment.PROD && params.TAG != 'none') {
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
      stage('Deploy: Migrate Database') {
        when { expression { return DEPLOYABLE } }
        steps {
          script {
            sh "make ${SERVICE}"
          }
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
          if (!(params.ACTION in [deploymentType.EDGE_DEPLOY, deploymentType.PROD_DEPLOY, deploymentType.PROD_PREDEPLOY])) {
            sh "docker rmi ${IMAGE} || true"
          }
          if (params.TAG) {
            sh "docker rmi ${IMAGE_BASE_SERVICE}:${params.TAG} || true"
          }
        }
        sh 'sudo chown -R jenkins *'
        deleteDir()
        imageDeleteUntagged(IMAGE_BASE_SERVICE)
      }
      failure {
        script {
          if (currentBuild.previousBuild?.result != 'FAILURE' && ENVIRONMENT in [environment.EDGE, environment.PROD, environment.STAGE]) {
            slackSend channel: '#developers', color: 'danger', message: "${currentBuild.fullDisplayName} Failed. (<${env.RUN_DISPLAY_URL}|Open>)"
          }
        }
      }
      success {
        script {
          def message
          if (params.ACTION == deploymentType.PROD_PREDEPLOY) {
            message = "Stage ${SERVICE} pipeline is now locked while pre-deploy testing of tag '${TAG}' is in progress."
            message += " Please hold PR merges to master until notified that stage ${SERVICE} pipeline is unlocked."
          }
          else if (params.ACTION in [deploymentType.EDGE_DEPLOY, deploymentType.PROD_DEPLOY]) {
            message = "Deployment of ${SERVICE} was successful to ${ENVIRONMENT}."
            if (params.ACTION == deploymentType.PROD_DEPLOY) {
              message += " Stage ${SERVICE} pipeline is now unlocked."
            }
          }
          else if (BUILDABLE && currentBuild.previousBuild?.result != 'SUCCESS' && ENVIRONMENT in [environment.EDGE, environment.PROD, environment.STAGE]) {
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
