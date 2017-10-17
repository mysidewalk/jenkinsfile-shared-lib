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
      $parameters
    }
    stages {
      stage('Deploy') {
        parallel {
          stage('Authwalk') {
            when { expression { return params.AUTHWALK } }
            steps {
              echo "../${service.AUTHWALK}/edge"
            }
          }
          stage('Elections') {
            when { expression { return params.ELECTIONS } }
            steps {
              echo "../${service.ELECTIONS}/edge"
            }
          }
          stage('Frontend') {
            when { expression { return params.FRONTEND } }
            steps {
              echo "../${service.FRONTEND}/edge"
            }
          }
          stage('mySidewalk') {
            when { expression { return params.MYSIDEWALK } }
            steps {
              echo "../${service.MYSIDEWALK}/edge"
            }
          }
          stage('Tesseract') {
            when { expression { return params.TESSERACT } }
            steps {
              echo "../${service.TESSERACT}/edge"
            }
          }
        }
      }
    }
  }
}
