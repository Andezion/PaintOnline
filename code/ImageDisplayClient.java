package com.example.littlepaint;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.canvas.Canvas;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

public class ImageDisplayClient
{
    public static void handleServerMessage(String base64Message, Canvas canvas)
    {
        try
        {
            byte[] imageBytes = Base64.getDecoder().decode(base64Message);
            java.awt.image.BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (bufferedImage != null)
            {
                javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.drawImage(fxImage, 0, 0, canvas.getWidth(), canvas.getHeight());
            }
            else
            {
                System.out.println("Failed to decode image from Base64.");
            }
        }
        catch (Exception e)
        {
            System.out.println("Error handling server message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
