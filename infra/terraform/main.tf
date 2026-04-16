# LAPS ECR repository — issue #34
#
# Terraform manages ONLY the ECR private repository.
# The EC2 instance, security group, and all other infrastructure
# are managed manually in AWS Academy (IAM constraints prevent full IaC).

resource "aws_ecr_lifecycle_policy" "laps" {
  repository = aws_ecr_repository.laps.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the latest 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_ecr_repository" "laps" {
  name                 = var.name_prefix
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project     = "LAPS"
    ManagedBy   = "Terraform"
    Environment = "aws-academy"
    Issue       = "34"
  }
}
