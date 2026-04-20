package com.project.arc.Automation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
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
            .apiKey(getEnv("TAVILY_KEY"))
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
                    default -> target;
                };
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

    /**
     * Opens Spotify and plays a song/artist using the spotify: URI scheme.
     * Works on Windows, macOS, and Linux (where Spotify is installed).
     * Format: spotify:search:<query> — lets Spotify resolve the best match.
     */
    public String playOnSpotify(String query) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            // Encode spaces as %20 for the URI — Spotify handles the rest
            String encoded = query.trim().replace(" ", "%20");
            String uri = "spotify:search:" + encoded;

            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", uri);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", uri);
            } else {
                pb = new ProcessBuilder("xdg-open", uri);
            }

            pb.start();
            return "SUCCESS: Spotify search launched for \"" + query + "\". " +
                    "If Spotify is installed it will open and play the top result.";
        } catch (IOException e) {
            return "DIAGNOSTIC: Could not launch Spotify. Ensure it is installed. Error: " + e.getMessage();
        }
    }

    /**
     * Opens WhatsApp Desktop with a pre-filled message to a phone number.
     * phoneNumber must be in international format without '+' or spaces, e.g. "919876543210".
     * If phoneNumber is blank, opens WhatsApp home screen instead.
     */
    public String sendWhatsAppMessage(String phoneNumber, String message) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String uri;

            if (phoneNumber == null || phoneNumber.isBlank()) {
                // No number — just open WhatsApp
                uri = "whatsapp:";
            } else {
                // Strip any stray characters (spaces, +, dashes) the user might have typed
                String cleaned = phoneNumber.replaceAll("[^0-9]", "");
                String encoded = message != null
                        ? message.trim().replace(" ", "%20").replace("\n", "%0A")
                        : "";
                uri = "https://wa.me/" + cleaned + (encoded.isEmpty() ? "" : "?text=" + encoded);
            }

            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", uri);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", uri);
            } else {
                pb = new ProcessBuilder("xdg-open", uri);
            }

            pb.start();

            if (phoneNumber == null || phoneNumber.isBlank()) {
                return "SUCCESS: WhatsApp opened. No number provided — home screen launched.";
            }
            return "SUCCESS: WhatsApp opened with pre-filled message to +" + phoneNumber + ". " +
                    "Hit Send in the app to deliver it.";
        } catch (IOException e) {
            return "DIAGNOSTIC: Could not open WhatsApp. Ensure it is installed. Error: " + e.getMessage();
        }
    }

    /**
     * Extracts text from a PDF file using Apache PDFBox.
     * Returns up to maxPages pages of content so the model context doesn't overflow.
     * If maxPages <= 0, all pages are extracted.

     */

    public String readPdf(String filePath, int maxPages) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "ERROR: PDF not found at path: " + filePath;
        }
        if (!filePath.toLowerCase().endsWith(".pdf")) {
            return "ERROR: File does not appear to be a PDF: " + filePath;
        }

        try (PDDocument doc = Loader.loadPDF(file)) {
            int totalPages = doc.getNumberOfPages();
            int pagesToRead = (maxPages <= 0 || maxPages > totalPages) ? totalPages : maxPages;

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pagesToRead);
            String text = stripper.getText(doc).trim();

            if (text.isBlank()) {
                return "DIAGNOSTIC: PDF loaded (" + totalPages + " pages) but no extractable text found. " +
                        "It may be a scanned/image-based PDF. Try the screenshotAndAnalyze tool instead.";
            }

            String header = String.format(
                    "[PDF: %s | %d of %d page(s) extracted]\n\n",
                    file.getName(), pagesToRead, totalPages
            );
            return header + text;

        } catch (IOException e) {
            return "DIAGNOSTIC: Failed to read PDF. Error: " + e.getMessage();
        }
    }



    public String performWebSearch(String query) {
        try {
            var results = searchEngine.search(query);
            return results.results().stream()
                    .limit(3)
                    .map(res -> "- " + res.title() + ": " + res.snippet())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "DIAGNOSTIC: Web uplink failed. Protocol error: " + e.getMessage();
        }
    }

    public String openInBrowser(String url) {
        try {
            // Prepend https:// if the user passed a bare domain like "youtube.com"
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }

            pb.start();
            return "SUCCESS: Opened '" + url + "' in your default browser.";
        } catch (IOException e) {
            return "DIAGNOSTIC: Failed to open browser. Error: " + e.getMessage();
        }
    }
}