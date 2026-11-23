# devops-agent

Kiro agent with Bitbucket and Jenkins MCP servers. Kiro starts the servers itself from `.kiro/settings/mcp.json`.

Bonus: both MCP servers are local servers written for this project (Spring Boot, in `bitbucket_mcp/` and `jenkins_mcp/`).
Each wraps the REST API of an external system (Bitbucket, Jenkins), and the agent combines the two in one investigation.

## Run

1. Build the jars (skip if `target/` already exists):
   ```
   mvn -f bitbucket_mcp/pom.xml package
   mvn -f jenkins_mcp/pom.xml package
   ```
2. Create the Kiro config from the template (`mcp.json` is gitignored; the template only has `${...}` placeholders and jar paths relative to the project root):
   ```
   cp .kiro/settings/mcp.json.example .kiro/settings/mcp.json
   ```
3. Load the credentials and start Kiro from the project root, so it picks up `AGENTS.md` and `.kiro/`:
   ```
   set -a; source private.env; set +a
   kiro-cli chat
   ```
   `set -a` exports the variables, which fill the `${...}` placeholders in `mcp.json`.

4. In the chat, run `/mcp` and check that `bitbucket` and `jenkins` are running. 