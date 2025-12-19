pipeline {
  agent any

  environment {
    // ===== GHCR 레지스트리 설정 =====
    REGISTRY   = "ghcr.io"
    GH_OWNER   = "sparta-next-me"
    IMAGE_REPO = "gateway-server"

    // ===== K8s 배포 타겟 =====
    NAMESPACE  = "next-me"
    DEPLOYMENT = "gateway-server"
    KUBECONFIG_CRED_ID = "k3s-kubeconfig" // Jenkins에 등록한 kubeconfig ID

    // [설명] 쿠버네티스 매니페스트 파일명 (루트 디렉토리에 있다고 가정)
    MANIFEST_FILE = "gateway-server.yaml"
    KUBECTL_BIN = "${WORKSPACE}/kubectl"
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
          chmod +x gradlew
          ./gradlew clean bootJar --no-daemon
        '''
      }
    }

    stage('Compute Image Tags') {
      steps {
        script {
          // [중요] 불변성 유지를 위해 커밋 SHA 태그 생성
          def sha = sh(script: "git rev-parse --short=12 HEAD", returnStdout: true).trim()
          env.IMAGE_TAG = sha
          env.IMAGE_SHA    = "${REGISTRY}/${GH_OWNER}/${IMAGE_REPO}:${env.IMAGE_TAG}"
          env.IMAGE_LATEST = "${REGISTRY}/${GH_OWNER}/${IMAGE_REPO}:latest"
        }
      }
    }

    stage('Docker Build & Push') {
      steps {
        withCredentials([
          usernamePassword(credentialsId: 'ghcr-credential', usernameVariable: 'USER', passwordVariable: 'TOKEN')
        ]) {
          sh '''
            set -e
            docker build -t "$IMAGE_SHA" -t "$IMAGE_LATEST" .
            echo "$TOKEN" | docker login ghcr.io -u "$USER" --password-stdin
            docker push "$IMAGE_SHA"
            docker push "$IMAGE_LATEST"
          '''
        }
      }
    }

    stage('Deploy to k3s') {
      steps {
        // [설명] kubectl 설치 (서버에 설치되어 있지 않을 경우 대비)
        sh '''
          if [ ! -x "$KUBECTL_BIN" ]; then
            curl -sSL -o "$KUBECTL_BIN" "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
            chmod +x "$KUBECTL_BIN"
          fi
        '''

        withCredentials([file(credentialsId: "${KUBECONFIG_CRED_ID}", variable: 'KUBECONFIG_FILE')]) {
          sh '''
            set -e
            export KUBECONFIG="$KUBECONFIG_FILE"

            # 1) gateway-server.yaml 적용 (Service와 Deployment 구조 생성)
            "$KUBECTL_BIN" apply -f "$MANIFEST_FILE" -n "$NAMESPACE"

            # 2) [핵심] 롤링 업데이트 실행
            # Deployment의 이미지를 방금 푸시한 SHA 태그 버전으로 변경합니다.
            "$KUBECTL_BIN" -n "$NAMESPACE" set image deployment/"$DEPLOYMENT" \
              gateway-server="$IMAGE_SHA"

            # 3) 무중단 배포 상태 모니터링
            # 새 파드가 다 뜰 때까지 기다립니다. (timeout 5분)
            "$KUBECTL_BIN" -n "$NAMESPACE" rollout status deployment/"$DEPLOYMENT" --timeout=300s

            # 4) 배포 결과 확인
            "$KUBECTL_BIN" -n "$NAMESPACE" get pods -l app=gateway-server
          '''
        }
      }
    }
  }

  post {
    success {
      echo "Gateway Server 배포 성공!"
    }
    always {
      // 빌드 노드 용량 확보를 위해 로컬 이미지 삭제
      sh "docker rmi $IMAGE_SHA || true"
      sh "docker rmi $IMAGE_LATEST || true"
    }
  }
}