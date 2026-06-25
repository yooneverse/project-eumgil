#!/usr/bin/env python3
"""prod Terraform guardrail tests."""

from pathlib import Path
import re
import unittest


ROOT_DIR = Path(__file__).resolve().parents[2]
PROD_MAIN_TF = ROOT_DIR / "INF" / "terraform" / "envs" / "prod" / "main.tf"
PROD_OUTPUTS_TF = ROOT_DIR / "INF" / "terraform" / "envs" / "prod" / "outputs.tf"


class ProdTerraformTest(unittest.TestCase):
    def test_admin_domain_routes_to_s2_and_can_adopt_existing_a_record(self):
        content = PROD_MAIN_TF.read_text(encoding="utf-8")
        match = re.search(
            r'resource "aws_route53_record" "admin_s2" \{(?P<body>.*?)\n\}',
            content,
            re.DOTALL,
        )

        self.assertIsNotNone(match)
        body = match.group("body")
        self.assertIn("name    = local.admin_domain", body)
        self.assertIn('type    = "A"', body)
        self.assertIn("records = [aws_eip.s2.public_ip]", body)
        self.assertIn("allow_overwrite = true", body)

    def test_route53_outputs_include_admin_domain(self):
        content = PROD_OUTPUTS_TF.read_text(encoding="utf-8")

        self.assertIn("admin     = local.create_dns ? local.admin_domain : null", content)


if __name__ == "__main__":
    unittest.main()
