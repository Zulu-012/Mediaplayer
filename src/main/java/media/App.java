package media;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Random;

public class App extends Application {

    // WINDOW
    private static final double WIDTH = 1270;
    private static final double HEIGHT = 690;

    private Stage primaryStage;
    private Scene scene;
    private Pane root;

    // MEDIA
    private MediaPlayer mediaPlayer;
    private File currentFile;
    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private int currentIndex = -1;

    // UI REFERENCES
    private ListView<File> playlistView;
    private Pane musicPane;
    private Pane listViewPane;

    // Labels
    private Label titleLabel;
    private Label artistLabel;
    private Label albumLabel;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private Label volumeValueLabel;
    private Label statusLabel;
    private Label pageIndicatorLabel;

    // Buttons
    private Button playBtn;
    private Button nextBtn;
    private Button previousBtn;
    private Button addBtn;
    private Button muteBtn;
    private Button volumeBtn;

    // Navigation buttons
    private Button videoModeBtn;
    private Button musicModeBtn;
    private Button exitBtn;

    // Icons
    private ImageView playIcon;
    private ImageView pauseIcon;
    private ImageView nextIcon;
    private ImageView previousIcon;
    private ImageView plusIcon;
    private ImageView muteIcon;
    private ImageView volumeIcon;
    private ImageView videoIcon;
    private ImageView musicIcon;
    private ImageView exitIcon;

    // Sliders
    private Slider durationSlider;
    private Slider volumeSlider;

    // Visualizer
    private final Rectangle[] bars = new Rectangle[24];
    private Timeline visualizerTimeline;
    private Random random = new Random();
    private boolean visualizerActive = false;

    // Album art
    private ImageView albumArtView;
    private ImageView placeholderImageView;
    private StackPane albumArtContainer;

    // Volume popup
    private HBox volumePopup;
    private boolean volumeVisible = false;

    // State
    private boolean muted = false;
    private double lastVolume = 0.7;

    // Background layer
    private Pane backgroundLayer;

