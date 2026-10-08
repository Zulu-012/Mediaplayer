package media;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
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

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

// vlcj-javafx 1.2.0 class
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;

import java.io.File;
import java.util.List;
import java.util.Random;

public class VideoPlayer {

    // WINDOW
    private static final double WIDTH = 1270;
    private static final double HEIGHT = 690;

    private Stage stage;
    private Scene scene;
    private Pane root;

    // MEDIA — VLCJ engine
    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer vlcPlayer;
    private ImageView mediaView;
    private File currentFile;
    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private int currentIndex = -1;
    private volatile boolean seekingFromSlider = false;

    // UI REFERENCES
    private ListView<File> playlistView;
    private Pane videoPane;
    private Pane listViewPane;
    private StackPane videoContainer;

    // Labels
    private Label titleLabel;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private Label volumeValueLabel;
    private Label statusLabel;
    private Label pageIndicatorLabel;
    private Label headerLabel;
    private Label headerPLabel;

    // Buttons
    private Button playBtn;
    private Button nextBtn;
    private Button previousBtn;
    private Button addBtn;
    private Button muteBtn;
    private Button volumeBtn;
    private Button fullScreenBtn;
    private Button minimizeBtn;

    // Navigation
    private Button musicModeBtn;
    private Button videoModeBtn;
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
    private ImageView fullScreenIcon;
    private ImageView minimizeIcon;

    // Sliders
    private Slider durationSlider;
    private Slider volumeSlider;

    // Volume popup
    private HBox volumePopup;
    private boolean volumeVisible = false;

    // Background
    private Pane backgroundLayer;

    // Placeholder
    private ImageView placeholderImage;

    // State
    private boolean muted = false;
    private double lastVolume = 0.85;
    private boolean isFullScreen = false;

    // PUBLIC ENTRY POINT
    public void show(Stage stage) {
        this.stage = stage;
        build(stage);
    }

    // BUILD EVERYTHING
    private void build(Stage stage) {
        this.stage = stage;

        root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);

        // Background
        backgroundLayer = new Pane();
        backgroundLayer.setPrefSize(WIDTH, HEIGHT);
        backgroundLayer.setMouseTransparent(true);
        applyObsidian(backgroundLayer, WIDTH, HEIGHT);
        root.getChildren().add(backgroundLayer);
        backgroundLayer.toBack();

        // Load icons
        loadIcons();

        // Build UI
        buildVideoPane();
        buildListViewPane();
        buildHeader();
        buildNavigation();

        // Add to root
        root.getChildren().addAll(videoPane, listViewPane);

        // Smoked glass on videoPane
        applySmokedGlass(videoPane, 870, 600);

        // Ensure background is truly behind everything
        backgroundLayer.toBack();

        // Scene
        scene = new Scene(root, WIDTH, HEIGHT);
        registerKeyboardShortcuts();

        // Init VLCJ engine BEFORE showing the stage
        initVlcjEngine();

        stage.setTitle("PenyaPlay — Video Player");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        // Clean up VLC when the window closes
        stage.setOnCloseRequest(e -> releaseVlcj());

        // Bring interactive to front
        bringInteractiveToFront();

