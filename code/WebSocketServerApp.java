package com.example.littlepaint;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WebSocketServerApp extends WebSocketServer
{
    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());

    public WebSocketServerApp(int port)
    {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        clients.add(conn);
        System.out.println("New connection from: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        clients.remove(conn);
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        System.out.println("Message received from client " + conn.getRemoteSocketAddress() + ": " + message);

        synchronized (clients)
        {
            for (WebSocket client : clients)
            {
                if (client != conn)
                {
                    client.send(message);
                }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        System.out.println("An error occurred: " + ex.getMessage());
    }

    @Override
    public void onStart()
    {
        System.out.println("WebSocket server started successfully");
    }

    public static void main(String[] args)
    {
        int port = 8080;
        WebSocketServerApp server = new WebSocketServerApp(port);

        server.start();
        System.out.println("WebSocket server is running on port: " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            System.out.println("Shutting down server...");
            try
            {
                server.stop();
            }
            catch (Exception e)
            {
                System.out.println("Error shutting down server: " + e.getMessage());
            }
        }));
    }
}
