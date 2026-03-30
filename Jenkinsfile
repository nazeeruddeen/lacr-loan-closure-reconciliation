pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        APP_NAME = 'lacr-loan-closure-reconciliation'
        IMAGE_TAG = "${env.BUILD_NUMBER ?: 'latest'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Building ${APP_NAME} from ${env.BRANCH_NAME ?: 'local'}"
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

        stage('Docker Build') {
            steps {
                sh "docker build -t ${APP_NAME}:${IMAGE_TAG} -f backend/Dockerfile backend"
                sh "docker tag ${APP_NAME}:${IMAGE_TAG} ${APP_NAME}:latest"
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
                    sh "docker tag ${APP_NAME}:${IMAGE_TAG} ${DOCKER_REGISTRY_URL}/${APP_NAME}:${IMAGE_TAG}"
                    sh "docker push ${DOCKER_REGISTRY_URL}/${APP_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Kubernetes Apply') {
            when {
                expression { return env.KUBECONFIG?.trim() }
            }
            steps {
                sh 'kubectl apply -f k8s/'
                sh 'kubectl rollout status deployment/lacr-backend -n lacr-loan --timeout=120s'
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
