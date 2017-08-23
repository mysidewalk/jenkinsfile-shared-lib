#!/usr/bin/env groovy

String call() {
  if (params.ACTION in [PROD_DEPLOY, PROD_PREDEPLOY]) {
    environment = PROD
  }
  else {
    environment = BRANCH_TO_ENVIRONMENT[env.BRANCH_NAME] ?: LOCAL
  }
  environment
}
