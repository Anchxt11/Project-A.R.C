package com.project.arc.Automation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class MediaTools {

    private final AutomationService automationService;

    public MediaTools(AutomationService automationService) {
        this.automationService = automationService;
    }

    // ─── Spotify ────────────────────────────────────────────────────────────────

    @Tool("Opens Spotify and plays a song, artist, album, or playlist. " +
            "Use when the user says 'play [song/artist]', 'put on some music', " +
            "'play X on Spotify', or similar. " +
            "Pass the song name, artist, or search query as 'query'.")
    public String playOnSpotify(
            @P("Song name, artist, album, or search query — e.g. 'Bohemian Rhapsody Queen' or 'lo-fi chill beats'")
            String query) {
        return automationService.playOnSpotify(query);
    }

    // ─── WhatsApp ───────────────────────────────────────────────────────────────

    @Tool("Opens WhatsApp Desktop and pre-fills a message to a contact. " +
            "Use when the user says 'send a WhatsApp to [person/number]', " +
            "'message [name] on WhatsApp', or 'WhatsApp [someone] saying [...]'. " +
            "phoneNumber must be in international format without '+', e.g. '919876543210'. " +
            "If the user only says 'open WhatsApp' without a contact, leave phoneNumber blank.")
    public String sendWhatsAppMessage(
            @P("Recipient phone number in international format without '+', e.g. '919876543210'. " +
                    "Leave blank to just open WhatsApp.")
            String phoneNumber,
            @P("The message text to pre-fill. Leave blank if the user did not specify a message.")
            String message) {
        return automationService.sendWhatsAppMessage(phoneNumber, message);
    }

    // ─── PDF Reader ─────────────────────────────────────────────────────────────

    @Tool("Reads and extracts the text content from a PDF file so ARC can analyse, " +
            "summarize, or answer questions about it. " +
            "Use when the user says 'read this PDF', 'summarize this document', " +
            "'what does the PDF say', or shares a file path ending in .pdf. " +
            "For very large PDFs, set maxPages to limit how many pages are extracted " +
            "(0 = all pages). The extracted text is returned directly to ARC.")
    public String readPdf(
            @P("Absolute file path to the PDF — e.g. 'C:\\Users\\anchi\\Documents\\report.pdf'")
            String filePath,
            @P("Maximum number of pages to extract. Use 0 to extract all pages. " +
                    "For large documents start with 10 to avoid context overflow.")
            int maxPages) {
        return automationService.readPdf(filePath, maxPages);
    }


}