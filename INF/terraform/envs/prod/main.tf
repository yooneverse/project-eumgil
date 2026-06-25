locals {
  name_prefix         = "${var.project_name}-${var.environment}"
  admin_cidrs         = compact([for cidr in split(",", var.admin_cidrs) : trimspace(cidr)])
  public_subnet_cidrs = compact([for cidr in split(",", var.public_subnet_cidrs) : trimspace(cidr)])
  private_subnet_cidrs = compact([
    for cidr in split(",", var.private_subnet_cidrs) : trimspace(cidr)
  ])
  create_dns      = var.route53_zone_id != "" && var.root_domain != ""
  create_s1_a     = local.create_dns && var.s1_public_ip != ""
  create_s1_cname = local.create_dns && var.s1_public_ip == "" && var.s1_public_domain != ""
  create_s1_dns   = local.create_s1_a || local.create_s1_cname
  redis_authed    = var.redis_auth_token != ""
  api_domain      = local.create_dns ? "api.${var.root_domain}" : "_"
  ai_domain       = local.create_dns ? "ai.${var.root_domain}" : "_"
  admin_domain    = local.create_dns ? "admin.${var.root_domain}" : "_"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
  az_count = min(length(data.aws_availability_zones.available.names), length(local.public_subnet_cidrs), length(local.private_subnet_cidrs))
}

data "aws_ami" "ubuntu_2404" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

resource "aws_subnet" "public" {
  count = local.az_count

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${count.index + 1}"
    Tier = "public"
  }
}

resource "aws_subnet" "private" {
  count = local.az_count

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.private_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-private-${count.index + 1}"
    Tier = "private"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id, aws_route_table.private.id]

  tags = {
    Name = "${local.name_prefix}-s3-gateway-endpoint"
  }
}

resource "aws_security_group" "s2" {
  name        = "${local.name_prefix}-s2-sg"
  description = "S2 prod primary server security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH from administrators"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = local.admin_cidrs
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "RDS PostgreSQL access from S2 only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from S2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.s2.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-redis-sg"
  description = "Redis access from S2 only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Redis from S2"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.s2.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_iam_role" "s2" {
  name = "${local.name_prefix}-s2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "s2_ssm" {
  role       = aws_iam_role.s2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "s2" {
  name = "${local.name_prefix}-s2-profile"
  role = aws_iam_role.s2.name
}

resource "aws_instance" "s2" {
  ami                         = data.aws_ami.ubuntu_2404.id
  instance_type               = var.s2_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.s2.id]
  key_name                    = var.s2_key_pair_name
  iam_instance_profile        = aws_iam_instance_profile.s2.name
  associate_public_ip_address = true

  root_block_device {
    volume_size           = var.s2_ebs_size_gb
    volume_type           = "gp3"
    delete_on_termination = true
    encrypted             = true
  }

  user_data = <<-EOF
    #!/usr/bin/env bash
    set -euo pipefail
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl unzip nginx certbot python3-certbot-nginx
    snap install amazon-ssm-agent --classic || true
    systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service || true
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    cat >/etc/nginx/sites-available/eumgil-prod <<'NGINXCONF'
    server {
      listen 80;
      server_name ${local.api_domain};

      location = /graphhopper/healthcheck {
        proxy_pass http://127.0.0.1:8080/health/graphhopper;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
      }

      location = /graphhopper-blue/healthcheck {
        proxy_pass http://127.0.0.1:18990/healthcheck;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
      }

      location = /graphhopper-green/healthcheck {
        proxy_pass http://127.0.0.1:18992/healthcheck;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
      }

      location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
      }
    }

    server {
      listen 80;
      server_name ${local.ai_domain};

      location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
      }
    }
    NGINXCONF
    ln -sf /etc/nginx/sites-available/eumgil-prod /etc/nginx/sites-enabled/eumgil-prod
    rm -f /etc/nginx/sites-enabled/default
    nginx -t
    systemctl enable --now nginx
    usermod -aG docker ubuntu || true
  EOF

  tags = {
    Name = "${local.name_prefix}-s2"
    Role = "prod-primary"
  }
}

