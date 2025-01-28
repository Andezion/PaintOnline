package com.example.littlepaint;

import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

import javafx.scene.canvas.Canvas;

public class WebSocketClientApp
{
    private static WebSocketClient webSocketClient;
    private static Canvas canvas;

    public static void setCanvas(Canvas canvass)
    {
        canvas = canvass;
    }

    public static void connect(String serverUri)
    {
        try
        {
            URI uri = new URI(serverUri);
            webSocketClient = new WebSocketClient(uri)
            {
                @Override
                public void onOpen(ServerHandshake handshake)
                {
                    System.out.println("Connected to server");
                }

                @Override
                public void onMessage(String message)
                {
                    System.out.println("Message received from server: " + message);
                    if (canvas != null)
                    {
                        Platform.runLater(() ->
                        {
                            ImageDisplayClient.handleServerMessage(message, canvas); // Передаём Canvas
                        });
                    }
                    else
                    {
                        System.out.println("Canvas is not set.");
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote)
                {
                    System.out.println("Connection closed: " + reason);
                }

                @Override
                public void onError(Exception ex)
                {
                    System.out.println("Error: " + ex.getMessage());
                }
            };

            webSocketClient.connect();
        }
        catch (Exception e)
        {
            System.out.println("Failed to connect to WebSocket server: " + e.getMessage());
        }
    }

    public static void sendMessage(String message)
    {
        if (webSocketClient != null && webSocketClient.isOpen())
        {
            webSocketClient.send(message);
        }
        else
        {
            System.out.println("WebSocket is not connected");
        }
    }

    public static void disconnect()
    {
        if (webSocketClient != null)
        {
            webSocketClient.close();
        }
    }
}
