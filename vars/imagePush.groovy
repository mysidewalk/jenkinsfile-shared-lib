#!/usr/bin/env groovy

void call(String srcImage, String destImage) {
  echo "Pushing ${srcImage} to ${destImage}."
  sh "docker tag ${srcImage} ${destImage}"
  sh "docker push ${destImage}"
}
