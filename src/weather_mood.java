import java.net.http.*;
import java.net.URI;
import java.io.*;
import java.time.LocalDateTime;
import org.opencv.core.*;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.highgui.HighGui;

public class weather_mood {

    static String lastMood = "";
    static String lastWeather = "";
    static CascadeClassifier faceCascade;
    static CascadeClassifier smileCascade;
    static CascadeClassifier eyeCascade;

    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();

        // Initialize classifiers once
        faceCascade = new CascadeClassifier("resources/haarcascade_frontalface_alt.xml");
        smileCascade = new CascadeClassifier("resources/haarcascade_smile.xml");
        eyeCascade = new CascadeClassifier("resources/haarcascade_eye_tree_eyeglasses.xml");

        // 1. Get user's approximate city via IP
        String city = getCityFromIp();
        if (city == null) {
            System.err.println("Could not determine location.");
            return;
        }

        // 2. Fetch initial weather
        String weather = getWeather(city);
        System.out.println("Starting continuous camera feed. Press 'q' to quit.");

        // 3. Start continuous camera feed with periodic detection
        startContinuousDetection(city);
    }

    public static void startContinuousDetection(String city) throws Exception {
        VideoCapture cap = new VideoCapture(0);
        if (!cap.isOpened()) {
            System.out.println("Camera not found");
            return;
        }

        Mat frame = new Mat();
        long lastDetectionTime = 0;
        long detectionInterval = 5000; // 5 seconds in milliseconds

        System.out.println("Camera feed started. Detection runs every 5 seconds.");
        System.out.println("Press 'q' or ESC to quit.");

        while (true) {
            // Read frame from camera
            cap.read(frame);
            if (frame.empty()) {
                continue;
            }

            // Check if it's time for detection (every 5 seconds)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDetectionTime >= detectionInterval) {
                // Perform mood detection
                String mood = detectMoodFromCurrentFrame(frame);

                // Get updated weather
                String weather = getWeather(city);

                // Log if changed
                if (!mood.equals(lastMood) || !weather.equals(lastWeather)) {
                    logToCsv(city, mood, weather);
                    lastMood = mood;
                    lastWeather = weather;
                }

                lastDetectionTime = currentTime;
                System.out.println("Detection completed: " + mood + " | " + weather);
            }

            // Display the frame with detection info
            displayFrameWithInfo(frame, lastMood);

            // FIXED: Check for quit key with proper ASCII comparison
            int key = HighGui.waitKey(30) & 0xFF;
            if (key == 27 || key == 113) { // ESC (27) or 'q' (113) to quit
                System.out.println("Quit key pressed. Stopping camera...");
                break;
            }
        }

        cap.release();
        HighGui.destroyAllWindows();
        System.out.println("Camera feed stopped.");
    }


    public static String detectMoodFromCurrentFrame(Mat currentFrame) {
        Mat gray = new Mat();
        Imgproc.cvtColor(currentFrame, gray, Imgproc.COLOR_BGR2GRAY);

        String result = "Neutral";

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
                result = "Sleepy";
            }

            // Draw rectangles around detected faces
            for (Rect face : facesArray) {
                Imgproc.rectangle(currentFrame, face.tl(), face.br(), new Scalar(0, 255, 0), 2);
            }
        }

        return result;
    }

    public static void displayFrameWithInfo(Mat frame, String mood) {
        // Add mood text overlay on frame
        String displayText = "Mood: " + mood + " | Press 'q' to quit";
        Imgproc.putText(frame, displayText, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 0), 2);

        // Display the frame
        HighGui.imshow("Weather Mood Detector", frame);
    }

    // Keep your existing methods unchanged
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

        // Get API key from environment variable
        String apiKey = System.getenv("WEATHER_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("WEATHER_API_KEY environment variable not set");
        }

        String url = "https://api.weatherapi.com/v1/current.json?key=" + apiKey + "&q=" + city + "&aqi=no";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        return body.split("\"text\":\"")[1].split("\"")[0];
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
