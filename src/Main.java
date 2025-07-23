// File: Main.java
import java.net.http.*;
import java.net.URI;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;

import org.opencv.core.*;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgcodecs.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

public class Main {

    static String lastMood = "";
    static String lastWeather = "";

    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();

        // 1. Get user's approximate city via IP
        String city = getCityFromIp();
        if (city == null) {
            System.err.println("Could not determine location.");
            return;
        }

        // 2. Fetch Weather
        String weather = getWeather(city);

        // 3. Get mood from webcam
        String mood = detectMoodFromFace();

        // 4. Log to CSV only if new
        if (!mood.equals(lastMood) || !weather.equals(lastWeather)) {
            logToCsv(city, mood, weather);
            lastMood = mood;
            lastWeather = weather;
        }
    }

    public static String getCityFromIp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest locReq = HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json"))
                .GET()
                .build();

        HttpResponse<String> locRes = client.send(locReq, HttpResponse.BodyHandlers.ofString());
        String body = locRes.body();
        String city = body.split("\"city\":\"")[1].split("\"")[0];
        System.out.println("Detected Location: " + city);
        return city;
    }

    public static String getWeather(String city) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String apiKey = "a0a29b535da04d61a94202017252207";
        String url = "https://api.weatherapi.com/v1/current.json?key=" + apiKey + "&q=" + city + "&aqi=no";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        String condition = body.split("\"text\":\"")[1].split("\"")[0];
        System.out.println("Weather: " + condition);
        return condition;
    }

    public static String detectMoodFromFace() throws InterruptedException {
        CascadeClassifier faceCascade = new CascadeClassifier("resources/haarcascade_frontalface_alt.xml");
        CascadeClassifier smileCascade = new CascadeClassifier("resources/haarcascade_smile.xml");
        CascadeClassifier eyeCascade = new CascadeClassifier("resources/haarcascade_eye_tree_eyeglasses.xml");

        VideoCapture cap = new VideoCapture(0);
        if (!cap.isOpened()) {
            System.out.println("Camera not found");
            return "Unknown";
        }

        Mat frame = new Mat();
        String result = "Neutral";
        int closedEyesCount = 0;

        for (int i = 0; i < 30; i++) {
            cap.read(frame);
            if (frame.empty()) continue;

            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            // Face Detection
            MatOfRect faces = new MatOfRect();
            faceCascade.detectMultiScale(gray, faces);
            Rect[] facesArray = faces.toArray();

            if (facesArray.length > 0) {
                // Smile Detection
                MatOfRect smiles = new MatOfRect();
                smileCascade.detectMultiScale(gray, smiles, 1.7, 20);
                Rect[] smilesArray = smiles.toArray();

                // Eye Detection
                MatOfRect eyes = new MatOfRect();
                eyeCascade.detectMultiScale(gray, eyes, 1.1, 5);
                Rect[] eyesArray = eyes.toArray();

                if (smilesArray.length > 0) {
                    result = "Happy";
                } else if (eyesArray.length == 0) {
                    closedEyesCount++;
                }
            }

            Thread.sleep(100);
        }

        cap.release();

        if (closedEyesCount > 15) result = "Sleepy";
        System.out.println("Detected Mood: " + result);
        return result;
    }


    public static void logToCsv(String city, String mood, String weather) throws IOException {
        String filePath = "log.csv";
        File file = new File(filePath);
        boolean exists = file.exists();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            if (!exists) {
                writer.write("Timestamp,City,Mood,Weather\n");
            }
            writer.write(LocalDateTime.now() + "," + city + "," + mood + "," + weather + "\n");
        }

        System.out.println("Logged to log.csv ✅");
    }
}
