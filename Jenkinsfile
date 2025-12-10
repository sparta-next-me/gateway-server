pipeline {
    agent any

    environment {
        APP_NAME       = "gateway-server"        // 그냥 읽기용 이름
        IMAGE_NAME     = "gateway-server"        // Docker 이미지 이름
        IMAGE_TAG      = "latest"                // 태그
        FULL_IMAGE     = "${IMAGE_NAME}:${IMAGE_TAG}"

        CONTAINER_NAME = "gateway-server"        // 컨테이너 이름
        HOST_PORT      = "3000"                  // EC2에서 열 포트
        CONTAINER_PORT = "3000"                  // server.port (게이트웨이 포트)
    }

    stages {

        stage('Build & Test') {
            steps {
                sh './gradlew clean test'
                sh './gradlew bootJar'
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                  docker build -t ${FULL_IMAGE} .
                """
            }
        }

        stage('Deploy') {
            steps {
                sh """
                  # 기존 컨테이너 있으면 정지/삭제
                  if [ \$(docker ps -aq -f name=${CONTAINER_NAME}) ]; then
                    echo "Stopping existing container..."
                    docker stop ${CONTAINER_NAME} || true
                    docker rm ${CONTAINER_NAME} || true
                  fi

                  echo "Starting new gateway-server container..."
                  docker run -d --name ${CONTAINER_NAME} \\
                    -p ${HOST_PORT}:${CONTAINER_PORT} \\
                    ${FULL_IMAGE}
                """
            }
        }
    }
}
