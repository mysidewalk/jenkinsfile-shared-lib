#!/usr/bin/env groovy

@Library('jenkinsfile-shared-lib') import buildJobStep, parameter


List<Object> call(List<Object> params) {
  stepsForParallel = [:]
  for (int i = 0; i < service.ALL.size(); i++) {
    String serviceName = service.ALL.get(i)
    String jobName = "../${serviceName}/test"
    def parameters = [
      string(name: parameter.ACTION, value: params.ACTION),
      string(name: parameter.TAG, value: params.TAG),
      string(name: parameter.TAG_MESSAGE, value: params.TAG_MESSAGE),
    ]
    stepsForParallel[serviceName] = buildJobStep(jobName, parameters)
  }
  return stepsForParallel
}
