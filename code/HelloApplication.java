package com.example.littlepaint;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class HelloApplication extends Application
{
    private static final String SERVER_URI = "ws://localhost:8080";

    @Override
    public void start(Stage stage) throws Exception
    {
        WebSocketClientApp.connect(SERVER_URI);

        stage.setScene(new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getResource("paint.fxml")))));
        stage.setTitle("Paint");
        stage.show();
    }

    @Override
    public void stop()
    {
        WebSocketClientApp.disconnect();
        System.out.println("App is closed. WebSocket is closed too!");
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
