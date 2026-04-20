package com.project.arc.Automation;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.awt.Desktop;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import io.github.cdimascio.dotenv.Dotenv;

public class AutomationService {
    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private String getEnv(String key) {
        String val = dotenv.get(key);
        return (val != null) ? val : System.getenv(key);
    }
    private final WebSearchEngine searchEngine = TavilyWebSearchEngine.builder()
            .apiKey(getEnv("TAVILY_KEY")) // Ensure this is in your environment variables
            .build();


    public String findAndOpenFile(String fileName, String rootPath) {
        try (Stream<Path> stream = Files.walk(Paths.get(rootPath))) {
            Path foundFile = stream
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst()
                    .orElse(null);

            if (foundFile != null) {
                Desktop.getDesktop().open(foundFile.toFile());
                return "SUCCESS: Protocol complete. File opened at " + foundFile.toAbsolutePath();
            }
            return "ERROR: Targeted file '" + fileName + "' not found in " + rootPath;
        } catch (IOException e) {
            return "DIAGNOSTIC: Recursive search breach. Error: " + e.getMessage();
        }
    }

    public String launchApp(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String target = command.toLowerCase().trim();
            ProcessBuilder pb;

            if (os.contains("win")) {
                // Mapping common display names to Windows URI Schemes
                target = switch (target) {
                    case "whatsapp" -> "whatsapp:";
                    case "xbox" -> "xbox:";
                    case "spotify" -> "spotify:";
                    case "camera" -> "microsoft.windows.camera:";
                    case "clock", "alarms" -> "ms-clock:";
                    case "calculator", "calc" -> "calculator:";
                    case "calendar" -> "outlookcal:";
                    case "mail", "outlook" -> "outlookmail:";
                    case "photos" -> "ms-photos:";
                    default -> target; // Fallback to raw command
                };

                // The empty double quotes "" are a critical 'start' parameter for titles
                pb = new ProcessBuilder("cmd", "/c", "start", "", target);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-a", command);
            } else {
                pb = new ProcessBuilder("xdg-open", command);
            }

            pb.start();
            return "SUCCESS: Protocol initiated. Target '" + target + "' is deploying.";
        } catch (IOException e) {
            return "DIAGNOSTIC FAILURE: Could not launch " + command + ". Verify app installation.";
        }
    }


    public String performWebSearch(String query) {
        try {
            var results = searchEngine.search(query);
            return results.results().stream()
                    .map(res -> String.format("[%s]: %s (Source: %s)", res.title(), res.snippet(), res.url()))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            return "DIAGNOSTIC: Web uplink failed. Protocol error: " + e.getMessage();
        }
    }
}