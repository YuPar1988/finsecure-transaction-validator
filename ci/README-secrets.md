# Jenkins secrets

Jenkins reads its credentials through Configuration as Code (`ci/jenkins/casc.yaml`).
Each `${NAME}` in that file is resolved from a file called `NAME` in the folder mounted at `/run/secrets`.
Keep this folder outside the repository and set `JENKINS_SECRETS_DIR` to its path in `ci/.env`.

| File | Content |
|------|---------|
| `JENKINS_ADMIN_PASSWORD` | Password for the Jenkins `admin` user |
| `DOCKERHUB_USER` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (Read & Write) |
| `AWS_ACCESS_KEY_ID` | Access key of the IAM user used for deployments |
| `AWS_SECRET_ACCESS_KEY` | Secret for that access key |
| `SMTP_USER` | Gmail address used to send notifications |
| `SMTP_PASSWORD` | Gmail app password |
| `NOTIFY_EMAIL` | Recipient of build notifications |
| `SONAR_TOKEN` | SonarQube token (generate in SonarQube: My Account → Security) |
| `kubeconfig` | `aws eks update-kubeconfig --name finsecure-eks --region ap-southeast-2 --kubeconfig kubeconfig` |

Files must not end with a newline.
