pipeline {
    agent any

    environment {
        APP_NAME        = "gateway-server"

        // GHCR에 올릴 이미지 이름
        REGISTRY        = "ghcr.io"
        GH_OWNER        = "sparta-next-me"
        IMAGE_REPO      = "gateway-server"
        FULL_IMAGE      = "${REGISTRY}/${GH_OWNER}/${IMAGE_REPO}:latest"

        CONTAINER_NAME  = "gateway-server"
        HOST_PORT       = "3000"   // EC2에서 노출 포트
        CONTAINER_PORT  = "3000"   // Spring server.port
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh '''
                  ./gradlew clean test --no-daemon
                  ./gradlew bootJar --no-daemon
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                  docker build -t ${FULL_IMAGE} .
                """
            }
        }

        stage('Push Image') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'ghcr-credential',   // Jenkins에 만든 GHCR 토큰
                        usernameVariable: 'REGISTRY_USER',
                        passwordVariable: 'REGISTRY_TOKEN'
                    )
                ]) {
                    sh """
                      echo "$REGISTRY_TOKEN" | docker login ghcr.io -u "$REGISTRY_USER" --password-stdin
                      docker push ${FULL_IMAGE}
                    """
                }
            }
        }

        stage('Deploy') {
            steps {
                sh """
                  # 기존 컨테이너 있으면 정지 + 삭제
                  if [ \$(docker ps -aq -f name=${CONTAINER_NAME}) ]; then
                    docker stop ${CONTAINER_NAME} || true
                    docker rm ${CONTAINER_NAME} || true
                  fi

                  # 새 컨테이너 실행 (GHCR 이미지 사용)
                  docker run -d --name ${CONTAINER_NAME} \\
                    -p ${HOST_PORT}:${CONTAINER_PORT} \\
                    ${FULL_IMAGE}
                """
            }
        }
    }
}
