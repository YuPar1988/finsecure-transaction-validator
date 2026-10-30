pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 60, unit: 'MINUTES')
    }

    triggers {
        // Poll as a fallback; a GitHub webhook triggers the job immediately when configured.
        pollSCM('H/5 * * * *')
    }

    parameters {
        choice(name: 'ACTION', choices: ['deploy', 'rollback'], description: 'deploy = full pipeline, rollback = roll the cluster back without building')
        string(name: 'ROLLBACK_REVISION', defaultValue: '', description: 'Deployment revision to roll back to (blank = previous revision)')
        booleanParam(name: 'RUN_SONAR', defaultValue: true, description: 'Run SonarQube analysis and wait for the quality gate')
        booleanParam(name: 'RUN_DEPENDENCY_CHECK', defaultValue: true, description: 'Run OWASP Dependency-Check (fails on CVSS >= 7)')
        string(name: 'NOTIFY_EMAIL', defaultValue: '', description: 'Where build notifications are sent (blank = DEFAULT_RECIPIENTS from Email Extension settings)')
    }

    environment {
        DOCKER_REPO      = "${env.DOCKERHUB_REPO ?: 'yupardocker/finsecure-transaction-validator'}"
        K8S_NAMESPACE    = 'finsecure'
        K8S_DEPLOYMENT   = 'transaction-validator'
        K8S_SERVICE      = 'finsecure-app'
        TRIVY_CACHE      = "${env.JENKINS_HOME ?: env.WORKSPACE}/.trivy-cache"
        DC_DATA_DIR      = "${env.JENKINS_HOME ?: env.WORKSPACE}/.dependency-check-data"
    }

    stages {
        stage('Checkout') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                checkout scm
                script {
                    sh 'chmod +x mvnw'
                    env.APP_VERSION = sh(script: './mvnw -q help:evaluate -Dexpression=project.version -DforceStdout', returnStdout: true).trim()
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.APP_VERSION}-${env.BUILD_NUMBER}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG} (${env.GIT_SHORT})"
                }
            }
        }

        stage('Build') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh './mvnw -B clean compile'
            }
        }

        stage('Test') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh './mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Package') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh './mvnw -B package -DskipTests'
                archiveArtifacts artifacts: 'target/transaction-validator.jar', fingerprint: true
            }
        }

        stage('Security Checks') {
            when { expression { params.ACTION == 'deploy' } }
            parallel {
                stage('SonarQube Analysis') {
                    when { expression { params.RUN_SONAR } }
                    steps {
                        withSonarQubeEnv('sonarqube') {
                            sh './mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectVersion=${IMAGE_TAG}'
                        }
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
                stage('OWASP Dependency-Check') {
                    when { expression { params.RUN_DEPENDENCY_CHECK } }
                    steps {
                        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                            sh './mvnw -B -Psecurity-scan dependency-check:check -DdataDirectory="${DC_DATA_DIR}"'
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Container Build') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh '''
                    docker build \
                      --build-arg APP_VERSION=${IMAGE_TAG} \
                      --label org.opencontainers.image.revision=${GIT_SHORT} \
                      -t ${DOCKER_REPO}:${IMAGE_TAG} .
                '''
            }
        }

        stage('Container Scan') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                sh '''
                    trivy image \
                      --cache-dir "${TRIVY_CACHE}" \
                      --severity HIGH,CRITICAL \
                      --ignore-unfixed \
                      --ignorefile .trivyignore \
                      --format table --output trivy-report.txt \
                      --exit-code 1 \
                      ${DOCKER_REPO}:${IMAGE_TAG} || SCAN_FAILED=1
                    cat trivy-report.txt
                    if [ -n "${SCAN_FAILED}" ]; then
                      echo "Security gate: HIGH/CRITICAL vulnerabilities found in the image. Release blocked."
                      exit 1
                    fi
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Push') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_TOKEN')]) {
                    sh '''
                        echo "${DOCKER_TOKEN}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker push ${DOCKER_REPO}:${IMAGE_TAG}
                        docker tag ${DOCKER_REPO}:${IMAGE_TAG} ${DOCKER_REPO}:latest
                        docker push ${DOCKER_REPO}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        sed -i "s|newName: .*|newName: docker.io/${DOCKER_REPO}|; s|newTag: .*|newTag: \\"${IMAGE_TAG}\\"|" k8s/kustomization.yaml
                        kubectl apply -k k8s/
                        kubectl -n ${K8S_NAMESPACE} annotate deployment/${K8S_DEPLOYMENT} \
                          kubernetes.io/change-cause="Jenkins build ${BUILD_NUMBER}: image ${IMAGE_TAG} (commit ${GIT_SHORT})" --overwrite

                        if ! kubectl -n ${K8S_NAMESPACE} rollout status deployment/${K8S_DEPLOYMENT} --timeout=300s; then
                          echo "Rollout did not complete, rolling back to the previous revision"
                          kubectl -n ${K8S_NAMESPACE} rollout undo deployment/${K8S_DEPLOYMENT}
                          kubectl -n ${K8S_NAMESPACE} rollout status deployment/${K8S_DEPLOYMENT} --timeout=300s
                          exit 1
                        fi
                        kubectl -n ${K8S_NAMESPACE} get pods -l app.kubernetes.io/name=${K8S_DEPLOYMENT} -o wide
                    '''
                }
            }
        }

        stage('Smoke Test') {
            when { expression { params.ACTION == 'deploy' } }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        HOST=$(kubectl -n ${K8S_NAMESPACE} get svc ${K8S_SERVICE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}{.status.loadBalancer.ingress[0].hostname}')
                        echo "Service address: ${HOST}"
                        for i in $(seq 1 20); do
                          if curl -fsS "http://${HOST}/health" | tee health.json | grep -q '"status":"UP"'; then
                            cat health.json; echo; exit 0
                          fi
                          sleep 6
                        done
                        echo "Health check failed, rolling back"
                        kubectl -n ${K8S_NAMESPACE} rollout undo deployment/${K8S_DEPLOYMENT}
                        exit 1
                    '''
                }
            }
        }

        stage('Rollback') {
            when { expression { params.ACTION == 'rollback' } }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl -n ${K8S_NAMESPACE} rollout history deployment/${K8S_DEPLOYMENT}
                        if [ -n "${ROLLBACK_REVISION}" ]; then
                          kubectl -n ${K8S_NAMESPACE} rollout undo deployment/${K8S_DEPLOYMENT} --to-revision=${ROLLBACK_REVISION}
                        else
                          kubectl -n ${K8S_NAMESPACE} rollout undo deployment/${K8S_DEPLOYMENT}
                        fi
                        kubectl -n ${K8S_NAMESPACE} rollout status deployment/${K8S_DEPLOYMENT} --timeout=300s
                        kubectl -n ${K8S_NAMESPACE} get deployment ${K8S_DEPLOYMENT} -o jsonpath='{.spec.template.spec.containers[0].image}{"\\n"}'
                    '''
                }
            }
        }
    }

    post {
        success {
            emailext(
                to: "${params.NOTIFY_EMAIL ?: '$DEFAULT_RECIPIENTS'}",
                subject: "SUCCESS: ${env.JOB_NAME} ${currentBuild.displayName}",
                mimeType: 'text/html',
                body: """<p>Pipeline <b>${env.JOB_NAME}</b> ${currentBuild.displayName} finished successfully.</p>
                         <p>Action: ${params.ACTION}<br>Image: ${env.DOCKER_REPO}:${env.IMAGE_TAG ?: '-'}</p>
                         <p><a href="${env.BUILD_URL}">Open build</a></p>"""
            )
        }
        failure {
            emailext(
                to: "${params.NOTIFY_EMAIL ?: '$DEFAULT_RECIPIENTS'}",
                subject: "FAILED: ${env.JOB_NAME} ${currentBuild.displayName}",
                mimeType: 'text/html',
                attachLog: true,
                body: """<p>Pipeline <b>${env.JOB_NAME}</b> ${currentBuild.displayName} <b>failed</b>.</p>
                         <p>Nothing was deployed past the failing stage. The console log is attached.</p>
                         <p><a href="${env.BUILD_URL}console">Open console output</a></p>"""
            )
        }
        always {
            sh 'docker image prune -f >/dev/null 2>&1 || true'
        }
    }
}
