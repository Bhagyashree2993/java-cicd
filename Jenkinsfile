// =============================================================================
// CI pipeline: GitHub → Jenkins → Maven → Test → SonarQube → Docker → Registry
//              → update Helm values in Git → Argo CD syncs to Kubernetes
// =============================================================================
// Jenkins prerequisites (see README.md, "Jenkins setup"):
//   Plugins     : Pipeline, Git, Docker Pipeline, SonarQube Scanner, JUnit,
//                 JaCoCo (optional), Credentials Binding, Timestamper
//   Tools       : JDK named 'jdk17', Maven named 'maven3'
//   SonarQube   : server named 'sonarqube' (Manage Jenkins → System)
//   Credentials : 'dockerhub-creds'  (Username/Password – registry login/token)
//                 'github-creds'     (Username/Password – GitHub user + PAT)
//   Agent       : must have Docker CLI + access to a Docker daemon
// =============================================================================

pipeline {
    agent any

    tools {
        jdk   'jdk17'
        maven 'maven3'
    }

    environment {
        // ---- change these to match your setup ----
        REGISTRY        = 'docker.io'
        IMAGE_REPO      = 'guvibhagyashreekhandare/java-app'
        GIT_REPO_URL    = 'https://github.com/Bhagyashree2993/java-cicd.git'
        TARGET_BRANCH   = 'main'          // branch that deploys
        HELM_VALUES     = 'helm/java-app/values.yaml'
        GIT_USER_NAME   = 'jenkins-ci'
        GIT_USER_EMAIL  = 'jenkins-ci@example.com'
        // -------------------------------------------
        IMAGE_TAG       = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'local'}"
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // Avoid an endless loop: the "Update Helm values" stage below pushes
                    // a commit, which would otherwise trigger this pipeline again.
                    def lastMsg = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    if (lastMsg.contains('[skip ci]')) {
                        currentBuild.result = 'NOT_BUILT'
                        currentBuild.description = 'Skipped: GitOps image-tag commit'
                        env.SKIP_PIPELINE = 'true'
                        echo 'Commit was made by CI ([skip ci]) — skipping the rest of the pipeline.'
                    }
                    echo "Image tag for this build: ${env.IMAGE_TAG}"
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
                    archiveArtifacts artifacts: 'target/java-app.jar', fingerprint: true, onlyIfSuccessful: true
                }
            }
        }

        stage('SonarQube Analysis') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh 'mvn -B sonar:sonar -Dsonar.projectVersion=${IMAGE_TAG}'
                }
            }
        }

        stage('Quality Gate') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                // Requires a SonarQube webhook → http://<jenkins>/sonarqube-webhook/
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                sh '''
                    docker build \
                      --label org.opencontainers.image.revision=${GIT_COMMIT} \
                      --label org.opencontainers.image.version=${IMAGE_TAG} \
                      -t ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG} \
                      -t ${REGISTRY}/${IMAGE_REPO}:latest .
                '''
            }
        }

        stage('Image Scan (Trivy)') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                // Non-blocking by default; change --exit-code to 1 to fail on HIGH/CRITICAL.
                sh '''
                    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                      aquasec/trivy:latest image --no-progress --exit-code 0 \
                      --severity HIGH,CRITICAL ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG} || true
                '''
            }
        }

        stage('Push to Registry') {
            when { not { environment name: 'SKIP_PIPELINE', value: 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'REG_USER',
                                                  passwordVariable: 'REG_PASS')]) {
                    sh '''
                        echo "$REG_PASS" | docker login ${REGISTRY} -u "$REG_USER" --password-stdin
                        docker push ${REGISTRY}/${IMAGE_REPO}:${IMAGE_TAG}
                        docker push ${REGISTRY}/${IMAGE_REPO}:latest
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
                    // works for both Multibranch (BRANCH_NAME set) and plain Pipeline jobs
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

                        # Update image repository + tag in the Helm values file
                        sed -i "s|^\\(\\s*repository:\\).*|\\1 ${REGISTRY}/${IMAGE_REPO}|" ${HELM_VALUES}
                        sed -i "s|^\\(\\s*tag:\\).*|\\1 \\"${IMAGE_TAG}\\"|"               ${HELM_VALUES}
                        grep -nE 'repository:|tag:' ${HELM_VALUES}

                        git add ${HELM_VALUES}
                        if git diff --cached --quiet; then
                          echo "No change in Helm values."
                        else
                          git commit -m "ci: deploy java-app ${IMAGE_TAG} [skip ci]"
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
                    echo "✅ ${IMAGE_REPO}:${IMAGE_TAG} pushed. Argo CD will sync it to Kubernetes."
                }
            }
        }
        failure { echo '❌ Pipeline failed — check the stage logs above.' }
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
