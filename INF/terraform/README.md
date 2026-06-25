# Terraform 운영 기준

## 목적

`INF/terraform`은 AWS에 생성되는 운영 인프라를 코드로 관리한다.

초기 범위는 `S2 prod primary`, `RDS`, `ElastiCache`, `S3`, `Route53`이다.
`S1`은 SSAFY 제공 서버이므로 Terraform 생성 대상이 아니며, `dev/Jenkins/build/운영도구` 서버와 S1 도메인 대상 외부 서버로만 취급한다.

## 디렉터리 구조

```text
INF/terraform/
  bootstrap/             # remote state S3 bucket과 DynamoDB lock table 생성
    main.tf
    variables.tf
    outputs.tf
    versions.tf
  envs/
    prod/                 # 운영 환경 진입점
      main.tf
      variables.tf
      outputs.tf
      versions.tf
```

초기에는 모듈을 과하게 나누지 않고 prod 환경에서 한 번에 plan 가능한 구조로 둔다.
리소스가 늘어나면 `modules/network`, `modules/ec2`, `modules/rds`처럼 분리한다.

## 환경 변수

루트 `.env.terraform`을 사용한다.

```bash
source .env.terraform
```

PowerShell에서는 아래처럼 불러온다.

```powershell
Get-Content .env.terraform | Where-Object { $_ -and $_ -notmatch '^#' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
```

## 실행 순서

### 1. Remote State Bootstrap

실제 운영 인프라를 만들기 전에 state 저장소부터 만든다.
bootstrap은 state bucket과 lock table만 만들며, 이 단계의 local state는 작고 변경 빈도가 낮다.

```bash
cd INF/terraform/bootstrap

terraform init
terraform fmt -recursive
terraform validate
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

bootstrap으로 생성한 bucket/table 이름을 기준으로 `envs/prod/backend.tf`를 로컬에 직접 작성한다.

```bash
cd ../envs/prod
```

`backend.tf`는 환경별 실제 state backend 설정이므로 저장소에 올리지 않는다.

### 2. Prod Plan

```bash
cd INF/terraform/envs/prod

terraform init
terraform fmt -recursive
terraform validate
terraform plan -out=prod.tfplan
```

`terraform apply`는 plan 결과와 비용을 확인한 뒤 별도 승인 후 실행한다.

## 현재 설계 결정

- Region은 `ap-northeast-2`를 기본값으로 둔다.
- Terraform prod state는 S3 backend와 DynamoDB lock으로 관리한다.
- `terraform.tfstate`, `terraform.tfvars`, `backend.tf`, `*.tfplan`은 Git에 올리지 않는다.
- 운영 전용 VPC를 생성하고 public/private subnet을 분리한다.
- S2는 public subnet에 배치하고, RDS와 Redis는 private subnet group에 배치한다.
- S2 EC2는 월 총비용 20만원 상한을 고려해 `t3.medium`으로 시작한다.
- S2에서 backend, GraphHopper runtime, AI Flask server의 메모리 부족이 확인되면 같은 x86 계열인 `t3.large`로 승급한다.
- S3는 subnet에 배치되는 리소스가 아니므로 public access block과 S3 Gateway VPC Endpoint로 보호한다.
- S3 Gateway VPC Endpoint는 S2 public route table과 data private route table 양쪽에 연결한다.
- RDS와 Redis는 public endpoint를 열지 않고 S2 security group에서만 접근하게 둔다.
- Redis는 AUTH token과 transit encryption을 사용한다. `.env.terraform`의 `TF_VAR_redis_auth_token`과 `.env.prod`의 `REDIS_PASSWORD`는 같은 값으로 맞춘다.
- 따라서 운영 WAS와 prod GraphHopper runtime은 S2에서 실행하는 것을 기준으로 한다.
- `api.${root_domain}`, `ai.${root_domain}`, `admin.${root_domain}`은 S2 Elastic IP로 연결한다.
- `api.dev.${root_domain}`과 `ai.dev.${root_domain}`은 S1 dev stack으로 연결한다.
- AI Flask 원 포트는 외부에 직접 열지 않고 S2 Nginx 또는 backend 내부 호출로 제어한다.
- HTTPS 인증서는 Terraform apply 이후 DNS 전파와 S2 서비스 기동을 확인한 뒤 S2에서 `certbot --nginx`로 발급한다.
- Grafana, Portainer, SonarQube, PLG 같은 운영도구 도메인은 S1 고정 IP가 있으면 A 레코드로 연결하고, 고정 IP가 없으면 `s1_public_domain`으로 CNAME 연결한다.
- Route53 record는 Hosted Zone ID가 입력된 경우에만 생성한다.

## 2026-05-20 반영 상태

- bootstrap remote state bucket과 DynamoDB lock table 생성 완료
- prod Terraform backend는 S3 remote state 기준으로 초기화 완료
- S2 prod primary, RDS, ElastiCache, S3, Route53 운영 리소스 apply 완료
- `api.busaneumgil.com`, `ai.busaneumgil.com`, `admin.busaneumgil.com`은 S2 Elastic IP로 연결 완료
- S2 prod runtime은 backend, AI Flask server, 관리자 웹, GraphHopper blue/green runtime을 실행한다.
- 2026-05-20 확인 기준 `https://api.busaneumgil.com/health`, `https://api.busaneumgil.com/health/graphhopper`, `https://ai.busaneumgil.com/health`, `https://admin.busaneumgil.com/health`는 모두 정상 응답한다.
- `jenkins/grafana/portainer/sonarqube/plg.busaneumgil.com`은 S1 도메인 CNAME으로 연결 완료
- `api.dev.busaneumgil.com`, `ai.dev.busaneumgil.com`은 S1 dev stack CNAME으로 연결 완료
- Terraform apply는 반드시 plan 검토 후 수동 승인으로 진행한다.
