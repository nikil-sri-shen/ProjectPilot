pipeline {
    agent any

    environment {
        IMAGE_NAME = "projectpilot"
        CONTAINER_NAME = "projectpilot-container"
        IMAGE_TAG = "latest"
        APP_PORT = "9090"
        CONTAINER_PORT = "9090"
    }

    options {
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-ssh-local',
                    url: 'git@github.com:nikil-sri-shen/ProjectPilot.git'
            }
        }

        stage('Build Application') {
            steps {
                sh '''
                    chmod +x gradlew
                    ./gradlew clean shadowJar -x test
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker rmi -f ${IMAGE_NAME}:${IMAGE_TAG} || true"
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
            }
        }

        stage('Stop Old Container') {
            steps {
                sh "docker rm -f ${CONTAINER_NAME} || true"
            }
        }

        stage('Run New Container') {
            steps {
                withCredentials([
                    string(credentialsId: 'mongo-uri', variable: 'MONGO_URI'),
                    string(credentialsId: 'mongo-db-name', variable: 'MONGO_DB_NAME'),
                    string(credentialsId: 'jwt-secret', variable: 'JWT_SECRET'),
                    string(credentialsId: 'from-email', variable: 'FROM_EMAIL'),
                    string(credentialsId: 'app-password', variable: 'APP_PASSWORD'),
                    string(credentialsId: 'smtp-host', variable: 'SMTP_HOST'),
                    string(credentialsId: 'smtp-port', variable: 'SMTP_PORT')
                ]) {

                    sh '''
                        docker run -d \
                        --name $CONTAINER_NAME \
                        -p $APP_PORT:$CONTAINER_PORT \
                        -e SERVER_PORT=$APP_PORT \
                        -e MONGO_URI=$MONGO_URI \
                        -e MONGO_DB_NAME=$MONGO_DB_NAME \
                        -e JWT_SECRET=$JWT_SECRET \
                        -e FROM_EMAIL=$FROM_EMAIL \
                        -e APP_PASSWORD=$APP_PASSWORD \
                        -e SMTP_HOST=$SMTP_HOST \
                        -e SMTP_PORT=$SMTP_PORT \
                        $IMAGE_NAME:$IMAGE_TAG
                    '''
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
