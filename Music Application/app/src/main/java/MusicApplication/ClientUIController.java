package MusicApplication;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.gson.Gson;
import java.net.MalformedURLException;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

public class ClientUIController {

    @FXML
    private TableView<Song> songTable;
    @FXML
    private TableColumn<Song, Integer> idColumn;
    @FXML
    private TableColumn<Song, String> songTitleColumn;
    @FXML
    private TableColumn<Song, String> artistColumn;
    @FXML
    private TableColumn<Song, Integer> playCountColumn;
    @FXML
    private TableColumn<Song, String> durationColumn; // Changed to String type
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    
    @FXML
    private Button playPauseButton;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Slider progressSlider;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label totalDurationLabel;

    @FXML
    private MediaView mediaView;

    private ObservableList<Song> songList = FXCollections.observableArrayList();
    private MediaPlayer mediaPlayer;
    private List<Song> allSongs = new ArrayList<>();
    private int currentSongIndex = -1;

    private static final String SERVER_URL = "http://localhost:3000";

    @FXML
    public void initialize() {
        // Set up columns to display song properties
        idColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getId()).asObject());
        songTitleColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTitle()));
        artistColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getArtist()));
        playCountColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getPlayCount()).asObject());
        durationColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(formatDuration(cellData.getValue().getDuration())));

        // Bind TableView with songList
        songTable.setItems(songList);
        
        songTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSong, newSong) -> {
            if (newSong != null) {
                handlePlay(null); // Call the play method when a song is selected
            }
        });
        
        fetchSongs();
        
        progressSlider.setOnMouseReleased(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(javafx.util.Duration.seconds(progressSlider.getValue()));
            }
        });
    }

    @FXML
    public void handleSearch(ActionEvent event) {
        String searchQuery = searchField.getText();
        Task<List<Song>> task = new Task<>() {
            @Override
            protected List<Song> call() {
                return searchSongsOnServer(searchQuery);
            }

            @Override
            protected void succeeded() {
                List<Song> searchResults = getValue();
                songList.clear();
                songList.addAll(searchResults);
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
            }
        };
        new Thread(task).start();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        fetchSongs();
    }

    @FXML
    public void handlePlay(ActionEvent event) {
        Song selectedSong = songTable.getSelectionModel().getSelectedItem();

        if (selectedSong != null) {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            Media media = streamSongFromServer(selectedSong);

            if (media != null) {
                mediaPlayer = new MediaPlayer(media);
                mediaView.setMediaPlayer(mediaPlayer);
                mediaPlayer.play();

                mediaPlayer.setOnReady(() -> {
                    // Update total duration
                    totalDurationLabel.setText(formatDuration((int) mediaPlayer.getMedia().getDuration().toSeconds()));
                    progressSlider.setMax(mediaPlayer.getMedia().getDuration().toSeconds());
                });

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    // Update current time
                    currentTimeLabel.setText(formatDuration((int) newTime.toSeconds()));
                    progressSlider.setValue(newTime.toSeconds());
                });
                
                // Update play count on the server
                Task<Void> updatePlayCountTask = new Task<>() {
                    @Override
                    protected Void call() {
                        updatePlayCountOnServer(selectedSong.getId());
                        return null;
                    }

                    @Override
                    protected void failed() {
                        getException().printStackTrace();
                    }
                };
                new Thread(updatePlayCountTask).start();
            }
        }
    }

    private String formatDuration(int duration) {
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @FXML
    public void handleNext(ActionEvent event) {
        int currentIndex = songTable.getSelectionModel().getSelectedIndex();
        if (currentIndex < songList.size() - 1) {
            songTable.getSelectionModel().select(currentIndex + 1);
            handlePlay(null);
        }
    }

    @FXML
    public void handlePrevious(ActionEvent event) {
        int currentIndex = songTable.getSelectionModel().getSelectedIndex();
        if (currentIndex > 0) {
            songTable.getSelectionModel().select(currentIndex - 1);
            handlePlay(null);
        }
    }

    @FXML
    public void handlePlayPause(ActionEvent event) {
        if (mediaPlayer != null) {
            if (mediaPlayer.getStatus() == Status.PLAYING) {
                mediaPlayer.pause();
                playPauseButton.setText("Play");
            } else if (mediaPlayer.getStatus() == Status.PAUSED) {
                mediaPlayer.play();
                playPauseButton.setText("Pause");
            }
        }
    }

    @FXML
    public void handleStop(ActionEvent event) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    @FXML
    public void handleExit(ActionEvent event) {
        Platform.exit();
    }

    private void fetchSongs() {
        Task<List<Song>> task = new Task<>() {
            @Override
            protected List<Song> call() {
                return getAllSongsFromServer();
            }

            @Override
            protected void succeeded() {
                List<Song> songs = getValue();
                songList.clear();
                songList.addAll(songs);
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
            }
        };
        new Thread(task).start();
    }

    private List<Song> getAllSongsFromServer() {
        List<Song> songs = new ArrayList<>();
        try {
            URI uri = new URI(SERVER_URL + "/songs");
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            Gson gson = new Gson();
            Song[] songArray = gson.fromJson(content.toString(), Song[].class);
            songs.addAll(Arrays.asList(songArray));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    private List<Song> searchSongsOnServer(String query) {
        List<Song> songs = new ArrayList<>();
        try {
            URI uri = new URI(SERVER_URL + "/search?q=" + query);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            Gson gson = new Gson();
            Song[] songArray = gson.fromJson(content.toString(), Song[].class);
            songs.addAll(Arrays.asList(songArray));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    private Media streamSongFromServer(Song song) {
        try {
            URI uri = new URI(SERVER_URL + "/songs/" + song.getId() + "/stream");
            URL url = uri.toURL();
            return new Media(url.toString());
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updatePlayCountOnServer(int songId) {
        try {
            URI uri = new URI(SERVER_URL + "/play/" + songId);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            System.out.println("Updated play count: " + content.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
