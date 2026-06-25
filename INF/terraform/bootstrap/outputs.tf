output "state_bucket_name" {
  description = "S3 bucket name for Terraform remote state."
  value       = aws_s3_bucket.state.bucket
}

output "lock_table_name" {
  description = "DynamoDB lock table name for Terraform remote state."
  value       = aws_dynamodb_table.lock.name
}
