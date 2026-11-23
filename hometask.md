Practice Scenario: MCP-Enabled DevOps Agent
AI-Enabled DevOps Engineer Program • Practical Homework
Connect an AI coding agent to external tools using Model Context Protocol (MCP) and configure it to use those tools as part
of a DevOps workflow.
Rules
• Connect at least 2 MCP servers.
• Use MCP tools to retrieve real information instead of guessing.
• Let the agent select tools from natural-language requests.
• Do not hard-code tool calls into every prompt.
• Keep agent instructions and skills short and reusable.
1. Connect MCP Servers
   Configure at least two MCP servers relevant to engineering work, such as GitHub, Azure DevOps, Jira, Microsoft Learn, or
   another server of your choice. Verify that the agent discovers tools from both servers.
   Done when: a natural-language request causes the agent to use a tool from each server successfully.
2. Add MCP Rules to AGENTS.md
   Add rules describing how the agent should use external tools. Add at least 3 rules of your own.
## MCP Tool Usage - Prefer MCP tools over assumptions. - Search before creating something new. - Never invent IDs, tool results, or system state. - Verify technical facts with authoritative documentation. - Report failed tool calls instead of assuming success.
3. Create an MCP Skill
   Create one reusable skill, for example: skills/devops-investigate/SKILL.md. It should instruct the agent to:
1. Search for an existing issue or work item.
2. Retrieve related technical information through another MCP server.
3. Correlate the results.
4. Recommend the next action.
   Done when: the skill uses tools from at least two MCP servers to complete one task.
4. Test the Agent
   Give the agent a goal rather than tool-by-tool commands:
   Investigate this deployment problem. Check whether it is already tracked,
   find relevant technical information, and recommend the next action.
   Capture which tools were selected, what they returned, and how the agent used the results.
   Success Criteria
   User request -> AGENTS.md -> Skill -> MCP Tool #1 -> MCP Tool #2 -> Result
   MCP-Enabled DevOps Agent • Practice Scenario
   Bonus: Build a Local MCP Server
   Create a simple local MCP server and connect your agent or model to it.
   • Expose at least 2 tools.
   • Each tool accepts an input and returns structured data.
   • Invoke the tools successfully using natural language.
   • Use the local server together with one external MCP server.
   Example tools: get_environment_status, get_deployment_info
   Bonus flow: Agent -> Local MCP tool -> External MCP tool -> Combined result
   MCP-Enabled DevOps Agent • Practice Scenario