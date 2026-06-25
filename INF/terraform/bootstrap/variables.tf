variable "project_name" {
  description = "Project name used for Terraform backend resource names."
  type        = string
  default     = "eumgil"
}

variable "aws_region" {
  description = "AWS region for Terraform backend resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "S3 bucket name for Terraform remote state."
  type        = string
}

variable "lock_table_name" {
  description = "DynamoDB table name for Terraform state locking."
  type        = string
  default     = "eumgil-terraform-lock"
}
