#!/usr/bin/env groovy

ENVIRONMENT_TO_RELEASE = [
  (environment.EDGE): 'gamma',
  (environment.PROD): 'latest',
  (environment.STAGE): 'beta',
]

String call(String branchName) {
  if (params.ACTION in [deploymentType.PROD_DEPLOY, deploymentType.PROD_PREDEPLOY]) {
    environment = environment.PROD
  }
  else {
    environment = BRANCH_TO_ENVIRONMENT[env.BRANCH_NAME] ?: environment.LOCAL
  }
  environment
}
