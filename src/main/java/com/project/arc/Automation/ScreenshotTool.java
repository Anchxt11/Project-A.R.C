package com.project.arc.Automation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * ScreenshotTool: Gives ARC the ability to capture the screen,
 * send the image to Gemini Vision for analysis, and optionally
 * web-search a fix for any problems found.
 */
public class ScreenshotTool {

    private final ChatModel visionModel;
    private final AutomationService automationService;

    private static final Path SCREENSHOT_DIR = Paths.get(
            System.getProperty("user.home"), "Documents", "ARC_Screenshots");

    // Max characters of the vision analysis to include in the search query prompt.
    // Keeps the Tavily query well under its 400-character limit.
    private static final int MAX_ANALYSIS_FOR_SEARCH = 300;

    public ScreenshotTool(ChatModel visionModel, AutomationService automationService) {
        this.visionModel = visionModel;
        this.automationService = automationService;
        ensureScreenshotDirExists();
    }

    /**
     * Full pipeline: screenshot → vision analysis → web search for fix.
     */
    @Tool("Takes a screenshot of the user's screen, analyzes it with AI vision, " +
            "then searches the web for a solution to any problem found. " +
            "Use this when the user says 'screenshot', 'look at my screen', " +
            "'what's the error', 'fix this', or points to something on screen.")
    public String screenshotAndAnalyze(
            @P("What the user wants to know or fix — e.g. 'fix the syntax error', 'explain what's on screen'")
            String userIntent) {

        // Step 1: Capture
        String screenshotPath;
        String base64Image;
        try {
            CaptureResult capture = captureScreen();
            screenshotPath = capture.filePath;
            base64Image    = capture.base64;
            System.out.println("\n[VISION PROTOCOL]: Screenshot captured → " + screenshotPath);
        } catch (Exception e) {
            return "DIAGNOSTIC: Screenshot capture failed. Error: " + e.getMessage();
        }

        // Step 2: Send to Gemini Vision — extract the text content only
        String visionAnalysis;
        try {
            visionAnalysis = analyzeWithVision(base64Image, userIntent);
            System.out.println("[VISION PROTOCOL]: Image analyzed by vision model.");
        } catch (Exception e) {
            return "DIAGNOSTIC: Vision analysis failed. Error: " + e.getMessage()
                    + "\nScreenshot saved at: " + screenshotPath;
        }

        // Step 3: Build a short, focused search query and run the web search
        String searchQuery = buildSearchQuery(userIntent, visionAnalysis);
        String webResults  = automationService.performWebSearch(searchQuery);

        // Step 4: Return a clean, concise report
        return String.format(
                "=== VISION ANALYSIS ===\n%s\n\n" +
                        "=== WEB SEARCH: \"%s\" ===\n%s\n\n" +
                        "Screenshot saved: %s",
                visionAnalysis, searchQuery, webResults, screenshotPath
        );
    }

    /**
     * Takes a screenshot and saves it — without analysis.
     */
    @Tool("Takes a screenshot of the user's screen and saves it to Documents/ARC_Screenshots. " +
            "Use this when the user just says 'take a screenshot' or 'capture my screen' " +
            "without asking for analysis.")
    public String takeScreenshot() {
        try {
            CaptureResult capture = captureScreen();
            return "SUCCESS: Screenshot saved to " + capture.filePath;
        } catch (Exception e) {
            return "DIAGNOSTIC: Screenshot failed. Error: " + e.getMessage();
        }
    }

    // ─── Private Helpers ────────────────────────────────────────────────────────

    private CaptureResult captureScreen() throws AWTException, IOException {
        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle screenRect = new Rectangle(screenSize);
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName  = "arc_screenshot_" + timestamp + ".png";
        Path   filePath  = SCREENSHOT_DIR.resolve(fileName);

        ImageIO.write(screenshot, "PNG", filePath.toFile());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(screenshot, "PNG", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        return new CaptureResult(filePath.toString(), base64);
    }

    private String analyzeWithVision(String base64Image, String userIntent) {
        UserMessage message = UserMessage.from(
                ImageContent.from(base64Image, "image/png"),
                TextContent.from(
                        "You are a precise technical analyst. The user's intent is: \"" + userIntent + "\"\n\n" +
                                "Analyze this screenshot carefully. Focus on:\n" +
                                "1. Any errors, warnings, or exceptions visible\n" +
                                "2. The exact error message text (copy it verbatim if present)\n" +
                                "3. What tool/language/framework is being used\n" +
                                "4. What line numbers or file names are visible\n" +
                                "5. What likely caused the problem\n\n" +
                                "Be specific and technical. If there is no error, describe what you see instead."
                )
        );

        ChatResponse response = visionModel.chat(message);

        // FIX: Extract only the text content — response.toString() dumps the entire
        // ChatResponse object (metadata, token counts, model name, etc.) into the
        // analysis string, which then gets forwarded to Tavily and blows its 400-char limit.
        String text = response.aiMessage().text();
        return (text != null) ? text.trim() : "DIAGNOSTIC: Vision model returned no text.";
    }

    /**
     * Builds a focused web search query from the vision analysis.
     * Truncates the analysis to MAX_ANALYSIS_FOR_SEARCH chars before sending
     * it to the model, so the resulting query stays well under Tavily's limit.
     */
    private String buildSearchQuery(String userIntent, String visionAnalysis) {
        // Truncate to prevent the model itself from producing a monster query
        String truncated = visionAnalysis.length() > MAX_ANALYSIS_FOR_SEARCH
                ? visionAnalysis.substring(0, MAX_ANALYSIS_FOR_SEARCH) + "..."
                : visionAnalysis;

        String prompt = String.format(
                "Based on this vision analysis of a screenshot:\n\"%s\"\n\n" +
                        "And the user's intent: \"%s\"\n\n" +
                        "Generate ONE concise web search query (max 8 words) to find a solution. " +
                        "Include the specific error message, language, and framework if visible. " +
                        "Reply with ONLY the search query — no explanation, no quotes, no punctuation.",
                truncated, userIntent
        );

        String query = visionModel.chat(prompt).trim();

        // Hard-cap the query at 80 characters as a final safety net
        if (query.length() > 80) {
            query = query.substring(0, 80).trim();
        }
        return query;
    }

    private void ensureScreenshotDirExists() {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
        } catch (IOException e) {
            System.err.println("ARC Warning: Could not create screenshot directory: " + e.getMessage());
        }
    }

    private record CaptureResult(String filePath, String base64) {}
}