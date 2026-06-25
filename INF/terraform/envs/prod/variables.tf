variable "project_name" {
  description = "Project name used for resource names and tags."
  type        = string
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-northeast-2"
}

variable "root_domain" {
  description = "Root domain managed by Route53."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID. Leave empty to skip DNS records."
  type        = string
  default     = ""
}

variable "vpc_cidr" {
  description = "CIDR block for the dedicated production VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Comma-separated public subnet CIDR list. S2 is placed in the first public subnet."
  type        = string
  default     = "10.20.1.0/24,10.20.2.0/24"

  validation {
    condition     = length(compact([for cidr in split(",", var.public_subnet_cidrs) : trimspace(cidr)])) >= 1
    error_message = "public_subnet_cidrs must contain at least one CIDR."
  }
}

variable "private_subnet_cidrs" {
  description = "Comma-separated private subnet CIDR list. RDS and ElastiCache subnet groups use these subnets."
  type        = string
  default     = "10.20.101.0/24,10.20.102.0/24"

  validation {
    condition     = length(compact([for cidr in split(",", var.private_subnet_cidrs) : trimspace(cidr)])) >= 2
    error_message = "private_subnet_cidrs must contain at least two CIDRs for managed data resources."
  }
}

variable "s1_public_domain" {
  description = "External S1 dev/Jenkins/ops server domain."
  type        = string
  default     = "s1.internal.example.com"
}

variable "s1_public_ip" {
  description = "External SSAFY S1 dev/Jenkins/ops server public IP. Required for S1 Route53 A records."
  type        = string
  default     = ""
}

variable "admin_cidrs" {
  description = "Comma-separated administrator CIDR list. Example: 1.2.3.4/32,5.6.7.8/32"
  type        = string

  validation {
    condition     = length(trimspace(var.admin_cidrs)) > 0
    error_message = "admin_cidrs must not be empty."
  }
}

variable "s2_instance_type" {
  description = "S2 EC2 instance type."
  type        = string
  default     = "t3.medium"
}

variable "s2_ebs_size_gb" {
  description = "S2 root EBS volume size in GiB."
  type        = number
  default     = 50
}

variable "s2_key_pair_name" {
  description = "EC2 key pair name for S2."
  type        = string

  validation {
    condition     = length(trimspace(var.s2_key_pair_name)) > 0
    error_message = "s2_key_pair_name must not be empty."
  }
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  description = "RDS allocated storage in GiB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Initial database name."
  type        = string

  validation {
    condition     = length(trimspace(var.db_name)) > 0
    error_message = "db_name must not be empty."
  }
}

variable "db_username" {
  description = "RDS master username."
  type        = string
  sensitive   = true

  validation {
    condition     = length(trimspace(var.db_username)) > 0
    error_message = "db_username must not be empty."
  }
}

variable "db_password" {
  description = "RDS master password."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 8
    error_message = "db_password must be at least 8 characters."
  }
}

variable "db_multi_az" {
  description = "Whether to enable RDS Multi-AZ."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_auth_token" {
  description = "Redis auth token. Use 16-128 printable characters without whitespace, slash, double quote, or at sign."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.redis_auth_token) >= 16 && length(var.redis_auth_token) <= 128 && !can(regex("[/@\"]", var.redis_auth_token)) && !can(regex("\\s", var.redis_auth_token))
    error_message = "redis_auth_token must be 16-128 characters and must not contain whitespace, slash, double quote, or at sign."
  }
}

variable "s3_bucket_name" {
  description = "Production S3 bucket name. Must be globally unique."
  type        = string

  validation {
    condition     = length(trimspace(var.s3_bucket_name)) > 0
    error_message = "s3_bucket_name must not be empty."
  }
}

variable "s3_public_access_block" {
  description = "Whether to block public access for S3 bucket."
  type        = bool
  default     = true
}
