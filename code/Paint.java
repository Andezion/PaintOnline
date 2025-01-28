package com.example.littlepaint;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Paint
{
    @FXML
    private Canvas canvas;

    @FXML
    private ColorPicker colorPicker;

    @FXML
    private TextField brushSize;

    private final List<Shape> shapes = new ArrayList<>();
    private List<double[]> currentBrushStrokes = new ArrayList<>();

    private String currentTool = "Brush";

    private boolean isDrawingShape = false;
    private double startX, startY;



    public void initialize()
    {
        WebSocketClientApp.setCanvas(canvas);
        GraphicsContext g = canvas.getGraphicsContext2D();

        canvas.setOnMousePressed(event ->
        {
            if (currentTool.endsWith("Shape"))
            {
                onMousePressed(event);
            }
            else if (isBrushTool())
            {
                currentBrushStrokes = new ArrayList<>();
            }
        });

        canvas.setOnMouseDragged(event ->
        {
            double size = Double.parseDouble(brushSize.getText());
            double x = event.getX() - size / 2;
            double y = event.getY() - size / 2;

            if (isBrushTool())
            {
                handleBrushTool(g, size, x, y);
            }
            else if (isDrawingShape)
            {
                onMouseDragged(event);
            }
        });

        canvas.setOnMouseReleased(event ->
        {
            if (isDrawingShape)
            {
                onMouseReleased(event);
            }
            else if (isBrushTool())
            {
                shapes.add(new Shape(currentTool, new ArrayList<>(currentBrushStrokes),
                        colorPicker.getValue(), Double.parseDouble(brushSize.getText())));
                currentBrushStrokes.clear();
                sendCanvasToServer();
            }
        });
    }

    private void handleBrushTool(GraphicsContext g, double size, double x, double y)
    {
        if (!isDrawingShape)
        {
            if (currentTool.equals("EraserBrush"))
            {
                g.setFill(Color.WHITE);
                g.fillRect(x, y, size, size);
                currentBrushStrokes.add(new double[]{x, y, size});
            }
            else
            {
                g.setFill(colorPicker.getValue());
                switch (currentTool)
                {
                    case "Brush", "SquareBrush" -> g.fillRect(x, y, size, size);
                    case "CircleBrush" -> g.fillOval(x, y, size, size);
                }
                currentBrushStrokes.add(new double[]{x, y, size});
            }
        }
    }

    private void onMousePressed(MouseEvent event)
    {
        if (!currentTool.endsWith("Shape"))
        {
            return;
        }

        isDrawingShape = true;
        startX = event.getX();
        startY = event.getY();
    }

    private void onMouseDragged(MouseEvent event)
    {
        if (!isDrawingShape || !currentTool.endsWith("Shape"))
        {
            return;
        }

        GraphicsContext g = canvas.getGraphicsContext2D();
        redrawCanvas();

        double endX = event.getX();
        double endY = event.getY();
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        g.setStroke(colorPicker.getValue());
        g.setLineWidth(Double.parseDouble(brushSize.getText()));

        if (currentTool.equals("CircleShape"))
        {
            g.strokeOval(Math.min(startX, endX), Math.min(startY, endY), width, height);
        }
        else if (currentTool.equals("SquareShape"))
        {
            g.strokeRect(Math.min(startX, endX), Math.min(startY, endY), width, height);
        }
    }

    private void onMouseReleased(MouseEvent event)
    {
        if (!isDrawingShape || !currentTool.endsWith("Shape"))
        {
            return;
        }

        isDrawingShape = false;

        double endX = event.getX();
        double endY = event.getY();
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        shapes.add(new Shape(
                currentTool,
                Math.min(startX, endX),
                Math.min(startY, endY),
                width, height,
                colorPicker.getValue(),
                Double.parseDouble(brushSize.getText())
        ));

        redrawCanvas();
        sendCanvasToServer();
    }

    public void onSelectBrush()
    {
        currentTool = "Brush";
    }

    public void onSelectEraser()
    {
        currentTool = "EraserBrush";
    }

    public void onSelectSquare()
    {
        currentTool = "SquareBrush";
    }

    public void onSelectCircle()
    {
        currentTool = "CircleBrush";
    }

    public void shapeCircle()
    {
        currentTool = "CircleShape";
    }

    public void shapeSquare()
    {
        currentTool = "SquareShape";
    }

    public void onSave()
    {
        try
        {
            WritableImage snapshot = canvas.snapshot(null, null);
            BufferedImage bufferedImage = new BufferedImage(
                    (int) snapshot.getWidth(),
                    (int) snapshot.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            java.awt.Graphics2D graphics = bufferedImage.createGraphics();

            graphics.drawImage(javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot, null), 0, 0, null);
            graphics.dispose();

            File outputFile = new File("paint.png");
            ImageIO.write(bufferedImage, "png", outputFile);
            System.out.println("Image saved to " + outputFile.getAbsolutePath());

            saveImageToDatabase(outputFile);
        }
        catch (Exception e)
        {
            System.out.println("Failed to save image: " + e);
            e.printStackTrace();
        }
    }

    private void saveImageToDatabase(File imageFile)
    {
        String url = "jdbc:mysql://localhost:3306/my_project_db";
        String username = "root";
        String password = "12345";

        String sql = "INSERT INTO images (image_data) VALUES (?)";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql);
             FileInputStream fis = new FileInputStream(imageFile))
        {

            statement.setBinaryStream(1, fis, (int) imageFile.length());
            int rowsInserted = statement.executeUpdate();

            if (rowsInserted > 0)
            {
                System.out.println("Image saved to database successfully.");
            }
        }
        catch (Exception e)
        {
            System.out.println("Failed to save image to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onOpen()
    {
        try
        {
            List<Integer> imageIds = getImageIdsFromDatabase();

            if (imageIds.isEmpty())
            {
                System.out.println("No images found in the database.");
                return;
            }

            ObservableList<Integer> observableImageIds = FXCollections.observableArrayList(imageIds);
            ComboBox<Integer> comboBox = new ComboBox<>(observableImageIds);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Open Image");
            VBox dialogRoot = new VBox(comboBox);
            Scene dialogScene = new Scene(dialogRoot);
            dialogStage.setScene(dialogScene);

            comboBox.setOnAction(event ->
            {
                Integer selectedId = comboBox.getValue();
                if (selectedId != null)
                {
                    try
                    {
                        javafx.scene.image.Image image = getImageFromDatabase(selectedId);
                        if (image != null)
                        {
                            GraphicsContext gc = canvas.getGraphicsContext2D();

                            gc.drawImage(image, 0, 0);

                            shapes.clear();
                            shapes.add(new Shape("Image", 0, 0, image.getWidth(), image.getHeight(), Color.TRANSPARENT, 0, selectedId));

                            redrawCanvas();
                            sendCanvasToServer();

                            dialogStage.close();
                        }
                        else
                        {
                            System.out.println("Image with ID " + selectedId + " not found.");
                        }
                    }
                    catch (Exception e)
                    {
                        System.out.println("Error loading image: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });


            dialogStage.show();
        }
        catch (Exception e)
        {
            System.out.println("Error getting image IDs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onExit()
    {
        Platform.exit();
    }

    private void redrawCanvas()
    {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (Shape shape : shapes)
        {
            if (shape.type.equals("Brush") && shape.brushStrokes != null)
            {
                g.setFill(shape.color);
                for (double[] stroke : shape.brushStrokes)
                {
                    double x = stroke[0];
                    double y = stroke[1];
                    double size = stroke[2];
                    g.fillRect(x, y, size, size);
                }
            }
            else if (shape.type.equals("EraserBrush") && shape.brushStrokes != null)
            {
                for (double[] stroke : shape.brushStrokes)
                {
                    double x = stroke[0];
                    double y = stroke[1];
                    double size = stroke[2];
                    g.clearRect(x, y, size, size);
                }
            }
            else if (shape.type.equals("CircleShape"))
            {
                g.setStroke(shape.color);
                g.strokeOval(shape.x, shape.y, shape.width, shape.height);
            }
            else if (shape.type.equals("SquareShape"))
            {
                g.setStroke(shape.color);
                g.strokeRect(shape.x, shape.y, shape.width, shape.height);
            }
            else if (shape.type.equals("Image") && shape.imageId != null)
            {
                try
                {
                    javafx.scene.image.Image image = getImageFromDatabase(shape.imageId);
                    if (image != null)
                    {
                        g.drawImage(image, shape.x, shape.y, shape.width, shape.height);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public static javafx.scene.image.Image getImageFromDatabase(int imageId) throws Exception
    {
        String url = "jdbc:mysql://localhost:3306/my_project_db";
        String username = "root";
        String password = "12345";
        String sql = "SELECT image_data FROM images WHERE id = ?";

        try(Connection connection = DriverManager.getConnection(url, username, password);
            PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, imageId);
            try(var result = statement.executeQuery())
            {
                if(result.next())
                {
                    var blob = result.getBlob("image_data");
                    java.io.InputStream binaryStream = blob.getBinaryStream();
                    return new javafx.scene.image.Image(binaryStream);
                }
                else
                {
                    return null;
                }
            }
        }
    }

    private List<Integer> getImageIdsFromDatabase() throws Exception
    {
        String url = "jdbc:mysql://localhost:3306/my_project_db";
        String username = "root";
        String password = "12345";
        String sql = "SELECT id FROM images";
        List<Integer> ids = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery())
        {

            while (resultSet.next())
            {
                ids.add(resultSet.getInt("id"));
            }
        }
        return ids;
    }

    private boolean isBrushTool()
    {
        return currentTool.equals("Brush") || currentTool.equals("EraserBrush") ||
                currentTool.equals("SquareBrush") || currentTool.equals("CircleBrush");
    }

    private void sendCanvasToServer()
    {
        try
        {
            WritableImage snapshot = canvas.snapshot(null, null);
            int width = (int) snapshot.getWidth();
            int height = (int) snapshot.getHeight();

            BufferedImage bufferedImage = new BufferedImage(
                    width,
                    height,
                    BufferedImage.TYPE_INT_ARGB
            );

            for (int x = 0; x < width; x++)
            {
                for (int y = 0; y < height; y++)
                {
                    Color fxColor = snapshot.getPixelReader().getColor(x, y);

                    java.awt.Color awtColor = new java.awt.Color(
                            (float) fxColor.getRed(),
                            (float) fxColor.getGreen(),
                            (float) fxColor.getBlue(),
                            (float) fxColor.getOpacity()
                    );

                    bufferedImage.setRGB(x, y, awtColor.getRGB());
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", outputStream);
            String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            WebSocketClientApp.sendMessage(base64Image);
        }
        catch (Exception e)
        {
            System.out.println("Failed to send image: " + e);
            e.printStackTrace();
        }
    }

    private static class Shape
    {
        String type;
        double x, y, width, height;
        Color color;
        double lineWidth;
        List<double[]> brushStrokes = null;
        Integer imageId;

        Shape(String type, double x, double y, double width, double height, Color color, double lineWidth)
        {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.lineWidth = lineWidth;
        }

        Shape(String type, double x, double y, double width, double height, Color color, double lineWidth, Integer imageId)
        {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.lineWidth = lineWidth;
            this.imageId = imageId;
        }

        Shape(String type, List<double[]> brushStrokes, Color color, double lineWidth)
        {
            this.type = type;
            this.brushStrokes = brushStrokes;
            this.color = color;
            this.lineWidth = lineWidth;
        }
    }
}
