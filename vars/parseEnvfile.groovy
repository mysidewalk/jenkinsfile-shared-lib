#!/usr/bin/env groovy

String call(String property, String envfile) {
  sh(script: "grep -i ${property}= ${envfile}", returnStdout: true).tokenize('=')[1].trim()
}