        root.requestFocus();
        statusLabel.setText("Ready — Add videos to get started");
        updatePlaybackButtons(false);
    }

    // =========================================================
    // VLCJ INIT / RELEASE
    // =========================================================
    private void initVlcjEngine() {
        // Tell JNA where VLC lives (in case it isn't on the system PATH)
        if (System.getProperty("jna.library.path") == null) {
            System.setProperty("jna.library.path", "C:\\Program Files\\VideoLAN\\VLC");
        }

        try {
            // Silence the noisy VLC plugin-cache errors and use DirectSound
            // (avoids the harmless "mmdevice audio output error: cannot initialize COM")
            String[] vlcArgs = {
                    "--quiet",
                    "--aout=directsound"
            };

            mediaPlayerFactory = new MediaPlayerFactory(vlcArgs);
            vlcPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();

            // The ImageView that will show the video frames
            mediaView = new ImageView();
            mediaView.setFitWidth(830);
            mediaView.setFitHeight(400);
            mediaView.setPreserveRatio(true);
            mediaView.setSmooth(true);

            // Use ImageViewVideoSurface from vlcj-javafx 1.2.0
            vlcPlayer.videoSurface().set(new ImageViewVideoSurface(mediaView));

            // Player event hooks
            vlcPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void timeChanged(MediaPlayer mp, long newTime) {
                    Platform.runLater(() -> {
                        if (!seekingFromSlider) {
                            durationSlider.setValue(newTime / 1000.0);
                        }
                        currentTimeLabel.setText(formatMillis(newTime));
                    });
                }

                @Override
                public void lengthChanged(MediaPlayer mp, long newLength) {
                    Platform.runLater(() -> {
                        durationSlider.setMax(newLength / 1000.0);
                        totalTimeLabel.setText(formatMillis(newLength));
                    });
                }

                @Override
                public void playing(MediaPlayer mp) {
                    Platform.runLater(() -> {
                        playBtn.setGraphic(pauseIcon);
                        statusLabel.setText("Playing");
                    });
                }

                @Override
                public void paused(MediaPlayer mp) {
                    Platform.runLater(() -> {
                        playBtn.setGraphic(playIcon);
                        statusLabel.setText("Paused");
                    });
                }

                @Override
                public void stopped(MediaPlayer mp) {
                    Platform.runLater(() -> playBtn.setGraphic(playIcon));
                }

                @Override
                public void finished(MediaPlayer mp) {
                    Platform.runLater(VideoPlayer.this::nextVideo);
                }

                @Override
                public void error(MediaPlayer mp) {
                    Platform.runLater(() -> statusLabel.setText("Playback error"));
                }
            });

            // Attach the ImageView to the videoContainer
            if (videoContainer != null) {
                videoContainer.getChildren().add(mediaView);
                mediaView.setVisible(false);
            }

        } catch (Throwable t) {
            System.err.println("Failed to init VLCJ: " + t.getMessage());
            System.err.println("Is VLC installed? Default path: C:\\Program Files\\VideoLAN\\VLC");
            if (statusLabel != null) {
                statusLabel.setText("VLC engine not available — install VLC");
            }
        }
    }

    private void releaseVlcj() {
        try {
            if (vlcPlayer != null) {
                vlcPlayer.controls().stop();
                vlcPlayer.release();
            }
            if (mediaPlayerFactory != null) {
                mediaPlayerFactory.release();
            }
        } catch (Throwable ignored) {}
    }

    // BRING INTERACTIVE TO FRONT
    private void bringInteractiveToFront() {
        if (videoPane != null) {
            videoPane.toFront();
            for (int i = 5; i < videoPane.getChildren().size(); i++) {
                videoPane.getChildren().get(i).toFront();
            }
        }
        if (listViewPane != null) listViewPane.toFront();
        if (musicModeBtn != null) musicModeBtn.toFront();
        if (videoModeBtn != null) videoModeBtn.toFront();
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
        fullScreenIcon = loadIcon("/icons/fullscreen.png", 20, 20);
        minimizeIcon = loadIcon("/icons/minimize-2.png", 20, 20);
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

    // BUILD VIDEO PANE
    private void buildVideoPane() {
        videoPane = new Pane();
        videoPane.setPrefSize(870, 600);
        videoPane.setLayoutX(390);
        videoPane.setLayoutY(80);

        Label nowPlayingLabel = new Label("NOW PLAYING");
        nowPlayingLabel.setStyle(
                "-fx-text-fill: #64748B;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 10px;"
        );
        nowPlayingLabel.setLayoutX(390);
        nowPlayingLabel.setLayoutY(8);

        titleLabel = new Label("No Video Selected");
        titleLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 18px;"
        );
        titleLabel.setLayoutX(60);
        titleLabel.setLayoutY(28);
        titleLabel.setPrefWidth(750);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setEllipsisString("...");
        titleLabel.setMaxWidth(750);

        videoContainer = new StackPane();
        videoContainer.setPrefSize(830, 400);
        videoContainer.setLayoutX(20);
        videoContainer.setLayoutY(60);
        videoContainer.setStyle(
                "-fx-background-color: rgba(10, 10, 12, 0.85);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: rgba(255, 255, 255, 0.08);" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1;"
        );

        placeholderImage = loadIcon("/icons/video.png", 200, 200);
        placeholderImage.setOpacity(0.15);

        videoContainer.getChildren().add(placeholderImage);
        videoContainer.setId("videoContainer");

        durationSlider = new Slider(0, 100, 0);
        durationSlider.setPrefWidth(830);
        durationSlider.setLayoutX(20);
        durationSlider.setLayoutY(478);
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
        currentTimeLabel.setLayoutX(20);
        currentTimeLabel.setLayoutY(496);

        totalTimeLabel = new Label("00:00");
        totalTimeLabel.setStyle(
                "-fx-text-fill: #9CA3AF;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 11px;"
        );
        totalTimeLabel.setLayoutX(818);
        totalTimeLabel.setLayoutY(496);

        double rowCenterY = 546;
        double centerX = 435;

        muteBtn = createCircleButton(muteIcon, 42, "#2a2a2e");
        muteBtn.setLayoutX(centerX - 200 - 21);
        muteBtn.setLayoutY(rowCenterY - 21);

        previousBtn = createCircleButton(previousIcon, 48, "#2a2a2e");
        previousBtn.setLayoutX(centerX - 130 - 24);
        previousBtn.setLayoutY(rowCenterY - 24);

        playBtn = createPlayButton();
        playBtn.setLayoutX(centerX - 34);
        playBtn.setLayoutY(rowCenterY - 34);

        nextBtn = createCircleButton(nextIcon, 48, "#2a2a2e");
        nextBtn.setLayoutX(centerX + 130 - 24);
        nextBtn.setLayoutY(rowCenterY - 24);

        fullScreenBtn = createCircleButton(fullScreenIcon, 42, "#2a2a2e");
        fullScreenBtn.setLayoutX(centerX + 200 - 21);
        fullScreenBtn.setLayoutY(rowCenterY - 21);
        fullScreenBtn.setTooltip(new Tooltip("Full Screen"));

        minimizeBtn = createCircleButton(minimizeIcon, 42, "#2a2a2e");
        minimizeBtn.setLayoutX(centerX + 260 - 21);
        minimizeBtn.setLayoutY(rowCenterY - 21);
        minimizeBtn.setTooltip(new Tooltip("Restore default size"));

        volumeBtn = createCircleButton(volumeIcon, 42, "#2a2a2e");
        volumeBtn.setLayoutX(830 - 21);
        volumeBtn.setLayoutY(rowCenterY - 21);

        volumePopup = new HBox(10);
        volumePopup.setAlignment(Pos.CENTER);
        volumePopup.setPrefWidth(230);
        volumePopup.setPrefHeight(50);
        volumePopup.setLayoutX(620);
        volumePopup.setLayoutY(440);
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

        volumeSlider = new Slider(0, 100, 85);
        volumeSlider.setPrefWidth(150);
        volumeSlider.setStyle(
                "-fx-control-inner-background: #374151;" +
                "-fx-accent: #3B82F6;"
        );

        volumeValueLabel = new Label("85%");
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
            if (vlcPlayer != null && !muted) {
                vlcPlayer.audio().setVolume(value);
            }
        });

        statusLabel = new Label("Ready");
        statusLabel.setStyle(
                "-fx-text-fill: #6B7280;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-font-size: 10px;"
        );
        statusLabel.setLayoutX(60);
        statusLabel.setLayoutY(580);
        statusLabel.setPrefWidth(750);
        statusLabel.setAlignment(Pos.CENTER);

        videoPane.getChildren().addAll(
                nowPlayingLabel,
                titleLabel,
                videoContainer,
                durationSlider,
                currentTimeLabel,
                totalTimeLabel,
                muteBtn,
                previousBtn,
                playBtn,
                nextBtn,
                fullScreenBtn,
                minimizeBtn,
                volumeBtn,
                statusLabel,
                volumePopup
        );

        playBtn.setOnAction(e -> {
            if (vlcPlayer == null) {
                if (!playlist.isEmpty()) {
                    loadVideo(playlist.get(0));
                }
                return;
            }
            if (vlcPlayer.status().isPlaying()) {
                pauseVideo();
            } else {
                playVideo();
            }
        });
        nextBtn.setOnAction(e -> nextVideo());
        previousBtn.setOnAction(e -> previousVideo());
        muteBtn.setOnAction(e -> toggleMute());
        volumeBtn.setOnAction(e -> toggleVolumePopup());
        fullScreenBtn.setOnAction(e -> enterFullScreen());
        minimizeBtn.setOnAction(e -> exitFullScreen());

        durationSlider.setOnMousePressed(e -> {
            seekingFromSlider = true;
        });
        durationSlider.setOnMouseReleased(e -> {
            if (vlcPlayer != null) {
                long targetMs = (long) (durationSlider.getValue() * 1000);
                vlcPlayer.controls().setTime(targetMs);
            }
            seekingFromSlider = false;
        });
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

        Label countLabel = new Label("0 videos");
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
        addBtn.setTooltip(new Tooltip("Add videos (Right-click for folder)"));

        playlistView = new ListView<>(playlist);
        playlistView.setPrefWidth(350);
        playlistView.setPrefHeight(550);
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
                        "-fx-text-fill: #E5E7EB;" +
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
                cellRemoveBtn.setTooltip(new Tooltip("Remove this video"));

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
                        removeVideo(item);
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
                                "-fx-background-color: rgba(34, 197, 94, 0.2);" +
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
                        loadVideo(newValue);
                    }
                    playlistView.refresh();
                }
        );

        playlistView.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                File f = playlistView.getSelectionModel().getSelectedItem();
                if (f != null) {
                    loadVideo(f);
                    playVideo();
                }
            }
        });

        addBtn.setOnAction(e -> addVideoFiles());
        addBtn.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.SECONDARY) {
                addVideoFolder();
            }
        });

        playlist.addListener((javafx.collections.ListChangeListener<File>) change -> {
            Label cl = (Label) listViewPane.lookup("#countLabel");
            if (cl != null) {
                cl.setText(playlist.size() + " videos");
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

        headerLabel = new Label("Penya");
        headerLabel.setLayoutX(15);
        headerLabel.setLayoutY(5);
        headerLabel.setStyle("-fx-text-fill: white;");
        headerLabel.setFont(blackops);

        headerPLabel = new Label("Play");
        headerPLabel.setLayoutX(185);
        headerPLabel.setLayoutY(5);
        headerPLabel.setStyle("-fx-text-fill: #228B22;");
        headerPLabel.setFont(blackops);

        pageIndicatorLabel = new Label("VIDEO MODE");
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

        root.getChildren().addAll(headerLabel, headerPLabel, pageIndicatorLabel);
        headerLabel.toFront();
        headerPLabel.toFront();
        pageIndicatorLabel.toFront();
    }

    // BUILD NAVIGATION
    private void buildNavigation() {
        musicModeBtn = new Button("Music", musicIcon);
        musicModeBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #228B22;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;"
        );
        musicModeBtn.setLayoutX(400);
        musicModeBtn.setLayoutY(20);
        musicModeBtn.setMinHeight(28);
        musicModeBtn.setMinWidth(80);

        videoModeBtn = new Button("Videos", videoIcon);
        videoModeBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #228B22;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;"
        );
        videoModeBtn.setLayoutX(490);
        videoModeBtn.setLayoutY(20);
        videoModeBtn.setMinHeight(28);
        videoModeBtn.setMinWidth(80);

        exitBtn = new Button("Exit", exitIcon);
        exitBtn.setStyle(
                "-fx-text-fill: white;" +
                "-fx-background-color: #8B0000;" +
                "-fx-border-color: transparent;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;"
        );
        exitBtn.setLayoutX(580);
        exitBtn.setLayoutY(20);
        exitBtn.setMinHeight(28);
        exitBtn.setMinWidth(80);

        // Music → close this video window, bring the music window back
        musicModeBtn.setOnAction(e -> {
            releaseVlcj();
            stage.close();  // triggers setOnHidden on the music stage → music window reappears
        });

        // Videos → already on videos
        videoModeBtn.setOnAction(e -> {
            pageIndicatorLabel.setText("VIDEO MODE");
            statusLabel.setText("You are on the Video page");
        });

        // Exit → quit
        exitBtn.setOnAction(e -> {
            releaseVlcj();
            Platform.exit();
        });

        root.getChildren().addAll(musicModeBtn, videoModeBtn, exitBtn);
        musicModeBtn.toFront();
        videoModeBtn.toFront();
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

    // FILE CHOOSER — accepts every video format
    private void addVideoFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Videos");

        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "All Video Files",
                        "*.mp4", "*.mkv", "*.avi", "*.mov", "*.wmv", "*.flv",
                        "*.m4v", "*.webm", "*.mpg", "*.mpeg", "*.ts", "*.m2ts",
                        "*.3gp", "*.ogv", "*.rm", "*.rmvb", "*.vob", "*.divx"
                ),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        List<File> files = chooser.showOpenMultipleDialog(scene.getWindow());
        if (files == null) return;

        int accepted = 0;
        int rejected = 0;

        for (File f : files) {
            if (f != null && f.isFile() && !playlist.contains(f)) {
                if (isVideoFile(f)) {
                    playlist.add(f);
                    accepted++;
                } else {
                    rejected++;
                }
            }
        }

        if (currentIndex == -1 && !playlist.isEmpty()) {
            currentIndex = 0;
            playlistView.getSelectionModel().select(0);
            loadVideo(playlist.get(0));
        }

        if (rejected > 0) {
            statusLabel.setText("Added " + accepted + " video(s) — " +
                    rejected + " rejected");
        } else {
            statusLabel.setText("Added " + accepted + " video(s)");
        }
        playlistView.refresh();
    }

    private void addVideoFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add Video Folder");
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
            } else if (isVideoFile(f) && !playlist.contains(f)) {
                playlist.add(f);
                added++;
            }
        }
        statusLabel.setText("Added " + added + " video(s) from folder");
    }

    private boolean isVideoFile(File f) {
        String n = f.getName().toLowerCase();
        String[] exts = {
                ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".m4v",
                ".webm", ".mpg", ".mpeg", ".ts", ".m2ts", ".3gp", ".ogv",
                ".rm", ".rmvb", ".vob", ".divx"
        };
        for (String e : exts) {
            if (n.endsWith(e)) return true;
        }
        return false;
    }

    // REMOVE VIDEO
    private void removeVideo(File file) {
        if (file == null) return;
        int index = playlist.indexOf(file);
        if (index < 0) return;

        if (vlcPlayer != null && index == currentIndex) {
            vlcPlayer.controls().stop();
        }

        playlist.remove(index);

        if (playlist.isEmpty()) {
            currentIndex = -1;
            titleLabel.setText("No Video Selected");
            durationSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
            updatePlaybackButtons(false);
            mediaView.setVisible(false);
            placeholderImage.setVisible(true);
            playlistView.refresh();
            return;
        }

        if (index >= playlist.size()) {
            index = playlist.size() - 1;
        }

        currentIndex = index;
        playlistView.getSelectionModel().select(index);
        loadVideo(playlist.get(index));
        playlistView.refresh();
    }

    // LOAD VIDEO
    private void loadVideo(File file) {
        if (file == null) return;
        currentFile = file;

        if (vlcPlayer == null) {
            statusLabel.setText("VLC engine not initialised");
            return;
        }

        try {
            titleLabel.setText(file.getName());
            mediaView.setVisible(true);
            placeholderImage.setVisible(false);
            mediaView.toFront();

            vlcPlayer.media().play(file.getAbsolutePath());
            vlcPlayer.audio().setVolume((int) (lastVolume * 100));

        } catch (Exception e) {
            statusLabel.setText("Cannot play: " + e.getMessage());
            System.err.println("Error loading video: " + e.getMessage());
        }
    }

    // PLAYBACK CONTROLS
    private void playVideo() {
        if (vlcPlayer != null) {
            if (vlcPlayer.status().isPlaying()) return;
            if (vlcPlayer.status().isPlayable()) {
                vlcPlayer.controls().play();
            } else if (currentFile != null) {
                vlcPlayer.media().play(currentFile.getAbsolutePath());
            }
        } else if (!playlist.isEmpty()) {
            loadVideo(playlist.get(0));
        }
    }

    private void pauseVideo() {
        if (vlcPlayer != null) {
            vlcPlayer.controls().pause();
        }
    }

    private void updatePlaybackButtons(boolean isPlaying) {
        if (isPlaying) {
            playBtn.setGraphic(pauseIcon);
        } else {
            playBtn.setGraphic(playIcon);
        }
    }

    private void toggleMute() {
        if (vlcPlayer == null) return;
        muted = !muted;
        if (muted) {
            vlcPlayer.audio().setMute(true);
            muteBtn.setGraphic(muteIcon);
            statusLabel.setText("Muted");
        } else {
            vlcPlayer.audio().setMute(false);
            muteBtn.setGraphic(muteIcon);
            statusLabel.setText("Unmuted");
        }
    }

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

    // FULL SCREEN / MINIMIZE
    private void enterFullScreen() {
        if (isFullScreen) return;
        isFullScreen = true;

        listViewPane.setVisible(false);
        headerLabel.setVisible(false);
        headerPLabel.setVisible(false);
        pageIndicatorLabel.setVisible(false);
        musicModeBtn.setVisible(false);
        videoModeBtn.setVisible(false);
        exitBtn.setVisible(false);

        videoPane.setLayoutX(0);
        videoPane.setLayoutY(0);
        videoPane.setPrefSize(WIDTH, HEIGHT);

        titleLabel.setVisible(false);

        double bottomStrip = 90;
        videoContainer.setPrefSize(WIDTH, HEIGHT - bottomStrip);
        videoContainer.setLayoutX(0);
        videoContainer.setLayoutY(0);
        if (mediaView != null) {
            mediaView.setFitWidth(WIDTH);
            mediaView.setFitHeight(HEIGHT - bottomStrip);
        }

        double rowCenterY = HEIGHT - 35;
        double centerX = WIDTH / 2;

        muteBtn.setLayoutX(centerX - 220 - 16);
        muteBtn.setLayoutY(rowCenterY - 16);

        previousBtn.setLayoutX(centerX - 150 - 19);
        previousBtn.setLayoutY(rowCenterY - 19);

        playBtn.setLayoutX(centerX - 26);
        playBtn.setLayoutY(rowCenterY - 26);

        nextBtn.setLayoutX(centerX + 150 - 19);
        nextBtn.setLayoutY(rowCenterY - 19);

        fullScreenBtn.setLayoutX(centerX + 220 - 16);
        fullScreenBtn.setLayoutY(rowCenterY - 16);

        minimizeBtn.setLayoutX(centerX + 280 - 16);
        minimizeBtn.setLayoutY(rowCenterY - 16);

        volumeBtn.setLayoutX(WIDTH - 60 - 16);
        volumeBtn.setLayoutY(rowCenterY - 16);

        shrinkButton(muteBtn, 32);
        shrinkButton(previousBtn, 38);
        shrinkButton(playBtn, 52);
        shrinkButton(nextBtn, 38);
        shrinkButton(fullScreenBtn, 32);
        shrinkButton(minimizeBtn, 32);
        shrinkButton(volumeBtn, 32);

        durationSlider.setLayoutX(60);
        durationSlider.setLayoutY(HEIGHT - 85);
        durationSlider.setPrefWidth(WIDTH - 140);

        currentTimeLabel.setLayoutX(60);
        currentTimeLabel.setLayoutY(HEIGHT - 105);

        totalTimeLabel.setLayoutX(WIDTH - 90);
        totalTimeLabel.setLayoutY(HEIGHT - 105);

        volumePopup.setLayoutX(WIDTH - 320);
        volumePopup.setLayoutY(HEIGHT - 135);

        statusLabel.setLayoutX(60);
        statusLabel.setLayoutY(HEIGHT - 20);
        statusLabel.setPrefWidth(WIDTH - 120);

        muteBtn.toFront();
        previousBtn.toFront();
        playBtn.toFront();
        nextBtn.toFront();
        fullScreenBtn.toFront();
        minimizeBtn.toFront();
        volumeBtn.toFront();
        durationSlider.toFront();
        currentTimeLabel.toFront();
        totalTimeLabel.toFront();
        volumePopup.toFront();
        statusLabel.toFront();

        statusLabel.setText("Full Screen mode");
    }

    private void exitFullScreen() {
        if (!isFullScreen) return;
        isFullScreen = false;

        listViewPane.setVisible(true);
        headerLabel.setVisible(true);
        headerPLabel.setVisible(true);
        pageIndicatorLabel.setVisible(true);
        musicModeBtn.setVisible(true);
        videoModeBtn.setVisible(true);
        exitBtn.setVisible(true);

        videoPane.setLayoutX(390);
        videoPane.setLayoutY(80);
        videoPane.setPrefSize(870, 600);

        titleLabel.setVisible(true);
        titleLabel.setLayoutX(60);
        titleLabel.setLayoutY(28);
        titleLabel.setPrefWidth(750);
        titleLabel.setMaxWidth(750);

        videoContainer.setPrefSize(830, 400);
        videoContainer.setLayoutX(20);
        videoContainer.setLayoutY(60);
        if (mediaView != null) {
            mediaView.setFitWidth(830);
            mediaView.setFitHeight(400);
        }

        double rowCenterY = 546;
        double centerX = 435;

        muteBtn.setLayoutX(centerX - 200 - 21);
        muteBtn.setLayoutY(rowCenterY - 21);

        previousBtn.setLayoutX(centerX - 130 - 24);
        previousBtn.setLayoutY(rowCenterY - 24);

        playBtn.setLayoutX(centerX - 34);
        playBtn.setLayoutY(rowCenterY - 34);

        nextBtn.setLayoutX(centerX + 130 - 24);
        nextBtn.setLayoutY(rowCenterY - 24);

        fullScreenBtn.setLayoutX(centerX + 200 - 21);
        fullScreenBtn.setLayoutY(rowCenterY - 21);

        minimizeBtn.setLayoutX(centerX + 260 - 21);
        minimizeBtn.setLayoutY(rowCenterY - 21);

        volumeBtn.setLayoutX(830 - 21);
        volumeBtn.setLayoutY(rowCenterY - 21);

        restoreButton(muteBtn, 42);
        restoreButton(previousBtn, 48);
        restoreButton(playBtn, 68);
        restoreButton(nextBtn, 48);
        restoreButton(fullScreenBtn, 42);
        restoreButton(minimizeBtn, 42);
        restoreButton(volumeBtn, 42);

        durationSlider.setLayoutX(20);
        durationSlider.setLayoutY(478);
        durationSlider.setPrefWidth(830);

        currentTimeLabel.setLayoutX(20);
        currentTimeLabel.setLayoutY(496);

        totalTimeLabel.setLayoutX(818);
        totalTimeLabel.setLayoutY(496);

        volumePopup.setLayoutX(620);
        volumePopup.setLayoutY(440);

        statusLabel.setLayoutX(60);
        statusLabel.setLayoutY(580);
        statusLabel.setPrefWidth(750);

        statusLabel.setText("Default size");
    }

    private void shrinkButton(Button btn, double size) {
        btn.setPrefSize(size, size);
        btn.setMinSize(size, size);
        btn.setMaxSize(size, size);
        String bg = (btn == playBtn) ? "#2a2a2e" : "#2a2a2e";
        btn.setStyle(
                "-fx-background-color: " + bg + ";" +
                "-fx-background-radius: " + (size / 2) + ";" +
                "-fx-border-color: rgba(255, 255, 255, 0.2);" +
                "-fx-border-radius: " + (size / 2) + ";" +
                "-fx-border-width: " + (btn == playBtn ? 2 : 1) + ";" +
                "-fx-cursor: hand;"
        );
    }

    private void restoreButton(Button btn, double size) {
        btn.setPrefSize(size, size);
        btn.setMinSize(size, size);
        btn.setMaxSize(size, size);
        String bg = (btn == playBtn) ? "#2a2a2e" : "#2a2a2e";
        btn.setStyle(
                "-fx-background-color: " + bg + ";" +
                "-fx-background-radius: " + (size / 2) + ";" +
                "-fx-border-color: rgba(255, 255, 255, 0.2);" +
                "-fx-border-radius: " + (size / 2) + ";" +
                "-fx-border-width: " + (btn == playBtn ? 2 : 1) + ";" +
                "-fx-cursor: hand;"
        );
    }

    // NEXT / PREVIOUS
    private void nextVideo() {
        if (playlist.isEmpty()) return;
        currentIndex++;
        if (currentIndex >= playlist.size()) {
            currentIndex = 0;
        }
        playlistView.getSelectionModel().select(currentIndex);
        loadVideo(playlist.get(currentIndex));
    }

    private void previousVideo() {
        if (playlist.isEmpty()) return;
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = playlist.size() - 1;
        }
        playlistView.getSelectionModel().select(currentIndex);
        loadVideo(playlist.get(currentIndex));
    }

    // FORMAT TIME
    private String formatMillis(long millis) {
        if (millis <= 0) return "00:00";
        int total = (int) (millis / 1000);
        int minutes = total / 60;
        int seconds = total % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

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
                    if (vlcPlayer == null) return;
                    if (vlcPlayer.status().isPlaying()) {
                        pauseVideo();
                    } else {
                        playVideo();
                    }
                    event.consume();
                }
                case N -> { nextVideo(); event.consume(); }
                case P -> { previousVideo(); event.consume(); }
                case M -> { toggleMute(); event.consume(); }
                case F -> { enterFullScreen(); event.consume(); }
                case ESCAPE -> {
                    if (isFullScreen) {
                        exitFullScreen();
                    }
                    event.consume();
                }
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
                    if (vlcPlayer != null) {
                        long t = vlcPlayer.status().time();
                        vlcPlayer.controls().setTime(Math.max(0, t - 5000));
                    }
                    event.consume();
                }
                case RIGHT -> {
                    if (vlcPlayer != null) {
                        long t = vlcPlayer.status().time();
                        vlcPlayer.controls().setTime(t + 5000);
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
    public void stop() {
        releaseVlcj();
    }
}