resource "aws_eip" "s2" {
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-s2-eip"
  }
}

resource "aws_eip_association" "s2" {
  allocation_id = aws_eip.s2.id
  instance_id   = aws_instance.s2.id
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgres" {
  identifier             = "${local.name_prefix}-postgres"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = var.db_allocated_storage_gb
  storage_type           = "gp3"
  storage_encrypted      = true
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  multi_az               = var.db_multi_az
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = true
  deletion_protection    = true
  apply_immediately      = false
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name_prefix}-redis-subnet-group"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${local.name_prefix}-redis"
  description                = "Redis for ${local.name_prefix}"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = local.redis_authed
  auth_token                 = local.redis_authed ? var.redis_auth_token : null
  automatic_failover_enabled = false
}

resource "aws_s3_bucket" "prod" {
  bucket = var.s3_bucket_name
}

resource "aws_s3_bucket_public_access_block" "prod" {
  count = var.s3_public_access_block ? 1 : 0

  bucket                  = aws_s3_bucket.prod.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "prod" {
  bucket = aws_s3_bucket.prod.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "prod" {
  bucket = aws_s3_bucket.prod.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_route53_record" "api_s2" {
  count = local.create_dns ? 1 : 0

  zone_id = var.route53_zone_id
  name    = local.api_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.s2.public_ip]
}

resource "aws_route53_record" "ai_s2" {
  count = local.create_dns ? 1 : 0

  zone_id = var.route53_zone_id
  name    = local.ai_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.s2.public_ip]
}

resource "aws_route53_record" "admin_s2" {
  count = local.create_dns ? 1 : 0

  zone_id = var.route53_zone_id
  name    = local.admin_domain
  type    = "A"
  ttl     = 60
  records = [aws_eip.s2.public_ip]
  # Allows Terraform to adopt a manually pre-created admin A record during rollout.
  allow_overwrite = true
}

resource "aws_route53_record" "jenkins_s1" {
  count = local.create_s1_a ? 1 : 0

  zone_id = var.route53_zone_id
  name    = "jenkins.${var.root_domain}"
  type    = "A"
  ttl     = 60
  records = [var.s1_public_ip]
}

resource "aws_route53_record" "jenkins_s1_cname" {
  count = local.create_s1_cname ? 1 : 0

  zone_id = var.route53_zone_id
  name    = "jenkins.${var.root_domain}"
  type    = "CNAME"
  ttl     = 60
  records = [var.s1_public_domain]
}

resource "aws_route53_record" "s1_ops" {
  for_each = local.create_s1_a ? toset(["grafana", "portainer", "sonarqube", "plg"]) : toset([])

  zone_id = var.route53_zone_id
  name    = "${each.key}.${var.root_domain}"
  type    = "A"
  ttl     = 60
  records = [var.s1_public_ip]
}

resource "aws_route53_record" "s1_ops_cname" {
  for_each = local.create_s1_cname ? toset(["grafana", "portainer", "sonarqube", "plg"]) : toset([])

  zone_id = var.route53_zone_id
  name    = "${each.key}.${var.root_domain}"
  type    = "CNAME"
  ttl     = 60
  records = [var.s1_public_domain]
}

resource "aws_route53_record" "s1_dev" {
  for_each = local.create_s1_a ? toset(["api.dev", "ai.dev"]) : toset([])

  zone_id = var.route53_zone_id
  name    = "${each.key}.${var.root_domain}"
  type    = "A"
  ttl     = 60
  records = [var.s1_public_ip]
}

resource "aws_route53_record" "s1_dev_cname" {
  for_each = local.create_s1_cname ? toset(["api.dev", "ai.dev"]) : toset([])

  zone_id = var.route53_zone_id
  name    = "${each.key}.${var.root_domain}"
  type    = "CNAME"
  ttl     = 60
  records = [var.s1_public_domain]
}
