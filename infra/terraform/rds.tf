# LAPS RDS PostgreSQL instance — issue #118
#
# Controlled by rds_enabled (default: false).
# Set rds_enabled = true and supply vpc_id, db_subnet_ids, ec2_security_group_id,
# db_username, and db_password to provision the database.
#
# AWS Academy note: if terraform apply fails with an IAM/permissions error on rds:*
# actions, create the instance manually in the console instead and set RDS_ENDPOINT
# in GitHub Secrets — the application connection is identical either way.

resource "aws_security_group" "rds" {
  count = var.rds_enabled ? 1 : 0

  name        = "${var.name_prefix}-rds-sg"
  description = "Allow PostgreSQL from EC2 security group only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from EC2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Project     = "LAPS"
    ManagedBy   = "Terraform"
    Environment = "aws-academy"
    Issue       = "118"
  }
}

resource "aws_db_subnet_group" "laps" {
  count = var.rds_enabled ? 1 : 0

  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.db_subnet_ids

  tags = {
    Project     = "LAPS"
    ManagedBy   = "Terraform"
    Environment = "aws-academy"
    Issue       = "118"
  }
}

resource "aws_db_instance" "laps" {
  count = var.rds_enabled ? 1 : 0

  identifier = "${var.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp2"
  storage_encrypted = true

  db_name  = "lapsdb"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.laps[0].name
  vpc_security_group_ids = [aws_security_group.rds[0].id]

  multi_az            = var.db_multi_az
  publicly_accessible = false
  deletion_protection = false
  skip_final_snapshot = true

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  # Ship postgres and upgrade logs to CloudWatch for audit trail (ASVS V7.1.1)
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Project     = "LAPS"
    ManagedBy   = "Terraform"
    Environment = "aws-academy"
    Issue       = "118"
  }
}
