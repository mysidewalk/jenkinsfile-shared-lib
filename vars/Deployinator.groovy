#!/usr/bin/env groovy


void call(String environment) {
  parameters = service.ALL.collect{
    booleanParam(name: $it.toUpperCase())
  }
  parameters += parameter.ALL
  parameters {
    $parameters
  }
  pipeline {
    agent any
    parameters {
      booleanParam(name: 'AUTHWALK')
      booleanParam(name: 'ELECTIONS')
      booleanParam(name: 'FRONTEND')
      booleanParam(name: 'MYSIDEWALK')
      booleanParam(name: 'TESSERACT')
    }
    stages {
      stage('Deploy') {
        parallel {
          stage('Authwalk') {
            when { expression { return params.AUTHWALK } }
            steps {
              echo "../${service.AUTHWALK}/${environment}"
            }
          }
          stage('Elections') {
            when { expression { return params.ELECTIONS } }
            steps {
              echo "../${service.ELECTIONS}/${environment}"
            }
          }
          stage('Frontend') {
            when { expression { return params.FRONTEND } }
            steps {
              echo "../${service.FRONTEND}/${environment}"
            }
          }
          stage('mySidewalk') {
            when { expression { return params.MYSIDEWALK } }
            steps {
              echo "../${service.MYSIDEWALK}/${environment}"
            }
          }
          stage('Tesseract') {
            when { expression { return params.TESSERACT } }
            steps {
              echo "../${service.TESSERACT}/${environment}"
            }
          }
        }
      }
    }
  }
}
