# ADR 0007: Java and Spring versions

- **Status:** Proposed
- **Decision:** Target Java 21 and Spring Boot 3.5.16. Centralize third-party dependency versions in the parent POM/BOMs. Baseline AWS SDK for Java 2.x at 2.59.1 and Testcontainers at 2.0.5.
- **Context:** The README identifies Java 21 and Spring Boot 3.5.x. Current official documentation reports Spring Boot 3.5.16 supports Java 17 through 25. AWS SDK v2 supports Java 8 or later; current official release listing shows 2.59.1. Testcontainers documentation lists 2.0.5.
- **Options:** Keep documented Java 21/Boot 3.5 line; move to Java 25 or another Spring Boot major; defer exact framework patch selection.
- **Trade-offs:** Java 21 matches the existing baseline and remains supported by Boot 3.5. Oracle's Java 21 update licensing changed in October 2026, so choose a JDK distribution intentionally. Boot 3.5 reduces migration risk versus a major framework change. Pinning SDK/test library versions makes builds repeatable but requires reviewed upgrades.
- **References:** [Oracle Java roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html), [Spring Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [AWS SDK setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-java-buildtool.html), [AWS SDK releases](https://github.com/aws/aws-sdk-java-v2/releases), [Testcontainers](https://java.testcontainers.org/).
