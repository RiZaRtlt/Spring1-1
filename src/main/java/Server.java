import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

public class Server implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedOutputStream out;
    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");


    public Server(){
        this.socket = null;
        this.in = null;
        this.out = null;
    }

    public Server(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        this.out = new BufferedOutputStream(this.socket.getOutputStream());
    }

    public void start() {

        final var threadPool = Executors.newFixedThreadPool(64);

        try (final var serverSocket = new ServerSocket(9999)) {
            while (true) {
                    final var firstsocket = serverSocket.accept();
                    threadPool.submit(new Server(firstsocket));
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = this.in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                return;
            }

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                this.out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                this.out.flush();
            }

            final var filePath = Path.of(".", "src/main/resources", path);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                this.out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                this.out.write(content);
                this.out.flush();
            }

            final var length = Files.size(filePath);
            this.out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, this.out);
            this.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
