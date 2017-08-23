#!/usr/bin/env groovy

String call(String property) {
  sh(script: "grep ${property}= ${ENVFILE}", returnStdout: true).tokenize('=')[1].trim()
}
