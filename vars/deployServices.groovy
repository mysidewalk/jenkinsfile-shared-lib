#!/usr/bin/env groovy

/**
 *  Pipeline deploying microservices in parallel.
 *
 *  Microservices: authwalk, elections, frontend, mysidewalk, tesseract
 *
 */


void call() {
  pipeline {
    agent any
    parameters {
      booleanParam(name: service.AUTHWALK)
      booleanParam(name: service.ELECTIONS)
      booleanParam(name: service.FRONTEND)
      booleanParam(name: service.MYSIDEWALK)
      booleanParam(name: service.TESSERACT)
      choice(
        name: parameter.ACTION,
        choices: parameter.ACTION_CHOICES,
        description: parameter.ACTION_DESCRIPTION,
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
      stage('Deploy') {
        stepsForParallel = [:]
        for (int i = 0; i < service.ALL.size(); i++) {
          String serviceName = service.ALL.get(i)
          String jobName = "../${serviceName}/test"
          def parameters = [
            string(name: parameter.ACTION, value: params.ACTION),
            string(name: parameter.TAG, value: params.TAG),
            string(name: parameter.TAG_MESSAGE, value: params.TAG_MESSAGE),
          ]
          stepsForParallel[serviceName] = buildJobStep(jobName, parameters)
        }
        stepsForParallel['failFast'] = false
        parallel stepsForParallel
      }
    }
    post {
      always {
        deleteDir()
      }
    }
  }
}
