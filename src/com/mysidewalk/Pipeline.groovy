#!/usr/bin/env groovy

package com.mysidewalk

/**
 *  Pipeline deploying microservices in parallel.
 *
 *  Microservices: authwalk, elections, frontend, mysidewalk, tesseract
 *
 */


def deployServices() {
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
        steps {
          parallel(
            (service.AUTHWALK): {
              script {
                if (params.authwalk) {
                  build job "../${service.AUTHWALK}/test"
                }
              }
            },
            (service.ELECTIONS): {
              script {
                if (params.elections) {
                  build job "../${service.ELECTIONS}/test"
                }
              }
            },
            (service.FRONTEND): {
              script {
                if (params.frontend) {
                  build job "../${service.FRONTEND}/test"
                }
              }
            },
            (service.MYSIDEWALK): {
              script {
                if (params.mysidewalk) {
                  build job "../${service.MYSIDEWALK}/test"
                }
              }
            },
            (service.TESSERACT): {
              script {
                if (params.tesseract) {
                  build job "../${service.TESSERACT}/test"
                }
              }
            }
          )
        }
      }
    }
    post {
      always {
        deleteDir()
      }
    }
  }
}
