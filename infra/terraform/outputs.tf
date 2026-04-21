output "ecr_repository_url" {
  description = "ECR repository URL. Use as the base for image tags: <url>:<git-sha>"
  value       = aws_ecr_repository.laps.repository_url
}

output "ecr_repository_arn" {
  description = "ECR repository ARN. Use when scoping IAM policies to this repository."
  value       = aws_ecr_repository.laps.arn
}

output "rds_endpoint" {
  description = "RDS instance endpoint (hostname:port). Set as RDS_ENDPOINT in GitHub Secrets when rds_enabled = true."
  value       = var.rds_enabled ? aws_db_instance.laps[0].endpoint : null
}

output "rds_db_name" {
  description = "RDS database name."
  value       = var.rds_enabled ? aws_db_instance.laps[0].db_name : null
}
