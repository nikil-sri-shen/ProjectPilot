pipeline {
    agent any

    environment {
        IMAGE_NAME = "projectpilot"
        CONTAINER_NAME = "projectpilot-container"
        IMAGE_TAG = "latest"
        APP_PORT = "9090"     // change if needed
        CONTAINER_PORT = "9090"
    }

    options {
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                // If using "Pipeline from SCM", you can remove this stage
                git branch: 'main',
                    credentialsId: 'github-ssh-local',
                    url: 'git@github.com:nikil-sri-shen/ProjectPilot.git'
            }
        }

        stage('Build Application') {
            steps {
                sh '''
                    chmod +x gradlew
                    ./gradlew clean build -x test
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    sh "docker rmi -f ${IMAGE_NAME}:${IMAGE_TAG} || true"
                    sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                }
            }
        }

        stage('Stop Old Container') {
            steps {
                script {
                    sh "docker rm -f ${CONTAINER_NAME} || true"
                }
            }
        }

        stage('Run New Container') {
            steps {
                script {
                    sh """
                        docker run -d \
                        --name ${CONTAINER_NAME} \
                        -p ${APP_PORT}:${CONTAINER_PORT} \
                        ${IMAGE_NAME}:${IMAGE_TAG}
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ Deployment Successful!"
            echo "🚀 App running at: http://localhost:${APP_PORT}"
        }
        failure {
            echo "❌ Pipeline Failed!"
        }
    }
}
