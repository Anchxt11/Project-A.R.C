package com.project.arc.Automation;

import dev.langchain4j.agent.tool.Tool;

public class ArcTools {
    private final AutomationService automationService;

    public ArcTools(AutomationService automationService) {
        this.automationService = automationService;
    }

    @Tool("Launches an application or executes a terminal command (e.g., 'notepad.exe' or 'calc')")
    public String executeSystemProcess(String command) {
        return automationService.launchApp(command);
    }

    @Tool("Recursively searches for a file by name starting from a specific directory and opens it")
    public String deepSearchAndOpen(String fileName, String startFolder) {
        String root;
        String home = System.getProperty("user.home");

        root = switch (startFolder.toLowerCase()) {
            case "downloads" -> home + "/Downloads";
            case "documents" -> home + "/Documents";
            case "pictures" -> home + "/Pictures";
            default -> System.getProperty("user.dir");
        };

        return automationService.findAndOpenFile(fileName, root);
    }
}