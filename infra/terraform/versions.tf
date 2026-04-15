terraform {
  required_version = ">= 1.6.0"

  backend "s3" {
    key = "laps/terraform.tfstate"
    # bucket and region are injected at init time via -backend-config flags:
    #   terraform init \
    #     -backend-config="bucket=laps-tfstate-<account-id>" \
    #     -backend-config="region=<aws-region>"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0, < 5.100.0"
    }
  }
}
