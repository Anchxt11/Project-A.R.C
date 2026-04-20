package com.project.arc.Automation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class WebTool {
    private final AutomationService automationService;

    public WebTool(AutomationService automationService) {
        this.automationService = automationService;
    }

    @Tool("Searches the live internet for real-time technical data, documentation, or news.")
    public String searchWeb(@P("The search query for the live web") String query) {
        return automationService.performWebSearch(query);
    }
}