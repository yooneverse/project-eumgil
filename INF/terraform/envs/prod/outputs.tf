output "vpc_id" {
  description = "Dedicated production VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = aws_subnet.private[*].id
}

output "s3_gateway_endpoint_id" {
  description = "S3 Gateway VPC Endpoint ID."
  value       = aws_vpc_endpoint.s3.id
}

output "s2_public_ip" {
  description = "S2 Elastic IP."
  value       = aws_eip.s2.public_ip
}

output "s2_instance_id" {
  description = "S2 EC2 instance ID."
  value       = aws_instance.s2.id
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint."
  value       = aws_db_instance.postgres.address
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint."
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "s3_bucket_name" {
  description = "Production S3 bucket name."
  value       = aws_s3_bucket.prod.bucket
}

output "route53_records" {
  description = "Route53 records created by this environment."
  value = {
    api       = local.create_dns ? local.api_domain : null
    ai        = local.create_dns ? local.ai_domain : null
    admin     = local.create_dns ? local.admin_domain : null
    jenkins   = local.create_s1_dns ? "jenkins.${var.root_domain}" : null
    api_dev   = local.create_s1_dns ? "api.dev.${var.root_domain}" : null
    ai_dev    = local.create_s1_dns ? "ai.dev.${var.root_domain}" : null
    grafana   = local.create_s1_dns ? "grafana.${var.root_domain}" : null
    portainer = local.create_s1_dns ? "portainer.${var.root_domain}" : null
    sonarqube = local.create_s1_dns ? "sonarqube.${var.root_domain}" : null
    plg       = local.create_s1_dns ? "plg.${var.root_domain}" : null
  }
}
