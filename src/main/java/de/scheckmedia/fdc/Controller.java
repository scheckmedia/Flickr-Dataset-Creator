package de.scheckmedia.fdc;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Controller implements FlickrApiEvents {
    final static String apiKey = "2f195dc58f4e8579c5fff3c7797a2240";
    final int thumbsize = 150;
    final double rightPaneSize = 280;

    @FXML
    ScrollPane spScroll;

    @FXML
    ScrollPane spRightScroll;

    @FXML
    VBox vpRight;

    @FXML
    Pane paneGrid;

    @FXML
    TextField tbQuery;

    @FXML
    TextField tbCategory;

    @FXML
    TextField tbOutputPath;

    @FXML
    ComboBox<AbstractMap.SimpleEntry<String, String>> cbQuality;

    @FXML
    ComboBox<String> cbItemsPerPage;

    @FXML
    ComboBox<String> cbSort;

    @FXML
    CheckBox chCC;

    @FXML
    CheckBox chTags;

    @FXML
    Label lblSelectedItems;

    @FXML
    Label lblStatus;

    @FXML
    Label lblProgress;

    @FXML
    ListView<String> lvQueued;

    @FXML
    Button btnOpen;

    @FXML
    Button btnStartDownloading;

    @FXML
    Button btnSearch;

    @FXML
    ProgressBar pbProgress;

    @FXML
    SplitPane spMain;

    ProgressIndicator piSearch = new ProgressIndicator();

    private HashMap<String, ArrayList<FlickImage>> downloadList = new HashMap<>();
    private ObservableList<FlickImage> selectedImages = FXCollections.observableArrayList( new ArrayList<>() );
    private FlickrApi api;
    private Gson gson = new Gson();
    private AtomicInteger imagesDownloaded = new AtomicInteger(0);


    @FXML
    public void initialize() {
        api = new FlickrApi(apiKey);
        setup();
    }

    private void setup() {
        // url_sq, url_t, url_s, url_q, url_m, url_n, url_z, url_c, url_l, url_o
        ObservableList<AbstractMap.SimpleEntry<String, String>> quality = FXCollections.observableArrayList(
                new AbstractMap.SimpleEntry<>("small square 75x75","url_s"),
                new AbstractMap.SimpleEntry<>("large square 150x150","url_q"),
                new AbstractMap.SimpleEntry<>("thumbnail, 100 on longest side","url_t"),
                new AbstractMap.SimpleEntry<>("small, 240 on longest side","url_m"),
                new AbstractMap.SimpleEntry<>("small, 320 on longest side","url_n"),
                new AbstractMap.SimpleEntry<>("medium 640, 640 on longest side","url_z"),
                new AbstractMap.SimpleEntry<>("medium 800, 800 on longest side","url_c"),
                new AbstractMap.SimpleEntry<>("large, 1024 on longest side","url_b"),
                new AbstractMap.SimpleEntry<>("large 1600, 1600 on longest side","url_h"),
                new AbstractMap.SimpleEntry<>("large 2048, 2048 on longest side","url_k"),
                new AbstractMap.SimpleEntry<>("original image","url_o")
        );

        ObservableList<String> sort = FXCollections.observableArrayList(
          "date-posted-desc",
                "date-posted-asc",
                "date-taken-desc",
                "date-taken-asc",
                "interestingness-desc",
                "interestingness-asc",
                "relevance"
        );

        ObservableList<String> perPage = FXCollections.observableArrayList();
        for(int i = 50; i <= 500; i += 50)
            perPage.add(Integer.toString(i));

        selectedImages.addListener((ListChangeListener<FlickImage>) c -> {
            String msg = String.format("%d selected Images", selectedImages.size());
            if(selectedImages.size() == 0)
                msg = "";

            lblStatus.setText(msg);
            lblStatus.toFront();
        });


        cbQuality.setItems(quality);
        cbSort.setItems(sort);
        cbItemsPerPage.setItems(perPage);

        cbQuality.getSelectionModel().select(3);
        cbSort.getSelectionModel().select(6);
        cbItemsPerPage.getSelectionModel().selectLast();

        chCC.setSelected(true);

        pbProgress.prefWidthProperty().bind(spScroll.widthProperty().subtract(10));
        lblStatus.prefWidthProperty().bind(spScroll.widthProperty().subtract(10));
        paneGrid.prefWidthProperty().bind(spScroll.widthProperty());

        vpRight.prefWidthProperty().bind(spRightScroll.widthProperty());

        lvQueued.getItems().addListener((ListChangeListener<String>) c -> {
            if(lvQueued.getItems().size() == 0)
                btnStartDownloading.setDisable(false);
            else
                btnStartDownloading.setDisable(false);
        });

        lvQueued.setOnMouseClicked((evt) -> {
            if(evt.getClickCount() == 2)
                lvQueued.getItems().remove(lvQueued.getSelectionModel().getSelectedItem());
        });

        tbQuery.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue)
                tbQuery.setText("");
        });


        lblProgress.visibleProperty().bind(pbProgress.visibleProperty());
        pbProgress.setVisible(false);
    }

    @FXML
    public void resize() {
        spScroll.widthProperty().addListener((observable, oldValue, newValue) -> {
            final double delta = newValue.doubleValue() - oldValue.doubleValue();
            rearrangeGrid(delta, 0);
            spMain.setDividerPositions(1 - rightPaneSize / spMain.getWidth());
        });

        spScroll.heightProperty().addListener((observable, oldValue, newValue) -> {
            final double delta = newValue.doubleValue() - oldValue.doubleValue();
            rearrangeGrid(0, delta);
        });
    }

    @FXML
    public void setDestinationFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File selectedDirectory =
                directoryChooser.showDialog(spScroll.getScene().getWindow());

        if(selectedDirectory != null){
            tbOutputPath.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void startDownloading() {
        try {
            if(lvQueued.getItems().isEmpty())
                throw new Exception("Category list should not be empty");

            if(new File(tbOutputPath.getText()).exists() == false)
                throw new Exception("Output folder should not be empty");

            // calc total
            int total = 0;
            for(String key : downloadList.keySet()) {
                total += downloadList.get(key).size();
            }

            pbProgress.setVisible(true);
            for(String key : downloadList.keySet()) {
                download(key, downloadList.get(key), total);
            }

            downloadList.clear();

        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Error while start downloading");
            a.setContentText(ex.getMessage());
            a.showAndWait();
        }
    }

    private void download(String category, ArrayList<FlickImage> images, int total) throws IOException {
        for(FlickImage img : images) {
            String filename = String.format("%s_%s.%s", category.replace(" ", "-"), img.getId(), img.getExtension());
            String dst = Paths.get(tbOutputPath.getText(), filename).toAbsolutePath().toString();
            sendRequestAndSave(img.getUrl(), dst, total);
        }
    }

    private void sendRequestAndSave(String src, String dst, int total) throws IOException {
        new Thread(() -> {
            System.out.println(String.format("start downloading of file %s to %s", src, dst));

            try(CloseableHttpClient client = HttpClientBuilder.create().build()) {
                HttpGet request = new HttpGet(src);
                HttpResponse result = client.execute(request);
                HttpEntity entity = result.getEntity();
                if(entity == null)
                    return;

                try (BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(new File(dst))
                )) {
                    try(BufferedInputStream bis = new BufferedInputStream(entity.getContent())) {
                        int inByte;
                        while((inByte = bis.read()) != -1) bos.write(inByte);


                        Platform.runLater(() -> {
                            int cur = imagesDownloaded.incrementAndGet();

                            double progress = (double)cur / total;
                            pbProgress.progressProperty().setValue(progress);

                            lblProgress.setText(String.format("%d / %d", cur, total));

                            if (cur == total) {
                                downloadsDone();
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void downloadsDone() {
        pbProgress.setVisible(false);
        paneGrid.prefHeightProperty().setValue(spScroll.getPrefHeight());
        lvQueued.getItems().clear();
        imagesDownloaded.set(0);
    }

    @FXML
    public void search() {
        try {
            if (tbQuery.getText().isEmpty())
                throw new Exception("Query should not be empty!");

            selectedImages.clear();

            paneGrid.setPrefHeight(spScroll.getHeight());

            this.api.search(
                    tbQuery.getText(),
                    chTags.isSelected(),
                    chCC.isSelected(),
                    cbSort.getSelectionModel().getSelectedItem(),
                    cbQuality.getSelectionModel().getSelectedItem().getValue(),
                    1,
                    Integer.parseInt(cbItemsPerPage.getSelectionModel().getSelectedItem()),
                    this
            );

        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Error while start searching");
            a.setContentText(ex.getMessage());
            a.showAndWait();
        }
    }

    @Override
    public void onRequestStart() {
        System.out.println("start request");
        Platform.runLater(() -> {
            VBox box = new VBox();
            box.setAlignment(Pos.CENTER);
            box.setFillWidth(true);
            box.prefWidthProperty().bind(paneGrid.widthProperty());
            box.prefHeightProperty().bind(paneGrid.heightProperty());
            box.getChildren().add(piSearch);
            paneGrid.getChildren().clear();
            paneGrid.getChildren().add(box);
        });
    }

    @Override
    public void onRequestEnd(Object data) {
        System.out.println("end request");
        Platform.runLater( () -> {
            paneGrid.getChildren().clear();
            parseSearchRequest((String)data);
        });
    }

    @Override
    public void onError(Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Error while sending HTTP Request");
        a.setContentText(ex.getMessage());
        a.showAndWait();
    }

    private void parseSearchRequest(String json) {
        LinkedTreeMap data = gson.fromJson(json, LinkedTreeMap.class);
        LinkedTreeMap photosSection = (LinkedTreeMap)data.get("photos");
        ArrayList<LinkedTreeMap> photos = (ArrayList)photosSection.get("photo");

        int margin = 10;
        double tw = thumbsize + margin;
        double num_cols = Math.floor(spScroll.getWidth() / (tw));
        int col = 0;
        int row = 0;

        for(LinkedTreeMap photo : photos) {
            boolean hasUrl = photo.containsKey(cbQuality.getSelectionModel().getSelectedItem().getValue());
            boolean hasThumbnail = photo.containsKey("url_q");

            if(hasUrl && hasThumbnail) {
                final FlickImage img = new FlickImage(
                        (String)photo.get("title"),
                        (String)photo.get("id"),
                        (String)photo.get("url_q"),
                        (String)photo.get(cbQuality.getSelectionModel().getSelectedItem().getValue()),
                        paneGrid
                );
                img.setCallback(new FlickeImageEvent() {
                    @Override
                    public void onSelect(FlickImage img) {
                        selectedImages.add(img);
                    }

                    @Override
                    public void onDeselect(FlickImage img) {
                        selectedImages.remove(img);
                    }

                    @Override
                    public void onDoubleClick(FlickImage img) {

                    }
                });

                img.setX(margin + tw * col);
                img.setY(margin + tw * row);

                col++;
                if(col >= num_cols) {
                    row++;
                    col = 0;
                }
            }
        }

        paneGrid.prefHeightProperty().setValue(row * tw);
    }

    private void rearrangeGrid(double dx, double dy) {
        int margin = 10;
        double tw = thumbsize + margin;
        double num_cols = Math.floor(spScroll.getWidth() / tw);
        int col = 0;
        int row = 0;

        for(Node node : paneGrid.getChildren()) {
            if(StackPane.class.isInstance(node)) {
                //boolean inside = !spScroll.getParent().getBoundsInLocal().contains(node.getBoundsInLocal());
                double nx = (margin + tw * col) - node.getLayoutX();
                double ny = (margin + tw * row) - node.getLayoutY();

                TranslateTransition tt = new TranslateTransition(Duration.millis(1000), node);
                tt.setToX(nx);
                tt.setToY(ny);
                tt.setCycleCount(1);
                tt.setAutoReverse(true);
                tt.play();

                col++;
                if(col >= num_cols) {
                    row++;
                    col = 0;
                }

            }
        }

        paneGrid.prefHeightProperty().setValue(row * tw);
        //spScroll.setVvalue(row * tw);
    }

    public void addToQueue() {
        if(tbCategory.getText().isEmpty() || selectedImages.size() == 0)
            return;

        downloadList.put(tbCategory.getText(), new ArrayList<>(selectedImages));
        lvQueued.getItems().add(String.format("%s - %d items", tbCategory.getText(), selectedImages.size()));
        selectedImages.clear();

        for(Node node : paneGrid.getChildren()) {
            if(StackPane.class.isInstance(node)) {
                StackPane p = (StackPane)node;
                Node n = p.getChildren().get(1);
                n.getStyleClass().remove("active");
            }
        }

        tbCategory.setText("");
        paneGrid.getChildren().clear();
        spScroll.setVvalue(0);
    }

    private interface FlickeImageEvent {
        void onSelect(FlickImage img);
        void onDeselect(FlickImage img);
        void onDoubleClick(FlickImage img);
    }

    private class FlickImage {
        private Pane parent;
        private StackPane stMain = new StackPane();
        private ImageView ivImage ;
        private ProgressIndicator piLoader = new ProgressIndicator();

        private String thumbnail = "";
        private String url = "";
        private String title = "";
        private String id = "";
        private FlickeImageEvent callback;

        public FlickImage( String title, String id, String thumbnail, String url, Pane parent) {
            this.title = title;
            this.id = id;
            this.thumbnail = thumbnail;
            this.url = url;
            this.parent = parent;

            buildViews();
            loadThumbnail();
        }

        public void setCallback(FlickeImageEvent cb) {
            this.callback = cb;
        }

        public void setUnselected() {
            ivImage.getStyleClass().remove("active");
        }

        private void buildViews() {
            stMain.setPrefWidth(thumbsize);
            stMain.setPrefHeight(thumbsize);
            stMain.getChildren().add(piLoader);
            stMain.setStyle("-fx-background-color: black");

            piLoader.prefWidthProperty().bind(stMain.prefWidthProperty());
            piLoader.prefHeightProperty().bind(stMain.prefHeightProperty());

            parent.getChildren().add(stMain);
        }

        private void loadThumbnail() {
            Image img = new Image(this.thumbnail, true);

            ivImage = new ImageView(img);
            ivImage.setFitWidth(stMain.getWidth());
            ivImage.setFitHeight(stMain.getHeight());

            ivImage.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2) {
                    callback.onDoubleClick(this);
                } else if (event.getClickCount() == 1) {
                    if (ivImage.getStyleClass().contains("active")) {
                        ivImage.getStyleClass().remove("active");
                        callback.onDeselect(this);
                    } else {
                        ivImage.getStyleClass().add("active");
                        callback.onSelect(this);
                    }
                }
            });
            ivImage.getStyleClass().add("image");

            piLoader.progressProperty().bind(img.progressProperty());
            piLoader.progressProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue.floatValue() == 1.0) {
                    piLoader.setVisible(false);
                }
            });

            stMain.getChildren().add(ivImage);
        }

        private void setX(double x) {
            stMain.setLayoutX(x);
        }

        private void setY(double y) {
            stMain.setLayoutY(y);
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getId() {
            return id;
        }

        public String getExtension() {
            int i = this.url.lastIndexOf('.');
            if (i > 0) {
                return this.url.substring(i+1);
            }

            return "";
        }

        @Override
        public String toString() {
            return "FlickImage{" +
                    "thumbnail='" + thumbnail + '\'' +
                    ", url='" + url + '\'' +
                    ", title='" + title + '\'' +
                    ", id='" + id + '\'' +
                    '}';
        }
    }
}
