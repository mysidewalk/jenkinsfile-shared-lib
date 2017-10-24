#!/usr/bin/env groovy

/**
 *  Pipeline for building and pre/deploying mySidewalk services in parallel
 */


void call(String branch, String actionChoices) {
  pipeline {
    agent any
    options {
      buildDiscarder(logRotator(numToKeepStr: '20'))
      disableConcurrentBuilds()
      skipDefaultCheckout()
    }
    parameters {
      booleanParam(name: 'AUTHWALK', description: '')
      booleanParam(name: 'ELECTIONS', description: '')
      booleanParam(name: 'FRONTEND', description: '')
      booleanParam(name: 'MYSIDEWALK', description: '')
      booleanParam(name: 'TESSERACT', description: '')
      choice(
        name: parameter.ACTION,
        choices: actionChoices,
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
    stages {
      stage('Setup') {
        steps {
          script {
            parameters_included = [
              string(name: parameter.ACTION, value: params.ACTION),
              string(name: parameter.TAG, value: params.TAG),
              string(name: parameter.TAG_MESSAGE, value: params.TAG_MESSAGE),
            ]
          }
        }
      }
      stage('Deploy') {
        parallel {
          stage('Authwalk') {
            when { expression { return params.AUTHWALK } }
            steps {
              build job: "../${service.AUTHWALK}/${environment}", parameters: parameters_included
            }
          }
          stage('Elections') {
            when { expression { return params.ELECTIONS } }
            steps {
              build job: "../${service.ELECTIONS}/${environment}", parameters: parameters_included
            }
          }
          stage('Frontend') {
            when { expression { return params.FRONTEND } }
            steps {
              build job: "../${service.FRONTEND}/${environment}", parameters: parameters_included
            }
          }
          stage('mySidewalk') {
            when { expression { return params.MYSIDEWALK } }
            steps {
              build job: "../${service.MYSIDEWALK}/${environment}", parameters: parameters_included
            }
          }
          stage('Tesseract') {
            when { expression { return params.TESSERACT } }
            steps {
              build job: "../${service.TESSERACT}/${environment}", parameters: parameters_included
            }
          }
        }
      }
    }
  }
}
