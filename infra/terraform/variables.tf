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
