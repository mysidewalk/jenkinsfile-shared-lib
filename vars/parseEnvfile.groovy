#!/usr/bin/env groovy

String call(String property, String envfile) {
  sh(script: "grep ${property}= ${envfile}", returnStdout: true).tokenize('=')[1].trim()
}
