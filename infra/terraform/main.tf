# LAPS ECR repository — issue #34
#
# Terraform manages ONLY the ECR private repository.
# The EC2 instance, security group, and all other infrastructure
# are managed manually in AWS Academy (IAM constraints prevent full IaC).

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
