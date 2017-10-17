#!/usr/bin/env groovy


void call(String environment) {
  pipeline {
    agent any
    parameters {
      booleanParam(name: 'AUTHWALK', description: '')
      booleanParam(name: 'ELECTIONS', description: '')
      booleanParam(name: 'FRONTEND', description: '')
      booleanParam(name: 'MYSIDEWALK', description: '')
      booleanParam(name: 'TESSERACT', description: '')
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
    stages {
      stage('Deploy') {
        parallel {
          stage('Authwalk') {
            when { expression { return params.AUTHWALK } }
            steps {
              echo "../${service.AUTHWALK}/${environment}"
              #build job: "../${service.AUTHWALK}/${environment}", parameters: params
            }
          }
          stage('Elections') {
            when { expression { return params.ELECTIONS } }
            steps {
              echo "../${service.ELECTIONS}/${environment}"
              #build job: "../${service.ELECTIONS}/${environment}", parameters: params
            }
          }
          stage('Frontend') {
            when { expression { return params.FRONTEND } }
            steps {
              echo "../${service.FRONTEND}/${environment}"
              #build job: "../${service.FRONTEND}/${environment}", parameters: params
            }
          }
          stage('mySidewalk') {
            when { expression { return params.MYSIDEWALK } }
            steps {
              echo "../${service.MYSIDEWALK}/${environment}"
              #build job: "../${service.MYSIDEWALK}/${environment}", parameters: params
            }
          }
          stage('Tesseract') {
            when { expression { return params.TESSERACT } }
            steps {
              echo "../${service.TESSERACT}/${environment}"
              #build job: "../${service.TESSERACT}/${environment}", parameters: params
            }
          }
        }
      }
    }
  }
}
