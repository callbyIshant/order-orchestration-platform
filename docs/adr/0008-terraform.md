# ADR 0008: Terraform

- **Status:** Proposed
- **Decision:** Use Terraform 1.17.1 as the initial CLI baseline; separate reusable modules from environment configuration, lock provider selections, and require human review of plans before apply or destroy.
- **Context:** AWS resources are introduced in a later milestone and can incur ongoing cost. Reproducibility and safe cleanup matter for a demo environment.
- **Options:** Terraform with modules and reviewed plans; hand-managed AWS resources; another infrastructure-as-code tool.
- **Trade-offs:** Terraform supports repeatable plans and reusable modules but requires state storage, locking, provider lifecycle management, and cost-aware operations. Manual setup is initially simple but difficult to reproduce. State backend and locking details remain for the infrastructure milestone.
- **Reference:** [HashiCorp Terraform install](https://developer.hashicorp.com/terraform/install).
