#!/usr/bin/env groovy

void call(String job, List<Object> parameters) {
  return {
    node {
      build job: $job, parameters: $parameters
    }
  }
}