    // START METHOD
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);

        // Background
        backgroundLayer = new Pane();
        backgroundLayer.setPrefSize(WIDTH, HEIGHT);
        backgroundLayer.setMouseTransparent(true);
        applyObsidian(backgroundLayer, WIDTH, HEIGHT);
        root.getChildren().add(backgroundLayer);
        backgroundLayer.toBack();

        // Load all icons
        loadIcons();

        // Build UI
        buildMusicPane();
        buildListViewPane();
        buildHeader();
        buildNavigation();

        // Add to root
        root.getChildren().addAll(musicPane, listViewPane);

        // Apply smoked glass effect
        applySmokedGlass(musicPane, 650, 530);

        // Ensure background is truly behind everything
        backgroundLayer.toBack();

        // Scene
        scene = new Scene(root, WIDTH, HEIGHT);
        registerKeyboardShortcuts();

        stage.setTitle("Penya Play Media Player");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        bringInteractiveToFront();

        root.requestFocus();
        startVisualizer();
        updatePlaybackButtons(false);
        statusLabel.setText("Ready — Add music to get started");
    }

    // BRING INTERACTIVE TO FRONT
    private void bringInteractiveToFront() {
        if (musicPane != null) {
            musicPane.toFront();
            for (int i = 5; i < musicPane.getChildren().size(); i++) {
                musicPane.getChildren().get(i).toFront();
            }
        }
        if (listViewPane != null) listViewPane.toFront();
        if (videoModeBtn != null) videoModeBtn.toFront();
        if (musicModeBtn != null) musicModeBtn.toFront();
        if (exitBtn != null) exitBtn.toFront();
        if (pageIndicatorLabel != null) pageIndicatorLabel.toFront();
    }

    // LOAD ICONS
    private void loadIcons() {
        playIcon = loadIcon("/icons/circle-play.png", 32, 32);
        pauseIcon = loadIcon("/icons/pause.png", 32, 32);
        nextIcon = loadIcon("/icons/skip-forward.png", 28, 28);
        previousIcon = loadIcon("/icons/skip-back.png", 28, 28);
        plusIcon = loadIcon("/icons/plus.png", 24, 24);
        muteIcon = loadIcon("/icons/volume-x.png", 24, 24);
        volumeIcon = loadIcon("/icons/volume.png", 24, 24);
        videoIcon = loadIcon("/icons/video.png", 16, 16);
        musicIcon = loadIcon("/icons/music.png", 16, 16);
        exitIcon = loadIcon("/icons/remove.png", 14, 14);
    }

    private ImageView loadIcon(String path, double width, double height) {
        try {
            Image img = new Image(getClass().getResource(path).toExternalForm());
            ImageView iv = new ImageView(img);
            iv.setFitWidth(width);
            iv.setFitHeight(height);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            return iv;
        } catch (Exception e) {
            System.err.println("Could not load icon: " + path);
            return new ImageView();
        }
    }

    // BUILD MUSIC PANE
    private void buildMusicPane() {
        musicPane = new Pane();
        musicPane.setPrefSize(650, 530);
        musicPane.setLayoutX(400);
        musicPane.setLayoutY(90);

        Label nowPlayingLabel = new Label("NOW PLAYING");
        nowPlayingLabel.setStyle(
                "-fx-text-fill: #64748B;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 10px;"
        );
        nowPlayingLabel.setLayoutX(280);
        nowPlayingLabel.setLayoutY(15);

        titleLabel = new Label("No Track Selected");
        titleLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 18px;"
        );
        titleLabel.setLayoutX(60);
        titleLabel.setLayoutY(35);
        titleLabel.setPrefWidth(530);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setEllipsisString("...");
        titleLabel.setMaxWidth(530);

        artistLabel = new Label("Unknown Artist");
        artistLabel.setStyle(
                "-fx-text-fill: #9CA3AF;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 12px;"
        );
        artistLabel.setLayoutX(60);
        artistLabel.setLayoutY(58);
        artistLabel.setPrefWidth(530);
        artistLabel.setAlignment(Pos.CENTER);

        albumLabel = new Label("Album • Track 00");
        albumLabel.setStyle(
                "-fx-text-fill: #6B7280;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 10px;"
        );
        albumLabel.setLayoutX(60);
        albumLabel.setLayoutY(76);
        albumLabel.setPrefWidth(530);
        albumLabel.setAlignment(Pos.CENTER);

        // ALBUM ART
        albumArtContainer = new StackPane();
        albumArtContainer.setPrefSize(280, 280);
        albumArtContainer.setLayoutX(185);
        albumArtContainer.setLayoutY(90);

        Circle artBg = new Circle(130);
        artBg.setFill(new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(40, 40, 45, 0.8)),
                new Stop(1, Color.rgb(20, 20, 25, 0.9))
        ));
        artBg.setStroke(Color.rgb(255, 255, 255, 0.1));
        artBg.setStrokeWidth(2);

        try {
            Image placeholderImg = new Image(
                    getClass().getResource("/icons/musicimage.png").toExternalForm()
            );
            placeholderImageView = new ImageView(placeholderImg);
            placeholderImageView.setFitWidth(240);
            placeholderImageView.setFitHeight(240);
            placeholderImageView.setPreserveRatio(true);
            placeholderImageView.setOpacity(0.6);
        } catch (Exception e) {
            placeholderImageView = new ImageView();
        }

        albumArtView = new ImageView();
        albumArtView.setFitWidth(240);
        albumArtView.setFitHeight(240);
        albumArtView.setPreserveRatio(true);
        albumArtView.setVisible(false);

        Rectangle clip = new Rectangle(240, 240);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        albumArtView.setClip(clip);

        albumArtContainer.getChildren().addAll(artBg, placeholderImageView, albumArtView);

        // VISUALIZER
        HBox visualizerBox = new HBox(4);
        visualizerBox.setAlignment(Pos.CENTER);
        visualizerBox.setLayoutX(60);
        visualizerBox.setLayoutY(355);
        visualizerBox.setPrefWidth(530);
        visualizerBox.setPrefHeight(55);

        for (int i = 0; i < bars.length; i++) {
            Rectangle bar = new Rectangle();
            bar.setWidth(14);
            bar.setHeight(8);
            bar.setArcWidth(4);
            bar.setArcHeight(4);
            bar.setFill(new LinearGradient(
                    0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#22C55E")),
                    new Stop(0.5, Color.web("#3B82F6")),
                    new Stop(1, Color.web("#8B5CF6"))
            ));
            bars[i] = bar;
            visualizerBox.getChildren().add(bar);
        }

        // DURATION SLIDER
        durationSlider = new Slider(0, 100, 0);
        durationSlider.setPrefWidth(580);
        durationSlider.setLayoutX(35);
        durationSlider.setLayoutY(410);
        durationSlider.setStyle(
                "-fx-control-inner-background: #1a1a1d;" +
                "-fx-accent: #22C55E;" +
                "-fx-background-color: transparent;"
        );

        currentTimeLabel = new Label("00:00");
        currentTimeLabel.setStyle(
                "-fx-text-fill: #9CA3AF;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 11px;"
        );
        currentTimeLabel.setLayoutX(35);
        currentTimeLabel.setLayoutY(430);

        totalTimeLabel = new Label("00:00");
        totalTimeLabel.setStyle(
                "-fx-text-fill: #9CA3AF;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 11px;"
        );
        totalTimeLabel.setLayoutX(580);
        totalTimeLabel.setLayoutY(430);

        // TRANSPORT BUTTONS (5)
        double btnY = 445;

        muteBtn = createCircleButton(muteIcon, 42, "#2a2a2e");
        muteBtn.setLayoutX(90);
        muteBtn.setLayoutY(btnY + 3);

        previousBtn = createCircleButton(previousIcon, 48, "#2a2a2e");
        previousBtn.setLayoutX(180);
        previousBtn.setLayoutY(btnY);

        playBtn = createPlayButton();
        playBtn.setLayoutX(291);
        playBtn.setLayoutY(btnY - 10);

        nextBtn = createCircleButton(nextIcon, 48, "#2a2a2e");
        nextBtn.setLayoutX(430);
        nextBtn.setLayoutY(btnY);

        volumeBtn = createCircleButton(volumeIcon, 42, "#2a2a2e");
        volumeBtn.setLayoutX(520);
        volumeBtn.setLayoutY(btnY + 3);

        // VOLUME POPUP
        volumePopup = new HBox(10);
        volumePopup.setAlignment(Pos.CENTER);
        volumePopup.setPrefWidth(230);
        volumePopup.setPrefHeight(50);
        volumePopup.setLayoutX(410);
        volumePopup.setLayoutY(btnY - 70);
        volumePopup.setPadding(new Insets(10, 14, 10, 14));
        volumePopup.setBackground(new Background(
                new BackgroundFill(
                        Color.rgb(20, 20, 24, 0.97),
                        new CornerRadii(12),
                        Insets.EMPTY
                )
        ));
        volumePopup.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.6)));
        volumePopup.setVisible(false);
        volumePopup.setManaged(false);

        volumeSlider = new Slider(0, 100, 70);
        volumeSlider.setPrefWidth(150);
        volumeSlider.setStyle(
                "-fx-control-inner-background: #374151;" +
                "-fx-accent: #3B82F6;"
        );

        volumeValueLabel = new Label("70%");
        volumeValueLabel.setStyle(
                "-fx-text-fill: #93C5FD;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 11px;"
        );
        volumeValueLabel.setMinWidth(38);

        volumePopup.getChildren().addAll(volumeSlider, volumeValueLabel);

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = newVal.intValue();
            volumeValueLabel.setText(value + "%");
            lastVolume = value / 100.0;
            if (mediaPlayer != null && !muted) {
                mediaPlayer.setVolume(lastVolume);
            }
        });

        // STATUS
        statusLabel = new Label("Ready");
        statusLabel.setStyle(
                "-fx-text-fill: #6B7280;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 10px;"
        );
        statusLabel.setLayoutX(60);
        statusLabel.setLayoutY(508);
        statusLabel.setPrefWidth(530);
        statusLabel.setAlignment(Pos.CENTER);

        musicPane.getChildren().addAll(
                nowPlayingLabel,
                titleLabel,
                artistLabel,
                albumLabel,
                albumArtContainer,
                visualizerBox,
                durationSlider,
                currentTimeLabel,
                totalTimeLabel,
                previousBtn,
                playBtn,
                nextBtn,
                muteBtn,
                volumeBtn,
                statusLabel,
                volumePopup
        );

        // Wiring
        playBtn.setOnAction(e -> {
            if (mediaPlayer == null) {
                if (!playlist.isEmpty()) {
                    loadTrack(playlist.get(0));
                }
                return;
            }
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                pauseMusic();
            } else {
                playMusic();
            }
        });
        nextBtn.setOnAction(e -> nextTrack());
        previousBtn.setOnAction(e -> previousTrack());
        muteBtn.setOnAction(e -> toggleMute());
        volumeBtn.setOnAction(e -> toggleVolumePopup());

        durationSlider.setOnMousePressed(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(durationSlider.getValue()));
            }
        });
        durationSlider.setOnMouseDragged(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(durationSlider.getValue()));
            }
        });
    }

    // VOLUME POPUP TOGGLE
    private void toggleVolumePopup() {
        volumeVisible = !volumeVisible;
        volumePopup.setVisible(volumeVisible);
        volumePopup.setManaged(volumeVisible);

        FadeTransition fade = new FadeTransition(Duration.millis(180), volumePopup);
        if (volumeVisible) {
            volumePopup.setOpacity(0);
            fade.setFromValue(0);
            fade.setToValue(1);
            volumePopup.toFront();
        } else {
            fade.setFromValue(1);
            fade.setToValue(0);
        }
        fade.play();
    }

    // BUILD LIST VIEW PANE
    private void buildListViewPane() {
        listViewPane = new Pane();
        listViewPane.setPrefSize(370, 560);
        listViewPane.setLayoutX(10);
        listViewPane.setLayoutY(100);

        Label playlistTitle = new Label("PLAYLIST");
        playlistTitle.setStyle(
                "-fx-text-fill: #22C55E;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 16px;"
        );
        playlistTitle.setLayoutX(15);
        playlistTitle.setLayoutY(5);

        Label countLabel = new Label("0 tracks");
        countLabel.setId("countLabel");
        countLabel.setStyle(
                "-fx-text-fill: #6B7280;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 11px;"
        );
        countLabel.setLayoutX(240);
        countLabel.setLayoutY(7);

        addBtn = new Button("", plusIcon);
        addBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-border-color: transparent;" +
                "-fx-cursor: hand;"
        );
        addBtn.setLayoutX(320);
        addBtn.setLayoutY(2);
        addBtn.setTooltip(new Tooltip("Add music files (Right-click for folder)"));

        playlistView = new ListView<>(playlist);
        playlistView.setPrefWidth(350);
        playlistView.setPrefHeight(490);
        playlistView.setLayoutX(10);
        playlistView.setLayoutY(30);
        playlistView.setStyle(
                "-fx-background-color: rgba(20, 20, 22, 0.35);" +
                "-fx-control-inner-background: rgba(20, 20, 22, 0.35);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(255, 255, 255, 0.08);" +
                "-fx-border-width: 1;"
        );

        playlistView.setCellFactory(lv -> new ListCell<File>() {
            private final Label nameLabel = new Label();
            private final Label indexLabel = new Label();
            private final Button cellRemoveBtn = new Button();
            private final HBox cellBox;

            {
                nameLabel.setStyle(
                        "-fx-text-fill: #2fab2f;" +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                        "-fx-font-size: 12px;"
                );
                nameLabel.setEllipsisString("...");
                nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setMinWidth(0);

                indexLabel.setStyle(
                        "-fx-text-fill: #6B7280;" +
                        "-fx-font-family: 'Consolas', monospace;" +
                        "-fx-font-size: 10px;"
                );
                indexLabel.setMinWidth(28);
                indexLabel.setAlignment(Pos.CENTER_RIGHT);

                ImageView cellRemoveIcon = loadIcon("/icons/remove.png", 14, 14);
                cellRemoveBtn.setGraphic(cellRemoveIcon);
                cellRemoveBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-border-color: transparent;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 2 4 2 4;"
                );
                cellRemoveBtn.setVisible(false);
                cellRemoveBtn.setTooltip(new Tooltip("Remove this track"));

                cellBox = new HBox(8);
                cellBox.setAlignment(Pos.CENTER_LEFT);
                cellBox.getChildren().addAll(indexLabel, nameLabel, cellRemoveBtn);

                setGraphic(cellBox);
                setPadding(new Insets(4, 8, 4, 8));

                setOnMouseEntered(e -> {
                    if (getItem() != null) {
                        cellRemoveBtn.setVisible(true);
                    }
                });
                setOnMouseExited(e -> {
                    boolean isSelected = getListView().getSelectionModel()
                            .getSelectedIndices().contains(getIndex());
                    cellRemoveBtn.setVisible(isSelected);
                });

                cellRemoveBtn.setOnAction(e -> {
                    File item = getItem();
                    if (item != null) {
                        removeTrack(item);
                    }
                    e.consume();
                });
            }

            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(cellBox);
                    indexLabel.setText(String.format("%02d", getIndex() + 1));

                    String name = item.getName();
                    if (name.length() > 35) {
                        name = name.substring(0, 32) + "...";
                    }
                    nameLabel.setText(name);

                    if (getIndex() == currentIndex) {
                        setStyle(
                                "-fx-background-color: rgba(237, 243, 239, 0.2);" +
                                "-fx-background-radius: 8;"
                        );
                        nameLabel.setStyle(
                                "-fx-text-fill: #22C55E;" +
                                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                                "-fx-font-size: 12px;" +
                                "-fx-font-weight: bold;"
                        );
                    } else {
                        setStyle("-fx-background-color: transparent;");
                        nameLabel.setStyle(
                                "-fx-text-fill: #E5E7EB;" +
                                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                                "-fx-font-size: 12px;"
                        );
                    }

                    boolean isSelected = getListView().getSelectionModel()
                            .getSelectedIndices().contains(getIndex());
                    cellRemoveBtn.setVisible(isSelected);
                }
            }
        });

        playlistView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        currentIndex = playlist.indexOf(newValue);
                        loadTrack(newValue);
                    }
                    playlistView.refresh();
                }
        );

        playlistView.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                File f = playlistView.getSelectionModel().getSelectedItem();
                if (f != null) {
                    loadTrack(f);
                    playMusic();
                }
            }
        });

        addBtn.setOnAction(e -> addMusicFiles());
        addBtn.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.SECONDARY) {
                addMusicFolder();
            }
        });

        playlist.addListener((javafx.collections.ListChangeListener<File>) change -> {
            Label cl = (Label) listViewPane.lookup("#countLabel");
            if (cl != null) {
                cl.setText(playlist.size() + " tracks");
            }
        });

        listViewPane.getChildren().addAll(
                playlistTitle,
                countLabel,
                addBtn,
                playlistView
        );
    }

    // BUILD HEADER
    private void buildHeader() {
        Font blackops;
        try {
            blackops = Font.loadFont(
                    getClass().getResource("/fonts/blackops.ttf").toExternalForm(),
                    50
            );
        } catch (Exception e) {
            blackops = Font.font("System", FontWeight.BOLD, 50);
        }
        if (blackops == null) {
            blackops = Font.font("System", FontWeight.BOLD, 50);
        }

        Label header = new Label("Penya");
        header.setLayoutX(15);
        header.setLayoutY(5);
        header.setStyle("-fx-text-fill: white;");
        header.setFont(blackops);

        Label headerP = new Label("Play");
        headerP.setLayoutX(185);
        headerP.setLayoutY(5);
        headerP.setStyle("-fx-text-fill: #228B22;");
        headerP.setFont(blackops);

        pageIndicatorLabel = new Label("MUSIC MODE");
        pageIndicatorLabel.setStyle(
                "-fx-text-fill: #22C55E;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 15px;" +
                "-fx-background-color: rgba(34, 197, 94, 0.12);" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 4 12 4 12;"
        );
        pageIndicatorLabel.setLayoutX(1080);
        pageIndicatorLabel.setLayoutY(20);

        root.getChildren().addAll(header, headerP, pageIndicatorLabel);
        header.toFront();
        headerP.toFront();
        pageIndicatorLabel.toFront();
    }

    // BUILD NAVIGATION
    private void buildNavigation() {
        // ---- VIDEOS button ----
        videoModeBtn = new Button("Videos", videoIcon);
        videoModeBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #228B22;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;"
        );
        videoModeBtn.setLayoutX(400);
        videoModeBtn.setLayoutY(635);
        videoModeBtn.setMinHeight(34);
        videoModeBtn.setMinWidth(90);

        // ---- MUSIC button ----
        musicModeBtn = new Button("Music", musicIcon);
        musicModeBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #228B22;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;"
        );
        musicModeBtn.setLayoutX(510);
        musicModeBtn.setLayoutY(635);
        musicModeBtn.setMinHeight(34);
        musicModeBtn.setMinWidth(90);

        // ---- EXIT button ----
        exitBtn = new Button("Exit", exitIcon);
        exitBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #8B0000;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;"
        );
        exitBtn.setLayoutX(620);
        exitBtn.setLayoutY(635);
        exitBtn.setMinHeight(34);
        exitBtn.setMinWidth(90);

        // ---- ACTIONS ----
        // Video → hide the music window, open the video window
        videoModeBtn.setOnAction(e -> {
            try {
                // Stop any playing music so it doesn't overlap the video audio
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                VideoPlayer vp = new VideoPlayer();

                // When the video window is closed, bring the music window back
                Stage videoStage = new Stage();
                videoStage.setOnHidden(ev -> {
                    primaryStage.show();
                    primaryStage.requestFocus();
                });

                // Hide the music window — it stays in memory
                primaryStage.hide();

                vp.show(videoStage);
            } catch (Exception ex) {
                ex.printStackTrace();
                primaryStage.show();
            }
        });

        // Music → indicate we are in Music mode
        musicModeBtn.setOnAction(e -> {
            pageIndicatorLabel.setText("♪ MUSIC MODE");
            statusLabel.setText("You are on the Music page");
        });

        // Exit → close application
        exitBtn.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
            Platform.exit();
        });

        root.getChildren().addAll(videoModeBtn, musicModeBtn, exitBtn);
        videoModeBtn.toFront();
        musicModeBtn.toFront();
        exitBtn.toFront();
    }

    // BUTTON FACTORY
    private Button createCircleButton(ImageView icon, double size, String bgColor) {
        Button button = new Button();
        button.setGraphic(icon);
        button.setPrefSize(size, size);
        button.setMinSize(size, size);
        button.setMaxSize(size, size);
        button.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: " + (size / 2) + ";" +
                "-fx-border-color: rgba(255, 255, 255, 0.1);" +
                "-fx-border-radius: " + (size / 2) + ";" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> {
            button.setScaleX(1.08);
            button.setScaleY(1.08);
            button.setStyle(
                    "-fx-background-color: #2563EB;" +
                    "-fx-background-radius: " + (size / 2) + ";" +
                    "-fx-border-color: rgba(255, 255, 255, 0.2);" +
                    "-fx-border-radius: " + (size / 2) + ";" +
                    "-fx-border-width: 1;" +
                    "-fx-cursor: hand;"
            );
        });

        button.setOnMouseExited(e -> {
            button.setScaleX(1);
            button.setScaleY(1);
            button.setStyle(
                    "-fx-background-color: " + bgColor + ";" +
                    "-fx-background-radius: " + (size / 2) + ";" +
                    "-fx-border-color: rgba(255, 255, 255, 0.1);" +
                    "-fx-border-radius: " + (size / 2) + ";" +
                    "-fx-border-width: 1;" +
                    "-fx-cursor: hand;"
            );
        });

        return button;
    }

    private Button createPlayButton() {
        Button button = new Button();
        button.setGraphic(playIcon);
        button.setPrefSize(68, 68);
        button.setMinSize(68, 68);
        button.setMaxSize(68, 68);
        button.setStyle(
                "-fx-background-color: #2a2a2e;" +
                "-fx-background-radius: 34;" +
                "-fx-border-color: rgba(255, 255, 255, 0.2);" +
                "-fx-border-radius: 34;" +
                "-fx-border-width: 2;" +
                "-fx-cursor: hand;"
        );
        button.setEffect(new DropShadow(18, Color.rgb(34, 197, 94, 0.45)));

        button.setOnMouseEntered(e -> {
            button.setScaleX(1.08);
            button.setScaleY(1.08);
        });
        button.setOnMouseExited(e -> {
            button.setScaleX(1);
            button.setScaleY(1);
        });

        return button;
    }

    // FILE CHOOSER
    private void addMusicFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Music");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Audio Files",
                        "*.mp3", "*.wav", "*.m4a", "*.aac", "*.flac"
                )
        );

        List<File> files = chooser.showOpenMultipleDialog(scene.getWindow());
        if (files == null) return;

        for (File f : files) {
            if (f != null && f.isFile() && !playlist.contains(f)) {
                playlist.add(f);
            }
        }

        if (currentIndex == -1 && !playlist.isEmpty()) {
            currentIndex = 0;
            playlistView.getSelectionModel().select(0);
            loadTrack(playlist.get(0));
        }

        statusLabel.setText("Added " + files.size() + " track(s)");
        playlistView.refresh();
    }

    private void addMusicFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add Music Folder");
        File dir = chooser.showDialog(scene.getWindow());
        if (dir != null) {
            scanFolder(dir);
            playlistView.refresh();
        }
    }

    private void scanFolder(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        int added = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                scanFolder(f);
            } else if (isAudioFile(f) && !playlist.contains(f)) {
                playlist.add(f);
                added++;
            }
        }
        statusLabel.setText("Added " + added + " track(s) from folder");
    }

    private boolean isAudioFile(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".wav")
                || n.endsWith(".aac") || n.endsWith(".m4a")
                || n.endsWith(".flac");
    }

    // REMOVE TRACK
    private void removeTrack(File file) {
        if (file == null) return;
        int index = playlist.indexOf(file);
        if (index < 0) return;

        if (mediaPlayer != null && index == currentIndex) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        playlist.remove(index);

        if (playlist.isEmpty()) {
            currentIndex = -1;
            titleLabel.setText("No Track Selected");
            artistLabel.setText("Unknown Artist");
            albumLabel.setText("Album • Track 00");
            durationSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
            updatePlaybackButtons(false);
            resetAlbumArt();
            playlistView.refresh();
            return;
        }

        if (index >= playlist.size()) {
            index = playlist.size() - 1;
        }

        currentIndex = index;
        playlistView.getSelectionModel().select(index);
        loadTrack(playlist.get(index));
        playlistView.refresh();
    }

    // LOAD TRACK
    private void loadTrack(File file) {
        if (file == null) return;
        currentFile = file;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        try {
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(muted ? 0 : lastVolume);

            titleLabel.setText(file.getName());
            artistLabel.setText("Local Music Library");
            albumLabel.setText("Track " + String.format("%02d", currentIndex + 1));

            loadAlbumArt(media);

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                durationSlider.setMax(total.toSeconds());
                totalTimeLabel.setText(formatTime(total));
                currentTimeLabel.setText("00:00");
                statusLabel.setText("Ready: " + file.getName());
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!durationSlider.isValueChanging()) {
                    durationSlider.setValue(newTime.toSeconds());
                }
                currentTimeLabel.setText(formatTime(newTime));
            });

            mediaPlayer.setOnEndOfMedia(this::nextTrack);
            mediaPlayer.setOnError(() -> {
                statusLabel.setText("Error playing: " + file.getName());
            });

            playMusic();

        } catch (Exception e) {
            statusLabel.setText("Cannot play: " + file.getName());
            System.err.println("Error loading track: " + e.getMessage());
        }
    }

    private void loadAlbumArt(Media media) {
        try {
            Object art = media.getMetadata().get("image");
            if (art instanceof Image) {
                albumArtView.setImage((Image) art);
                albumArtView.setVisible(true);
                placeholderImageView.setVisible(false);
                return;
            }
        } catch (Exception e) {
            // No embedded art
        }
        resetAlbumArt();
    }

    private void resetAlbumArt() {
        albumArtView.setImage(null);
        albumArtView.setVisible(false);
        placeholderImageView.setVisible(true);
    }

    // PLAYBACK CONTROLS
    private void playMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
            playBtn.setGraphic(pauseIcon);
            visualizerActive = true;
            statusLabel.setText("Playing");
        } else if (!playlist.isEmpty()) {
            loadTrack(playlist.get(0));
        }
    }

    private void pauseMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            playBtn.setGraphic(playIcon);
            visualizerActive = false;
            statusLabel.setText("Paused");
        }
    }

    private void stopMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            durationSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            playBtn.setGraphic(playIcon);
            visualizerActive = false;
            statusLabel.setText("Stopped");
        }
    }

    private void updatePlaybackButtons(boolean isPlaying) {
        if (isPlaying) {
            playBtn.setGraphic(pauseIcon);
            visualizerActive = true;
        } else {
            playBtn.setGraphic(playIcon);
            visualizerActive = false;
        }
    }

    private void toggleMute() {
        if (mediaPlayer == null) return;

        muted = !muted;

        if (muted) {
            lastVolume = mediaPlayer.getVolume();
            mediaPlayer.setVolume(0);
            muteBtn.setGraphic(muteIcon);
            statusLabel.setText("Muted");
        } else {
            mediaPlayer.setVolume(lastVolume);
            muteBtn.setGraphic(muteIcon);
            statusLabel.setText("Unmuted");
        }
    }

    // NEXT / PREVIOUS
    private void nextTrack() {
        if (playlist.isEmpty()) return;
        currentIndex++;
        if (currentIndex >= playlist.size()) {
            currentIndex = 0;
        }
        playlistView.getSelectionModel().select(currentIndex);
        loadTrack(playlist.get(currentIndex));
    }

    private void previousTrack() {
        if (playlist.isEmpty()) return;
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = playlist.size() - 1;
        }
        playlistView.getSelectionModel().select(currentIndex);
        loadTrack(playlist.get(currentIndex));
    }

    // VISUALIZER ANIMATION
    private void startVisualizer() {
        visualizerTimeline = new Timeline(
                new KeyFrame(Duration.millis(80), e -> {
                    for (Rectangle bar : bars) {
                        double targetHeight;
                        if (visualizerActive) {
                            targetHeight = 7 + random.nextDouble() * 45;
                        } else {
                            targetHeight = 7;
                        }
                        Timeline barAnim = new Timeline(
                                new KeyFrame(Duration.millis(100),
                                        new KeyValue(bar.heightProperty(), targetHeight,
                                                Interpolator.EASE_BOTH))
                        );
                        barAnim.play();
                    }
                })
        );
        visualizerTimeline.setCycleCount(Animation.INDEFINITE);
        visualizerTimeline.play();
    }

    // FORMAT TIME
    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int total = (int) Math.floor(duration.toSeconds());
        int minutes = total / 60;
        int seconds = total % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // KEYBOARD SHORTCUTS
    private void registerKeyboardShortcuts() {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case SPACE -> {
                    if (mediaPlayer == null) return;
                    if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                        pauseMusic();
                    } else {
                        playMusic();
                    }
                    event.consume();
                }
                case S -> { stopMusic(); event.consume(); }
                case N -> { nextTrack(); event.consume(); }
                case P -> { previousTrack(); event.consume(); }
                case M -> { toggleMute(); event.consume(); }
                case UP -> {
                    double value = Math.min(100, volumeSlider.getValue() + 5);
                    volumeSlider.setValue(value);
                    event.consume();
                }
                case DOWN -> {
                    double value = Math.max(0, volumeSlider.getValue() - 5);
                    volumeSlider.setValue(value);
                    event.consume();
                }
                case LEFT -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seek(mediaPlayer.getCurrentTime().subtract(Duration.seconds(5)));
                    }
                    event.consume();
                }
                case RIGHT -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(5)));
                    }
                    event.consume();
                }
                default -> { }
            }
        });
    }

    // VISUAL EFFECTS
    private void applyObsidian(Pane pane, double width, double height) {
        pane.setBackground(new Background(
                new BackgroundFill(Color.web("#100e0b"), null, null)
        ));

        Rectangle layer1 = new Rectangle(width, height);
        layer1.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#030303")),
                new Stop(0.42, Color.web("#111111")),
                new Stop(0.72, Color.web("#1c1c1c")),
                new Stop(1.00, Color.web("#090909"))
        ));
        layer1.setMouseTransparent(true);

        Circle glow = new Circle();
        glow.setRadius(width * 0.45);
        glow.setCenterX(width * 0.68);
        glow.setCenterY(height * 0.38);
        glow.setFill(new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(161, 161, 170, 0.16)),
                new Stop(0.65, Color.TRANSPARENT)
        ));
        glow.setEffect(new BoxBlur(18, 18, 3));
        glow.setBlendMode(BlendMode.SCREEN);
        glow.setMouseTransparent(true);

        Random rnd = new Random();
        Pane grain = new Pane();
        grain.setMouseTransparent(true);
        for (int i = 0; i < 1200; i++) {
            Circle dot = new Circle(
                    rnd.nextDouble() * width,
                    rnd.nextDouble() * height,
                    0.5
            );
            dot.setFill(Color.rgb(180, 180, 180, rnd.nextDouble() * 0.08));
            grain.getChildren().add(dot);
        }
        grain.setBlendMode(BlendMode.OVERLAY);

        pane.getChildren().addAll(layer1, glow, grain);
    }

    private void applySmokedGlass(Pane pane, double width, double height) {
        pane.setBackground(new Background(
                new BackgroundFill(
                        Color.rgb(22, 22, 25, 0.12),
                        new CornerRadii(30),
                        Insets.EMPTY
                )
        ));

        Rectangle layer1 = new Rectangle(width, height);
        layer1.setArcWidth(30);
        layer1.setArcHeight(30);
        layer1.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.rgb(15, 15, 17, 0.95)),
                new Stop(0.50, Color.rgb(39, 39, 42, 0.80)),
                new Stop(1.00, Color.rgb(63, 63, 70, 0.55))
        ));
        layer1.setMouseTransparent(true);

        Circle glowLeft = new Circle(width * 0.30, height * 0.35, width * 0.42);
        glowLeft.setFill(new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(113, 113, 122, 0.35)),
                new Stop(0.40, Color.TRANSPARENT)
        ));
        glowLeft.setEffect(new BoxBlur(180, 180, 3));
        glowLeft.setBlendMode(BlendMode.SCREEN);
        glowLeft.setMouseTransparent(true);

        Circle glowRight = new Circle(width * 0.72, height * 0.65, width * 0.38);
        glowRight.setFill(new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(161, 161, 170, 0.25)),
                new Stop(0.35, Color.TRANSPARENT)
        ));
        glowRight.setEffect(new BoxBlur(216, 216, 3));
        glowRight.setBlendMode(BlendMode.SCREEN);
        glowRight.setMouseTransparent(true);

        Rectangle sheen = new Rectangle(width, height);
        sheen.setArcWidth(30);
        sheen.setArcHeight(30);
        sheen.setFill(new LinearGradient(
                0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.rgb(255, 255, 255, 0.05)),
                new Stop(0.50, Color.rgb(255, 255, 255, 0.00)),
                new Stop(1.00, Color.rgb(255, 255, 255, 0.03))
        ));
        sheen.setEffect(new BoxBlur(36, 36, 3));
        sheen.setBlendMode(BlendMode.OVERLAY);
        sheen.setMouseTransparent(true);

        Rectangle border = new Rectangle(width, height);
        border.setArcWidth(30);
        border.setArcHeight(30);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.rgb(255, 255, 255, 0.08));
        border.setStrokeWidth(1.2);
        border.setMouseTransparent(true);

        pane.getChildren().addAll(0, List.of(layer1, glowLeft, glowRight, sheen, border));
    }

    // CLEANUP
    @Override
    public void stop() {
        if (visualizerTimeline != null) {
            visualizerTimeline.stop();
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }

    // MAIN
    public static void main(String[] args) {
        launch();
    }
}