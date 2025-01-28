# PaintOnline
Реализация многопользовательского синхронного рисования 

## Пошёл контент
* [General info](#general-info)
* [Demonstration](#demonstration)
* [Technologies](#technologies)
* [Features](#features)
* [Inspiration](#inspiration)
* [Setup](#setup)
---

## General info
Как финальный проект на моей специальности нам задали написать синхронный многопользовательский холст для рисования, с возможностью рисовать кистью разных размеров и цветов, использовать ластик, и наносить сложные формы, к примеру круги или прямоугольники. Так же ко всему этому должна была быть подвязана база данных, указаний к которой не было.

---
## Demonstration
Скоро будет!

---
## Technologies
Проект настрадали с помощью:
* JavaFX
* Spring
* Apache Maven
* MySQL
* WebSocket
---
## Features
Начнём с сервера, изначально мы создаём хранилище для наших уникальных пользователей по их WebSocket. Далее мы создаём пару методов для понимания подключились-ли мы к серверу и отключились-ли от сервера.
Функция onMessage() рассылает всем пользователям то "сообщение" которые нарисовал другой пользователь. Это одна из самых важных функций во всем проекте. 
```
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
```
Далее идут функции для понимая правильно-ли запустился сам сервер или нет. Если же нет - то мы получаем описание нашей ошибки. В main() мы просто запускаем сервер с определённым нашим портом.

Переходя к клиенту, у нас есть метод подключающий к серверу. Внутри него, после подключения, мы проверям подключены или отключены и получаем сообщение от других клиентов:
```
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
```
Далее мы имеем функцию отключающую нас от сервера и функцию рассылающую сообщения всем клиентам.
В предыдущем отрывке кода мы задействовали метод из класса ImageDisplayClient, так что плавно перейдём к нему.
На самом деле это абсолютно не нужный класс, лишь с одним методом, который нужно было засунуть в клиента и упростить себе жизнь, но уже как есть, итак функция которая обрабатывает сообщение полученное от сервера и отображающая это изображение на холсте нашего пользователя. Если у нас что-то не пошло или не получилось - то есть обработка ошибок. Кстати обработка ошибок тут написана везде, так что снихуя программа не закроется:
```
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
```
Изначально мы декодируем в удобный нам формат, после чего вгружаем всё это дело в буферное изображение, а если на этом этапе у нас до сих пор ничего не вылетело - помещаем это изображение в нужное на холсте пользователя место. 

Далее наш класс Paint.java, тут я собрал всю логику рисовки на холсте. Тут у нас есть наш основной canvas для пользователя, на котором происходят все действия. Так же для хранения наших форм и рисунков я использую два массива. Дефолтный иструмент у нас это кисть. 

Иницилизация происходит в тот момент когда клиент включает приложение, мы открываем наш canvas нанося на него все нужные нам элементы. После создаём GraphicsContext для отрисовки, и в зависимости от инструмента заполняем наш холст. Для этого у нас есть парочка вспомогательных функций, о которых дальше. Как только мы отпускаем мыш, то есть перестаём рисовать - то форма сохраняется в один из двух массивов. 
Для кистей мы используем handleBrushTool, которая просто заполняет нужным цветом определённую форму в конкретных координатах, в конце тоже добавляет в массив. Потом 3 функции для отслеживания мыши, где мы её зажали, куда мы её тащили и где мы её отпустили. 

Функция для сохранения изображения работает таким образом, что мы кодируем наше изображение, проверяя всё ли правильно закодировалось, и файлом отправляем это в другую функцию, а именно saveImageToDatabase(), которая уже непосредственно взаимодействует с базой и выполняя SQL запрос - записывает изображение в базу:
```
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
```
Функция для отрытия файла из базы работает по такому принципу - сначала выбираем нужный индекс, проверяем существует-ли изображение с таким индексом, и потом декодируем его обратно, помещая на наш холст. Так же это изображние будет открыто и у всех пользователей, что в целом удобно:
```
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
```
В ней всё предельно просто, несмотря на то, что выглядит она так уебищно. Мы входим в массив форм, проверяем каждую форму, и в зависимости от типа этой формы - отображаем её на холсте, если же мы открываем изобраение, то используем функции дальше. Берём нужный номер фотографии - getImageFromDatabase - возвращаем декодированное изображение из базы данныхю

Самая важная функция - отправляющая наш холст на сервер, то есть наше сдекодированное изображение мы посылаем на сервер, который потом рассылает его всем пользователям, которые декодируют изображение, и видят его на своём холсте!
```
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

```
Тут всё как обычно, кодируем, проверяем правильность цветов, и через стрим отправляем на сервер в формате Base64, потому что это удобно.
Далее у нас есть класс форма, и поскольку форм у нас несколько - то и конструкторов класса у нас несколько, к сожалению даунская Джава это позволяет. 

Что-ж, HelloApplication.java
Поскольку разработчик бедный и тупой, то всё это находится на локалхосте, который мы пихаем клиенту для подключения. В старт у нас всего-ли открытие нашего canvas, который у нас постоянно обновляется. 

---
## Inspiration
- Тройбан за семестр
---
## Setup

Никак, еле запустилось у меня, у вас не получится!

