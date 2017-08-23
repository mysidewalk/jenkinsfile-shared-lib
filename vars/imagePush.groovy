#!/usr/bin/env groovy

def call(String srcImage, String destImage) {
  echo "Pushing ${srcImage} to ${destImage}."
  sh "docker tag ${srcImage} ${destImage}"
  sh "docker push ${destImage}"
}
