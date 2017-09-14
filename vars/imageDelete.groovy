#!/usr/bin/env groovy

void call(String image) {
  echo "Deleting ${image}."
  sh "docker rmi ${image}"
  sh "gcloud container images untag -q ${image}"
}
