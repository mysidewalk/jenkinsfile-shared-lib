#!/usr/bin/env groovy

def call(String property) {
  sh(script: "grep ${property}= ${ENVFILE}", returnStdout: true).tokenize('=')[1].trim()
}
