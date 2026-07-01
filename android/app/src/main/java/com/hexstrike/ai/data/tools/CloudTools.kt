package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.tools.ToolCategory.CLOUD_CONTAINER

val cloudTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "trivy",
        description = "Scan container images, filesystems, or IaC for vulnerabilities and misconfigurations.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Script(listOf("curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin")),
        defaultTimeoutMs = 10 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "Image name, directory, or repo to scan", required = true),
            ToolParam("scan_type", description = "Trivy subcommand", default = "image", enumValues = listOf("image", "fs", "repo", "config")),
            ToolParam("additional_args", description = "Extra trivy flags", isRawFlags = true),
        ),
    ) { a -> "trivy ${a["scan_type"] ?: "image"} ${a["additional_args"] ?: ""} ${shEscape(a.getValue("target"))}".trim() },
    SecurityTool(
        id = "checkov",
        description = "Static analysis for Terraform/CloudFormation/Kubernetes IaC misconfigurations.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Pip(listOf("checkov")),
        params = listOf(
            ToolParam("directory", description = "Path to the IaC directory to scan", required = true),
            ToolParam("additional_args", description = "Extra checkov flags", isRawFlags = true),
        ),
    ) { a -> "checkov -d ${shEscape(a.getValue("directory"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "terrascan",
        description = "Detect security violations and compliance issues in Terraform/Kubernetes/Helm IaC.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Script(listOf("curl -L \"$(curl -s https://api.github.com/repos/tenable/terrascan/releases/latest | grep -o 'https://.*Linux_x86_64.tar.gz')\" -o /tmp/terrascan.tar.gz && tar -xzf /tmp/terrascan.tar.gz -C /usr/local/bin terrascan")),
        params = listOf(ToolParam("directory", description = "Path to the IaC directory to scan", required = true)),
    ) { a -> "terrascan scan -d ${shEscape(a.getValue("directory"))}" },
    SecurityTool(
        id = "prowler",
        description = "AWS/Azure/GCP cloud security posture assessment against CIS and other benchmarks. Requires cloud credentials to already be configured in the environment.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Pip(listOf("prowler")),
        defaultTimeoutMs = 30 * 60 * 1000L,
        params = listOf(
            ToolParam("provider", description = "Cloud provider", default = "aws", enumValues = listOf("aws", "azure", "gcp")),
            ToolParam("additional_args", description = "Extra prowler flags", isRawFlags = true),
        ),
    ) { a -> "prowler ${a["provider"] ?: "aws"} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "kube_bench",
        description = "Check a Kubernetes cluster against CIS Kubernetes Benchmark controls.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Script(listOf("curl -L https://github.com/aquasecurity/kube-bench/releases/latest/download/kube-bench_linux_amd64.tar.gz -o /tmp/kb.tar.gz && tar -xzf /tmp/kb.tar.gz -C /usr/local/bin kube-bench")),
        params = listOf(ToolParam("additional_args", description = "Extra kube-bench flags", isRawFlags = true)),
    ) { a -> "kube-bench ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "kube_hunter",
        description = "Actively hunt for security weaknesses in a Kubernetes cluster.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Pip(listOf("kube-hunter")),
        params = listOf(
            ToolParam("target", description = "Cluster/node IP or CIDR range"),
            ToolParam("remote", type = "boolean", description = "Use --remote scanning mode", default = "true"),
        ),
    ) { a ->
        buildString {
            append("kube-hunter")
            if (a["remote"] != "false" && a["target"] != null) append(" --remote ${shEscape(a.getValue("target"))}")
        }
    },
    SecurityTool(
        id = "docker_bench_security",
        description = "Audit a Docker host's configuration against CIS Docker Benchmark best practices.",
        category = CLOUD_CONTAINER,
        install = InstallMethod.Script(listOf("git clone https://github.com/docker/docker-bench-security /opt/docker-bench-security")),
        requiresConfirmation = false,
        params = emptyList(),
    ) { _ -> "cd /opt/docker-bench-security && sh docker-bench-security.sh" },
)
