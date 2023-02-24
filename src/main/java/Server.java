
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Server implements Runnable {
    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");


    public Server(){
        this.socket = null;
        this.in = null;
        this.out = null;
    }

    public Server(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(this.socket.getInputStream());
        this.out = new BufferedOutputStream(this.socket.getOutputStream());
    }

    public void start() {

        final var threadPool = Executors.newFixedThreadPool(64);
        System.out.println("Сервер стартовал");
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
            final var limit = 4096;

            this.in.mark(limit);

            final var buffer = new byte[limit];
            final var read = in.read(buffer);

            Request request = new Request();
            setIsCorrect(buffer,limit,request);
            request.setRequestHeaders(setHeaders(buffer));
            if (!request.getIsCorrect()) {
                badRequest(out);
                return;
            }
            if (request.getIsQuery()) {
            }
            if (setBody(buffer, request, limit)) {
            }
            final var path = request.getPath();
            if (path == null) {
                badRequest(out);
                return;
            } else {
                if (!validPaths.contains(path)) {
                    badRequest(out);
                    return;
                }
            }
            final var filePath = Path.of("src", "main/resources", path);
            final var mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
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

    public void setIsCorrect(byte[] buffer, int limit, Request request) {
        final var target = new byte[]{'\r','\n'};
        int index = indexOf(buffer, target, 0 , limit);
        //String[] firstLine = String.valueOf(buffer).substring(0, index).split(" ");
        String[] firstLine = new String(Arrays.copyOf(buffer, index)).split(" ");

        if (firstLine.length != 3) {
            request.setIsCorrect(false);
        } else {
            request.setIsCorrect(true);
            request.setRequestMethod(firstLine[0]);
            if (firstLine[1].contains("?")) {
                request.setRequestPath(firstLine[1].substring(0, firstLine[1].indexOf("?")));
                request.setQueryLine(firstLine[1].substring(firstLine[1].indexOf("?"), firstLine[1].length() - 1));
                request.setIsQuery(true);
            } else {
                request.setRequestPath(firstLine[1]);
                request.setIsQuery(false);
            }
        }
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    protected List<NameValuePair> setHeaders(byte[] buffer) {
        final var targetF = new byte[]{'\r', '\n', '\r', '\n'};
        final var targetS = new byte[]{'\r', '\n'};
        int startOfHeaders = indexOf(buffer,targetS,0,buffer.length)+2;
        int finishOfHeaders = indexOf(buffer,targetF,startOfHeaders,buffer.length);
        List<NameValuePair> requestHeaders = new ArrayList<>();
        String[] headersValues = new String(Arrays.copyOf(buffer, finishOfHeaders)).split("\r\n");
        //String[] headersValues = String.valueOf(buffer).substring(startOfHeaders,finishOfHeaders).split("\r\n");
        int ind = 0;
        Stream.of(headersValues)
                .forEach(x->{
                    if (x.indexOf(":") != -1) {
                    String[] strgs = x.split(":");
                    requestHeaders.add(new BasicNameValuePair(strgs[0],strgs[1]));}
                });
        return requestHeaders;
    }

    protected boolean setBody(byte[] buffer, Request request, int limit) {
        List<NameValuePair> contentLength = request.getHeader("Content-Length");
        if (!contentLength.isEmpty()) {
            byte[] target = new byte[]{'\r', '\n', '\r', '\n'};
            int start = indexOf(buffer,target,0,limit)+4;
            int finish = start + Integer.parseInt(contentLength.get(0).getValue().trim(),10);
            request.setRequestBody(String.valueOf(buffer).substring(start,finish));
            return true;
        } else {
            return false;
        }
    }

}
