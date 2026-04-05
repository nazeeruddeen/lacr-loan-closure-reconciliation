pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        BACKEND_IMAGE = 'lacr-loan-closure-reconciliation'
        FRONTEND_IMAGE = 'lacr-loan-closure-reconciliation-frontend'
        IMAGE_TAG = "${env.BUILD_NUMBER ?: 'latest'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Building ${BACKEND_IMAGE} from ${env.BRANCH_NAME ?: 'local'}"
            }
        }

        stage('Test') {
            steps {
                dir('backend') {
                    sh 'mvn -B test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                dir('backend') {
                    sh 'mvn -B -DskipTests package'
                }
            }
        }

        stage('Frontend E2E') {
            steps {
                withEnv([
                        'CI=true',
                        'LACR_MYSQL_ROOT_PASSWORD=lacrRoot#2026',
                        'LACR_DB_PASSWORD=lacrDb#2026',
                        'LACR_CLOSUREOPS_PASSWORD=ClosureOps#2026',
                        'LACR_RECONLEAD_PASSWORD=ReconLead#2026',
                        'LACR_AUDITOR_PASSWORD=Auditor#2026',
                        'LACR_OPSADMIN_PASSWORD=OpsAdmin#2026',
                        'LACR_FRONTEND_HOST_PORT=4500',
                        'LACR_MYSQL_HOST_PORT=33063',
                        'LACR_REDIS_HOST_PORT=6380',
                        'LACR_MONGO_HOST_PORT=27018'
                ]) {
                    sh '''
                        set -euo pipefail
                        rm -rf frontend/playwright-report frontend/test-results
                        docker compose down -v --remove-orphans || true
                        docker compose up -d --build
                        ready=0
                        for attempt in $(seq 1 36); do
                          if curl -fsS http://127.0.0.1:8012/actuator/health/readiness >/dev/null && curl -fsS http://127.0.0.1:4500/ >/dev/null; then
                            ready=1
                            break
                          fi
                          sleep 5
                        done
                        if [ "$ready" -ne 1 ]; then
                          docker compose logs
                          exit 1
                        fi
                        docker run --rm --add-host=host.docker.internal:host-gateway \
                          -e CI=true \
                          -e PLAYWRIGHT_JUNIT_OUTPUT_NAME=test-results/e2e-results.xml \
                          -e LACR_E2E_BASE_URL=http://host.docker.internal:4500 \
                          -e LACR_E2E_API_BASE_URL=http://host.docker.internal:8012 \
                          -e LACR_E2E_PASSWORD="$LACR_CLOSUREOPS_PASSWORD" \
                          -v "$PWD/frontend:/work" \
                          -w /work \
                          mcr.microsoft.com/playwright:v1.59.1-noble \
                          sh -lc "npm ci && npx playwright test tests/golden-path.spec.ts --reporter=line,junit,html"
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'frontend/test-results/e2e-results.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'frontend/playwright-report/**,frontend/test-results/**'
                    sh 'docker compose down -v --remove-orphans || true'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${BACKEND_IMAGE}:${IMAGE_TAG} -f backend/Dockerfile backend"
                sh "docker tag ${BACKEND_IMAGE}:${IMAGE_TAG} ${BACKEND_IMAGE}:latest"
                sh "docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -f frontend/Dockerfile frontend"
                sh "docker tag ${FRONTEND_IMAGE}:${IMAGE_TAG} ${FRONTEND_IMAGE}:latest"
            }
        }

        stage('Docker Push') {
            when {
                expression { return env.DOCKER_REGISTRY_URL?.trim() }
            }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS')]) {
                    sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin ${DOCKER_REGISTRY_URL}"
                    sh "docker tag ${BACKEND_IMAGE}:${IMAGE_TAG} ${DOCKER_REGISTRY_URL}/${BACKEND_IMAGE}:${IMAGE_TAG}"
                    sh "docker push ${DOCKER_REGISTRY_URL}/${BACKEND_IMAGE}:${IMAGE_TAG}"
                    sh "docker tag ${BACKEND_IMAGE}:latest ${DOCKER_REGISTRY_URL}/${BACKEND_IMAGE}:latest"
                    sh "docker push ${DOCKER_REGISTRY_URL}/${BACKEND_IMAGE}:latest"
                    sh "docker tag ${FRONTEND_IMAGE}:${IMAGE_TAG} ${DOCKER_REGISTRY_URL}/${FRONTEND_IMAGE}:${IMAGE_TAG}"
                    sh "docker push ${DOCKER_REGISTRY_URL}/${FRONTEND_IMAGE}:${IMAGE_TAG}"
                    sh "docker tag ${FRONTEND_IMAGE}:latest ${DOCKER_REGISTRY_URL}/${FRONTEND_IMAGE}:latest"
                    sh "docker push ${DOCKER_REGISTRY_URL}/${FRONTEND_IMAGE}:latest"
                }
            }
        }

        stage('Kubernetes Apply') {
            when {
                expression { return env.KUBECONFIG?.trim() }
            }
            steps {
                sh 'kubectl apply -f k8s/00-namespace.yaml'
                sh 'kubectl apply -f k8s/01-configmap.yaml'
                sh 'kubectl apply -f k8s/02-secret.yaml'
                sh 'kubectl apply -f k8s/03-mysql.yaml'
                sh 'kubectl apply -f k8s/04-backend.yaml'
                sh 'kubectl apply -f k8s/05-redis.yaml'
                sh 'kubectl apply -f k8s/06-mongo.yaml'
                sh 'kubectl apply -f k8s/07-frontend.yaml'
                sh 'kubectl apply -f k8s/08-ingress.yaml'
                sh 'kubectl rollout status deployment/lacr-backend -n lacr-loan --timeout=120s'
                sh 'kubectl rollout status deployment/lacr-frontend -n lacr-loan --timeout=120s'
            }
        }
    }

    post {
        success {
            echo "LACR pipeline finished successfully."
        }
        failure {
            echo "LACR pipeline failed. Review the stage logs for the failing step."
        }
    }
}
