module media {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.media;

    // VLCJ + VLCJ JavaFX support
    requires uk.co.caprica.vlcj;
    requires uk.co.caprica.vlcj.javafx;
    requires com.sun.jna;

    requires java.desktop;

    opens media to javafx.fxml;
    exports media;
}