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

    @Tool("Opens a website or URL in the user's default browser. " +
            "Use when the user says 'open [site]', 'go to [url]', 'launch [website]', " +
            "'open youtube', 'take me to github.com', or shares any link to visit. " +
            "Accepts bare domains like 'youtube.com' or full URLs like 'https://github.com/user/repo'.")
    public String openInBrowser(
            @P("The website URL or domain to open — e.g. 'youtube.com', 'https://github.com/torvalds/linux'")
            String url) {
        return automationService.openInBrowser(url);
    }
}
