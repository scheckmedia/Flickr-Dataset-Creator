package de.scheckmedia.fdc;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        System.out.println(getClass().toString());
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("main.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("FDC - Flickr Dataset Creator");
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getClassLoader().getResource("style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        loader.<Controller>getController().resize();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
