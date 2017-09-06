#!/usr/bin/env groovy

package com.mysidewalk.docker

/**
 *  Pipeline for building, testing, releasing, and pre/deploying a Docker image.
 *
 *  Dependencies: curl, docker, docker-compose, gcloud-sdk, git, jq, make, pssh, xargs
 *  Jenkins Plugins: ansiColor, Slack Notification Plugin
 *
 *  Resources:
 *    https://jenkins.io/user-handbook.pdf
 *    https://jenkins.io/doc/book/pipeline/
 *    https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki
 */


def buildDockerImage(String serviceName, String dockerComposeFile='', Closure stageSetup=null, Closure stageTest=null) {
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
        name: 'ACTION',
        choices: "none\n${deploymentType.ABANDON_PREDEPLOY}\n${deploymentType.EDGE_DEPLOY}\n${deploymentType.PROD_PREDEPLOY}\n${deploymentType.PROD_DEPLOY}",
        description: """${deploymentType.ABANDON_PREDEPLOY}
  1) Removes git tag "latest-prerelease"
  2) Removes GCR tags "latest-prerelease"
  3) Removes "latest-prerelease" image from GCE instances
  Must be on branch "master" to run this deployment.

${deploymentType.EDGE_DEPLOY}
  1) Pulls "gamma-prerelease" docker images on edge GCE instance
  2) Promotes GCR docker images labeled "gamma-prerelease" to "gamma"
  3) Adds git tag "gamma" to "gamma-prerelease" tagged commit
  4) Restarts edge GCE services to use new "gamma" docker images
  Must be on branch "edge" to run this deployment.

${deploymentType.PROD_PREDEPLOY}
  1) Promotes GCR docker images labeled "beta" to "latest-prerelease"
  2) Adds git tag "latest-prerelease" to "beta" tagged commit
  3) Adds GCR tag of "TAG" to docker images
  4) Adds git tag of "TAG" with "TAG_MESSAGE" to "latest-prerelease" tagged commit
  5) Pulls "latest-prerelease" docker images on prod GCE instances
  6) Locks stage by preventing new image builds of master until PROD_DEPLOY or ABANDON_PREDEPLOY have been run
  Must be on branch "master" to run this deployment.

${deploymentType.PROD_DEPLOY}
  1) Promotes GCR docker images labeled "latest-prerelease" to "latest"
  2) Restarts prod GCE services to use new "latest" docker images
  Must be on branch "master" to run this deployment.""",
      )
      string(
        name: 'TAG',
        defaultValue: 'none',
        description: 'Git and GCR Docker image tag name. (e.g. 2017-01-30-v1) Only used by ${deploymentType.PROD_PREDEPLOY}.',
      )
      text(
        name: 'TAG_MESSAGE',
        defaultValue: 'release candidate',
        description: 'Git tag message. (e.g. "This is the first release of reports.") Only used by ${deploymentType.PROD_PREDEPLOY}.',
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
            IMAGE = "${SERVICE}:${env.BRANCH_NAME.toLowerCase()}_${env.BUILD_ID}"
            if (params.ACTION == deploymentType.PROD_PREDEPLOY) {
              // Set prod images to 'beta' for testing against prod environment db w/o rebuilding
              IMAGE = "${IMAGE_BASE_SERVICE}:beta"
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
            else if (params.ACTION == deploymentType.EDGE_DEPLOY && env.BRANCH_NAME != environment.EDGE) {
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
            if (PREDEPLOYABLE || DEPLOYABLE || params.ACTION == deploymentType.ABANDON_PREDEPLOY) {
              GCE_INSTANCES = parseEnvfile("GCE_${SERVICE.toUpperCase()}", ENVFILE).tokenize(' ').toSet()
              pssh(GCE_INSTANCES, "sudo /etc/auth-gcr.sh")
            }
            if (params.ACTION == deploymentType.ABANDON_PREDEPLOY && isStageLocked(IMAGE_BASE_SERVICE)) {
              gitRemoveTag('latest-prerelease')
              imageDelete("${IMAGE_BASE_SERVICE}:latest-prerelease")
              pssh(GCE_INSTANCES, "sudo docker rmi ${IMAGE_BASE_SERVICE}:latest-prerelease || true")
              slackSend channel: '#developers', color: 'good', message: "Stage ${SERVICE} pipeline is now unlocked."
            }
          }
          if (stageSetup) {
            stageSetup()
          }
        }
      }
      stage('Build Image') {
        when { expression { return BUILDABLE } }
        steps {
          if (!whenHack(BUILDABLE)) {
            return
          }
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
          if (!whenHack(TESTABLE)) {
             return
          }
          if (stageTest) {
            stageTest()
          }
          else {
            sh "make test"
          }
        }
      }
      stage('Release Docker Image to GCR') {
        when { expression { return RELEASABLE } }
        steps {
          if (!whenHack(RELEASABLE)) {
            return
          }
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
          if (!whenHack(PREDEPLOYABLE)) {
            return
          }
          script {
            psshPullImage(GCE_INSTANCES, "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}")
          }
        }
      }
      stage('Deploy: Promote Prerelease Images') {
        when { expression { return DEPLOYABLE } }
        steps {
          if (!whenHack(DEPLOYABLE)) {
            return
          }
          imagePush("${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE_PRE}", "${IMAGE_BASE_SERVICE}:${IMAGE_RELEASE}")
          gitAddTag("${IMAGE_RELEASE}", 'ready for deploy')
        }
      }
      stage('Deploy: Make Service') {
        when { expression { return DEPLOYABLE } }
        steps {
          script {
            if (!whenHack(DEPLOYABLE)) {
              return
            }
            sh "make ${SERVICE}"
          }
        }
      }
      stage('Deploy: GCE Update Service') {
        when { expression { return DEPLOYABLE } }
        steps {
          if (!whenHack(DEPLOYABLE)) {
            return
          }
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
            message += " Please hold PR merges to ${SERVICE} until notified that stage ${SERVICE} pipeline is unlocked."
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
