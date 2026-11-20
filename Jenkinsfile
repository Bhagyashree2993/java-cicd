// =============================================================================
// Habit Tracker (given HabitApp code) CI pipeline - Jenkins does CI only, never kubectl
//
//  GitHub -> Checkout -> Maven build & unit tests -> SonarQube + Quality Gate
//         -> Docker multi-stage build -> Trivy scan -> container smoke test
//         -> push versioned image to Docker Hub -> update Helm values in Git
//         -> Argo CD (GitOps) syncs the new version to Kubernetes
//
// Jenkins prerequisites:
//   Tools       : JDK 'jdk21' (the app needs Java 21), Maven 'maven3'
//   SonarQube   : server 'sonarqube' + webhook  http://jenkins:8080/sonarqube-webhook/
//   Credentials : 'dockerhub-creds' (user + access token), 'github-creds' (user + PAT),
//                 'sonar-token' (secret text, used by the SonarQube server config)
// =============================================================================

pipeline {
    agent any

    tools {
        jdk   'jdk21'
        maven 'maven3'
    }

    environment {
        REGISTRY       = 'docker.io'
        IMAGE_REPO     = 'guvibhagyashreekhandare/habit-tracker'
        GIT_REPO_URL   = 'https://github.com/Bhagyashree2993/java-cicd.git'
        TARGET_BRANCH  = 'main'
        HELM_VALUES    = 'helm/habit-tracker/values.yaml'
        GIT_USER_NAME  = 'jenkins-ci'
        GIT_USER_EMAIL = 'jenkins-ci@example.com'
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // The GitOps stage pushes a "[skip ci]" commit; don't rebuild for it.
                    def lastMsg = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    if (lastMsg.contains('[skip ci]')) {
                        currentBuild.result = 'NOT_BUILT'
                        currentBuild.description = 'Skipped: GitOps image-tag commit'
                        env.SKIP_PIPELINE = 'true'
                        echo 'Commit was made by CI ([skip ci]) - skipping.'
                    } else {
                        def appVersion = sh(script: "mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout",
                                            returnStdout: true).trim()
                        env.APP_VERSION = appVersion
                        // Semantic version + build number + git commit, e.g. 1.0.0-12-a1b2c3d
                        env.IMAGE_TAG = "${appVersion}-${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
                        currentBuild.description = "image ${env.IMAGE_TAG}"
                        echo "Version ${env.APP_VERSION}, image tag ${env.IMAGE_TAG}"
                    }
                }
            }
        }

        stage('Build & Unit Test') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    archiveArtifacts artifacts: 'target/habit-tracker.jar', fingerprint: true, onlyIfSuccessful: true
                }
            }
        }

        stage('SonarQube Analysis') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                withSonarQubeEnv('sonarqube') {
                    // The given pom.xml points sonar.host.url at localhost:9000; inside Jenkins the
                    // server is the one configured in withSonarQubeEnv, so override it here.
                    sh '''mvn -B sonar:sonar \
                          -Dsonar.host.url=${SONAR_HOST_URL} \
                          -Dsonar.projectKey=habit-tracker-app \
                          -Dsonar.projectName="Habit Tracker (HabitApp)" \
                          -Dsonar.projectVersion=${IMAGE_TAG}'''
                }
            }
        }

        stage('Quality Gate') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build (multi-stage)') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                sh '''
                    docker build \
                      --build-arg APP_VERSION=${IMAGE_TAG} \
                      --label org.opencontainers.image.revision=${GIT_COMMIT} \
                      -t ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG} \
                      -t ${REGISTRY}/${IMAGE_REPO}:latest .
                    docker image ls ${REGISTRY}/${IMAGE_REPO}
                '''
            }
        }

        stage('Image Scan (Trivy)') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                // Report-only: HIGH/CRITICAL findings are listed but do not fail the build.
                // The trivy-cache volume keeps the vulnerability DB between builds.
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v trivy-cache:/root/.cache/ \
                      aquasec/trivy:latest image --no-progress --scanners vuln \
                      --timeout 20m --exit-code 0 --severity HIGH,CRITICAL \
                      ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG} \
                    || echo "WARNING: Trivy scan could not complete (network/DB). Scan is non-blocking."
                '''
            }
        }

        stage('Container Smoke Test') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                // Start the image and exercise the given app's REST API on port 8080 before
                // publishing it. The output is saved as app-output.txt (build artifact).
                sh '''
                    docker rm -f habit-smoke >/dev/null 2>&1 || true
                    docker run -d --name habit-smoke ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}
                    for i in $(seq 1 30); do
                      if docker exec habit-smoke wget -qO- http://127.0.0.1:8080/api/habits >/dev/null 2>&1; then
                        echo "App is answering on port 8080 after ${i} tries"; break
                      fi
                      sleep 3
                      if [ "$i" = "30" ]; then docker logs habit-smoke; exit 1; fi
                    done
                    H='http://127.0.0.1:8080/api/habits'
                    {
                      echo "=== Habit Tracker application output (image ${IMAGE_TAG}) ==="
                      echo; echo "--- GET / (dashboard) ---"
                      docker exec habit-smoke wget -qO- http://127.0.0.1:8080/ | grep -o '<title>.*</title>'
                      echo; echo "--- POST /api/habits (create) ---"
                      docker exec habit-smoke wget -qO- --header 'Content-Type: application/json' \
                        --post-data '{"name":"Drink water","description":"8 glasses a day","frequency":"DAILY"}' $H
                      echo; echo "--- POST /api/habits (create) ---"
                      docker exec habit-smoke wget -qO- --header 'Content-Type: application/json' \
                        --post-data '{"name":"Read 10 pages","description":"before bed","frequency":"DAILY"}' $H
                      echo; echo "--- POST /api/habits/1/complete (yesterday and today) ---"
                      docker exec habit-smoke wget -qO- --post-data '' "$H/1/complete?date=$(date -d yesterday +%F 2>/dev/null || date -v-1d +%F)"
                      echo
                      docker exec habit-smoke wget -qO- --post-data '' $H/1/complete
                      echo; echo "--- GET /api/habits/1/stats ---"
                      docker exec habit-smoke wget -qO- $H/1/stats
                      echo; echo "--- GET /api/habits ---"
                      docker exec habit-smoke wget -qO- $H
                      echo; echo "--- container user ---"
                      docker exec habit-smoke id
                    } | tee app-output.txt
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'app-output.txt', allowEmptyArchive: true
                    sh 'docker rm -f habit-smoke >/dev/null 2>&1 || true'
                }
            }
        }

        stage('Push to Registry') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'REG_USER',
                                                  passwordVariable: 'REG_PASS')]) {
                    // Registry connections can drop on flaky networks: retry up to 6 times
                    sh '''
                        ok=0
                        for attempt in 1 2 3 4 5 6; do
                          if echo "$REG_PASS" | docker login ${REGISTRY} -u "$REG_USER" --password-stdin \
                             && docker push ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG} \
                             && docker push ${REGISTRY}/${IMAGE_REPO}:latest; then
                            ok=1; echo "Push succeeded on attempt ${attempt}"; break
                          fi
                          echo "Push attempt ${attempt} failed - retrying in 20s"; sleep 20
                        done
                        [ "$ok" = "1" ]
                    '''
                }
            }
            post {
                always { sh 'docker logout ${REGISTRY} || true' }
            }
        }

        stage('Update Helm values (GitOps)') {
            when {
                allOf {
                    not { environment name: 'SKIP_PIPELINE', value: 'true' }
                    expression { env.BRANCH_NAME == null || env.BRANCH_NAME == env.TARGET_BRANCH }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-creds',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_TOKEN')]) {
                    sh '''
                        set -e
                        git config user.name  "${GIT_USER_NAME}"
                        git config user.email "${GIT_USER_EMAIL}"
                        git fetch origin ${TARGET_BRANCH}
                        git checkout -B ${TARGET_BRANCH} origin/${TARGET_BRANCH}

                        sed -i "s|^\\(\\s*repository:\\).*|\\1 ${REGISTRY}/${IMAGE_REPO}|" ${HELM_VALUES}
                        sed -i "s|^\\(\\s*tag:\\).*|\\1 \\"${IMAGE_TAG}\\"|"               ${HELM_VALUES}
                        grep -nE 'repository:|tag:' ${HELM_VALUES}

                        git add ${HELM_VALUES}
                        if git diff --cached --quiet; then
                          echo "No change in Helm values."
                        else
                          git commit -m "ci: deploy habit-tracker ${IMAGE_TAG} [skip ci]"
                          REPO_PATH=$(echo ${GIT_REPO_URL} | sed 's|https://||')
                          git push https://${GIT_USER}:${GIT_TOKEN}@${REPO_PATH} HEAD:${TARGET_BRANCH}
                        fi
                    '''
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.SKIP_PIPELINE != 'true') {
                    echo "SUCCESS: ${IMAGE_REPO}:${IMAGE_TAG} pushed; Argo CD will deploy it to Kubernetes."
                }
            }
        }
        failure { echo 'FAILED - check the stage logs above.' }
        always {
            sh 'docker image prune -f >/dev/null 2>&1 || true'
            cleanWs()
        }
    }
}
