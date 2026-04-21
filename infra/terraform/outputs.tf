output "ecr_repository_url" {
  description = "ECR repository URL. Use as the base for image tags: <url>:<git-sha>"
  value       = aws_ecr_repository.laps.repository_url
}

output "ecr_repository_arn" {
  description = "ECR repository ARN. Use when scoping IAM policies to this repository."
  value       = aws_ecr_repository.laps.arn
}
