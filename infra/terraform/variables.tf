variable "aws_region" {
  description = "AWS Academy region to deploy into, for example us-east-1."
  type        = string

  validation {
    condition     = length(trimspace(var.aws_region)) > 0
    error_message = "aws_region must not be empty."
  }
}

variable "name_prefix" {
  description = "Prefix used for AWS resource names and tags. Also used as the ECR repository name."
  type        = string
  default     = "laps"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9-]{1,31}$", var.name_prefix))
    error_message = "name_prefix must start with a letter and contain only letters, numbers, and hyphens, length 2-32."
  }
}

# ── RDS ──────────────────────────────────────────────────────────────────────

variable "rds_enabled" {
  description = "Set to true to create an RDS PostgreSQL instance. Requires vpc_id, db_subnet_ids, ec2_security_group_id, db_username, and db_password."
  type        = bool
  default     = false
}

variable "vpc_id" {
  description = "VPC ID for the RDS security group. Required when rds_enabled = true."
  type        = string
  default     = ""
}

variable "db_subnet_ids" {
  description = "Subnet IDs for the RDS DB subnet group. Must span at least two AZs. Required when rds_enabled = true."
  type        = list(string)
  default     = []
}

variable "ec2_security_group_id" {
  description = "Security group ID of the EC2 instance. RDS will allow inbound PostgreSQL from this SG only. Required when rds_enabled = true."
  type        = string
  default     = ""
}

variable "db_username" {
  description = "Master username for the RDS instance. Required when rds_enabled = true."
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_password" {
  description = "Master password for the RDS instance (minimum 8 characters). Required when rds_enabled = true."
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GiB for the RDS instance."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Enable Multi-AZ deployment. Leave false for AWS Academy to avoid additional cost."
  type        = bool
  default     = false
}
