package com.project.arc;

import com.project.arc.Automation.ArcTools;
import com.project.arc.Automation.AutomationService;
import com.project.arc.Automation.FileSystemTools;
import com.project.arc.Automation.MediaTools;
import com.project.arc.Automation.ScreenshotTool;
import com.project.arc.Automation.WebTool;
import com.project.arc.config.ArcAssistant;
import com.project.arc.config.ArcConfig;
import com.project.arc.config.MCPToolProvider;
import com.project.arc.memory.retrievers.MemoryRetrievalService;
import com.project.arc.memory.store.MemorySync;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javafx.scene.text.TextAlignment;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class ArcUI extends Application {

    // ── Palette ────────────────────────────────────────────────────────────────
    private static final String BG_DEEP      = "#070B0F";
    private static final String BG_PANEL     = "#0B1017";
    private static final String BG_INPUT     = "#0F1820";
    private static final String ACCENT_CYAN  = "#00D4FF";
    private static final String ACCENT_DIM   = "#0A3A4A";
    private static final String TEXT_PRIMARY = "#CDD9E5";
    private static final String TEXT_DIM     = "#3A5060";
    private static final String BORDER_COLOR = "#162030";

    // ── State ──────────────────────────────────────────────────────────────────
    private VBox       chatBox;
    private ScrollPane scrollPane;
    private TextField  inputField;
    private Label      statusLabel;
    private Label      clockLabel;
    private Label      currentArcLabel;  // active streaming target

    // ARC backend
    private ArcAssistant arc;
    private MemorySync   syncEngine;
    private ArcConfig    config;
    private String       sessionID;

    // Drag
    private double dragX, dragY;

    // ── Application Entry ──────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        initArcBackend(stage);

        stage.initStyle(StageStyle.UNDECORATED);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DEEP + ";");
        root.setTop(buildTitleBar(stage));
        root.setCenter(buildChatArea());
        root.setBottom(buildInputBar());

        root.setBorder(new Border(new BorderStroke(
                Color.web(ACCENT_DIM),
                BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY,
                new BorderWidths(1)
        )));

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(ACCENT_CYAN, 0.15));
        glow.setRadius(40);
        root.setEffect(glow);

        Scene scene = new Scene(root, 1000, 700);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(buildCSS());

        stage.setScene(scene);
        stage.setTitle("A.R.C.");
        stage.show();

        runBootSequence();
        startClock();
        inputField.requestFocus();
    }

    // ── Backend Init ───────────────────────────────────────────────────────────
    private void initArcBackend(Stage stage) {
        try {
            config = new ArcConfig();
            MemoryRetrievalService mrs = new MemoryRetrievalService(config);
            syncEngine = new MemorySync(config);

            AutomationService svc    = new AutomationService();
            ArcTools       arcTools  = new ArcTools(svc);
            FileSystemTools fsTools  = new FileSystemTools();
            MCPToolProvider mcpTool  = new MCPToolProvider();
            WebTool        webTool   = new WebTool(svc);
            ScreenshotTool shotTool  = new ScreenshotTool(config.visionModel(), svc);
            MediaTools     mediaTool = new MediaTools(svc);

            arc = AiServices.builder(ArcAssistant.class)
                    .streamingChatModel(config.streamingGeminiModel())
                    .chatModel(config.geminiModel())
                    .tools(arcTools, fsTools, webTool, shotTool, mediaTool)
                    .toolProvider(mcpTool.mcpToolProvider())
                    .beforeToolExecution(event -> {
                        ToolExecutionRequest req = event.request();
                        if (req.name().contains("write") || req.name().contains("append")) {
                            Platform.runLater(() -> showAuthDialog(req, stage));
                        }
                    })
                    .afterToolExecution(event -> {
                        String toolName = event.request().name();
                        if (!toolName.equalsIgnoreCase("searchWeb")
                                && !toolName.equalsIgnoreCase("screenshotAndAnalyze")) {
                            Object result = event.result();
                            if (result != null) {
                                Platform.runLater(() ->
                                        addSystemMessage("PROTOCOL: " + result.toString()));
                            }
                        }
                    })
                    .chatMemoryProvider(memId -> MessageWindowChatMemory.builder()
                            .id(memId)
                            .chatMemoryStore(config.redisStore())
                            .maxMessages(10)
                            .build())
                    .retrievalAugmentor(mrs.getAugmenter())
                    .build();

            sessionID = UUID.randomUUID().toString();

        } catch (Exception e) {
            sessionID = UUID.randomUUID().toString();
            System.err.println("[ARC] Backend init failed: " + e.getMessage());
        }
    }

    // ── Title Bar ──────────────────────────────────────────────────────────────
    private HBox buildTitleBar(Stage stage) {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 20, 0, 24));
        bar.setPrefHeight(48);
        bar.setStyle(
                "-fx-background-color: " + BG_PANEL + ";" +
                        "-fx-border-color: transparent transparent " + BORDER_COLOR + " transparent;" +
                        "-fx-border-width: 0 0 1 0;"
        );
        bar.setOnMousePressed(e -> { dragX = e.getSceneX(); dragY = e.getSceneY(); });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });

        // Pulsing core dot
        Circle dot = new Circle(5, Color.web(ACCENT_CYAN));
        DropShadow dotGlow = new DropShadow(10, Color.web(ACCENT_CYAN, 0.9));
        dot.setEffect(dotGlow);
        FadeTransition pulse = new FadeTransition(Duration.millis(1600), dot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.2);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        Label title = new Label("A.R.C.");
        title.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 15px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";" +
                        "-fx-letter-spacing: 5px;"
        );
        Label sub = new Label("AUTONOMOUS RESPONSE CORE");
        sub.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-letter-spacing: 3px;"
        );
        VBox titleStack = new VBox(2, title, sub);
        titleStack.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(12, dot, titleStack);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Clock
        clockLabel = new Label("00:00:00");
        clockLabel.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";"
        );

        // Session badge
        String shortId = sessionID != null ? sessionID.substring(0, 8) : "--------";
        Label sessionBadge = new Label("SES·" + shortId);
        sessionBadge.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-padding: 3 7;" +
                        "-fx-border-color: " + BORDER_COLOR + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;" +
                        "-fx-background-color: " + BG_DEEP + ";"
        );

        // Flush
        Button flushBtn = titleBtn("FLUSH", "#F4BF4F");
        flushBtn.setOnAction(e -> {
            if (config != null) config.redisStore().deleteMessages(sessionID);
            addSystemMessage("L2 MEMORY FLUSHED — FRESH CONTEXT INITIALIZED");
        });

        // Minimize
        Button btnMin = titleBtn("—", "#F4BF4F");
        btnMin.setOnAction(e -> stage.setIconified(true));

        // Close
        Button btnClose = titleBtn("✕", "#FF5F57");
        btnClose.setOnAction(e -> {
            addSystemMessage("CRYSTALLIZING SESSION...");
            PauseTransition d = new PauseTransition(Duration.millis(400));
            d.setOnFinished(ev -> {
                if (syncEngine != null) syncEngine.exitFlush(sessionID);
                if (config != null) config.neo4jDriver().close();
                Platform.exit();
            });
            d.play();
        });

        HBox right = new HBox(14, sessionBadge, clockLabel, flushBtn, btnMin, btnClose);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);   // never shrink below natural size
        right.setMaxWidth(Region.USE_PREF_SIZE);   // don't grow either — spacer handles that

        bar.getChildren().addAll(left, spacer, right);
        return bar;
    }

    private Button titleBtn(String label, String hoverColor) {
        Button btn = new Button(label);
        String base =
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 11px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 3 10;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;";
        String hover =
                "-fx-background-color: " + hoverColor + "18;" +
                        "-fx-text-fill: " + hoverColor + ";" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 11px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 3 10;" +
                        "-fx-border-color: " + hoverColor + "55;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    // ── Chat Area ──────────────────────────────────────────────────────────────
    private StackPane buildChatArea() {
        chatBox = new VBox(8);
        chatBox.setPadding(new Insets(28, 32, 24, 32));
        // chatBox must NOT set a fixed width — let ScrollPane drive it
        chatBox.setStyle("-fx-background-color: " + BG_DEEP + ";");

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);   // chatBox stretches to viewport width
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle(
                "-fx-background-color: " + BG_DEEP + ";" +
                        "-fx-background: " + BG_DEEP + ";" +
                        "-fx-border-color: transparent;"
        );
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Top fade mask — bind width to scrollPane so it never overflows
        Rectangle topFade = new Rectangle(0, 40);
        topFade.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(BG_DEEP, 1.0)),
                new Stop(1, Color.web(BG_DEEP, 0.0))));
        topFade.setMouseTransparent(true);

        StackPane stack = new StackPane(scrollPane, topFade);
        // Bind fade width to the stack so it's always exactly as wide as the pane
        topFade.widthProperty().bind(stack.widthProperty());
        StackPane.setAlignment(topFade, Pos.TOP_CENTER);
        return stack;
    }

    // ── Input Bar ──────────────────────────────────────────────────────────────
    private VBox buildInputBar() {
        VBox container = new VBox(0);
        container.setStyle(
                "-fx-background-color: " + BG_PANEL + ";" +
                        "-fx-border-color: " + BORDER_COLOR + " transparent transparent transparent;" +
                        "-fx-border-width: 1 0 0 0;"
        );

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(14, 28, 14, 28));

        Label prompt = new Label("▸");
        prompt.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 16px;" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";"
        );

        inputField = new TextField();
        inputField.setPromptText("Enter command or message...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        styleInput(false);
        inputField.focusedProperty().addListener((obs, o, focused) -> styleInput(focused));

        Button sendBtn = new Button("TRANSMIT");
        String sendBase =
                "-fx-background-color: " + ACCENT_DIM + ";" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-letter-spacing: 2px;" +
                        "-fx-border-color: " + ACCENT_CYAN + "55;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;" +
                        "-fx-padding: 9 22;" +
                        "-fx-cursor: hand;";
        String sendHover =
                "-fx-background-color: " + ACCENT_CYAN + "22;" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-letter-spacing: 2px;" +
                        "-fx-border-color: " + ACCENT_CYAN + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;" +
                        "-fx-padding: 9 22;" +
                        "-fx-cursor: hand;";
        sendBtn.setStyle(sendBase);
        sendBtn.setOnMouseEntered(e -> sendBtn.setStyle(sendHover));
        sendBtn.setOnMouseExited(e -> sendBtn.setStyle(sendBase));

        Runnable send = () -> {
            String text = inputField.getText().trim();
            if (!text.isEmpty()) {
                inputField.clear();
                handleUserInput(text);
            }
        };
        sendBtn.setOnAction(e -> send.run());
        inputField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) send.run(); });

        row.getChildren().addAll(prompt, inputField, sendBtn);

        statusLabel = new Label("READY  //  ALL SYSTEMS NOMINAL");
        statusLabel.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-letter-spacing: 1px;" +
                        "-fx-padding: 0 28 10 28;"
        );

        container.getChildren().addAll(row, statusLabel);
        return container;
    }

    private void styleInput(boolean focused) {
        inputField.setStyle(
                "-fx-background-color: " + BG_INPUT + ";" +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                        "-fx-prompt-text-fill: " + TEXT_DIM + ";" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 14px;" +
                        "-fx-border-color: " + (focused ? ACCENT_CYAN : BORDER_COLOR) + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 2; -fx-background-radius: 2;" +
                        "-fx-padding: 9 14;"
        );
    }

    // ── Input Handler ──────────────────────────────────────────────────────────
    private void handleUserInput(String text) {
        // Built-in commands
        if (text.equalsIgnoreCase("flush")) {
            if (config != null) config.redisStore().deleteMessages(sessionID);
            addSystemMessage("L2 MEMORY FLUSHED — FRESH CONTEXT INITIALIZED");
            return;
        }
        if (text.equalsIgnoreCase("exit") || text.equalsIgnoreCase("shutdown")) {
            addSystemMessage("CRYSTALLIZING SESSION — SAFE TRAVELS, SIR.");
            PauseTransition d = new PauseTransition(Duration.seconds(1));
            d.setOnFinished(e -> {
                if (syncEngine != null) syncEngine.exitFlush(sessionID);
                if (config != null) config.neo4jDriver().close();
                Platform.exit();
            });
            d.play();
            return;
        }

        addUserMessage(text);

        if (arc == null) {
            addArcMessage("DIAGNOSTIC: Backend offline. Check config and restart.");
            return;
        }

        setStatus("PROCESSING...");

        // Open a new streaming ARC bubble
        currentArcLabel = beginArcBubble();

        arc.chat(sessionID, text)
                .onPartialResponse(token -> Platform.runLater(() -> {
                    currentArcLabel.setText(currentArcLabel.getText() + token);
                    scrollToBottom();
                }))
                .onCompleteResponse(resp -> Platform.runLater(() -> {
                    currentArcLabel = null;
                    List<ChatMessage> evicted = syncEngine.getAndPrune(sessionID, config.MEMORY_THRESHOLD);
                    if (!evicted.isEmpty()) syncEngine.archiveToL3(evicted);
                    setStatus("READY  //  ALL SYSTEMS NOMINAL");
                    scrollToBottom();
                }))
                .onError(t -> Platform.runLater(() -> {
                    if (currentArcLabel != null) {
                        currentArcLabel.setText(
                                "DIAGNOSTIC ERROR: " + t.getClass().getSimpleName()
                                        + " — " + t.getMessage());
                        currentArcLabel = null;
                    }
                    setStatus("DIAGNOSTIC FAILURE — CHECK LOGS");
                }))
                .start();
    }

    // ── Message Builders ───────────────────────────────────────────────────────

    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 0, 4, 0));

        VBox bubble = new VBox(5);
        bubble.setMaxWidth(680);
        bubble.setPadding(new Insets(13, 18, 13, 18));
        bubble.setStyle(
                "-fx-background-color: #0D1E2E;" +
                        "-fx-border-color: #1A3050;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4 4 0 4;" +
                        "-fx-background-radius: 4 4 0 4;"
        );

        Label name = new Label("YOU");
        name.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-letter-spacing: 2px;"
        );
        Label msg = new Label(text);
        msg.setWrapText(true);
        msg.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                        "-fx-line-spacing: 2;"
        );

        bubble.getChildren().addAll(name, msg);
        row.getChildren().add(bubble);
        fadeIn(row);
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    /**
     * Creates an ARC bubble with an empty label and returns it so tokens
     * can be streamed into it incrementally.
     */
    private Label beginArcBubble() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        // Cyan left accent stripe
        Rectangle stripe = new Rectangle(2.5, 1);
        stripe.setFill(Color.web(ACCENT_CYAN, 0.65));
        stripe.heightProperty().bind(row.heightProperty().subtract(8));

        VBox bubble = new VBox(6);
        bubble.setMaxWidth(700);
        bubble.setPadding(new Insets(13, 18, 13, 18));
        bubble.setStyle(
                "-fx-background-color: #07111A;" +
                        "-fx-border-color: " + ACCENT_DIM + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 0 4 4 4;" +
                        "-fx-background-radius: 0 4 4 4;"
        );

        // Header: name + timestamp
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label arcName = new Label("A.R.C.");
        arcName.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";" +
                        "-fx-letter-spacing: 3px;"
        );
        Label ts = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        ts.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";"
        );
        header.getChildren().addAll(arcName, ts);

        // Streaming target
        Label msgLabel = new Label("");
        msgLabel.setWrapText(true);
        msgLabel.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: " + ACCENT_CYAN + ";" +
                        "-fx-line-spacing: 3;"
        );

        bubble.getChildren().addAll(header, msgLabel);
        row.getChildren().addAll(stripe, bubble);

        fadeIn(row);
        chatBox.getChildren().add(row);
        scrollToBottom();
        return msgLabel;
    }

    /** One-shot (non-streaming) ARC message. */
    private void addArcMessage(String text) {
        Label lbl = beginArcBubble();
        lbl.setText(text);
    }

    private void addSystemMessage(String text) {
        // Use a StackPane so the label naturally centres within the full chatBox width
        StackPane row = new StackPane();
        row.setPadding(new Insets(10, 0, 10, 0));
        row.setMaxWidth(Double.MAX_VALUE);   // stretch to chatBox width

        Label lbl = new Label("◆  " + text + "  ◆");
        lbl.setWrapText(true);
        lbl.setTextAlignment(TextAlignment.CENTER);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle(
                "-fx-font-family: 'Courier New';" +
                        "-fx-font-size: 10px;" +
                        "-fx-text-fill: " + TEXT_DIM + ";" +
                        "-fx-letter-spacing: 2px;"
        );

        StackPane.setAlignment(lbl, Pos.CENTER);
        row.getChildren().add(lbl);
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    // ── Boot Sequence ──────────────────────────────────────────────────────────
    private void runBootSequence() {
        String[] sys = {
                "INITIALIZING CORE SYSTEMS",
                "REDIS L2 MEMORY CONNECTED",
                "NEO4J L3 GRAPH ONLINE",
                "RAG RETRIEVAL AUGMENTOR ACTIVE",
                "MCP TOOL PROVIDER REGISTERED",
                "ALL PROTOCOLS NOMINAL"
        };
        for (int i = 0; i < sys.length; i++) {
            final int idx = i;
            PauseTransition p = new PauseTransition(Duration.millis(300 * (i + 1)));
            p.setOnFinished(e -> {
                if (idx < sys.length - 1) addSystemMessage(sys[idx]);
                else addArcMessage("ALL SECTORS ONLINE. STANDING BY, BOSS.");
            });
            p.play();
        }
    }

    // ── Auth Dialog (file write/append confirmation) ───────────────────────────
    private void showAuthDialog(ToolExecutionRequest req, Stage owner) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("ARC — Authorization Required");
        alert.setHeaderText("Modification Protocol Detected");
        alert.setContentText(
                "Protocol:  " + req.name() + "\n" +
                        "Arguments: " + req.arguments() + "\n\n" +
                        "Authorize this file modification?"
        );
        alert.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) {
                throw new RuntimeException("Protocol Denied: Manual override by Boss.");
            }
        });
    }

    // ── Utilities ──────────────────────────────────────────────────────────────
    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void setStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
        ));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private void fadeIn(javafx.scene.Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(280), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private String buildCSS() {
        String css =
                ".scroll-bar:vertical { -fx-background-color: " + BG_PANEL + "; -fx-pref-width: 5px; }\n" +
                        ".scroll-bar:vertical .thumb { -fx-background-color: " + ACCENT_DIM + "; -fx-background-radius: 3; }\n" +
                        ".scroll-bar:vertical .track { -fx-background-color: transparent; }\n" +
                        ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-pref-height:0; -fx-pref-width:0; }\n" +
                        ".scroll-bar .increment-arrow,  .scroll-bar .decrement-arrow  { -fx-pref-height:0; -fx-pref-width:0; }\n" +
                        ".text-field { -fx-highlight-fill: " + ACCENT_DIM + "; -fx-highlight-text-fill: " + TEXT_PRIMARY + "; }\n" +
                        ".dialog-pane { -fx-background-color: " + BG_PANEL + "; }\n" +
                        ".dialog-pane .header-panel { -fx-background-color: " + BG_DEEP + "; }\n";
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("arc_", ".css");
            java.nio.file.Files.writeString(tmp, css);
            tmp.toFile().deleteOnExit();
            return tmp.toUri().toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}