package com.indiatravel.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ItineraryDesktopApp extends Application {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ListView<String> dayList = new ListView<>();
    private final TextArea details = new TextArea();

    @Override
    public void start(Stage stage) {
        TextField backendUrl = new TextField("http://localhost:8080/api");
        TextField title = new TextField("Desktop India Journey");
        TextField startDate = new TextField("2026-04-01");
        TextField endDate = new TextField("2026-04-04");
        TextField cities = new TextField("Delhi,Jaipur,Goa");
        TextField interests = new TextField("heritage,nature,food");
        TextField budget = new TextField("30000");
        ComboBox<String> pace = new ComboBox<>(FXCollections.observableArrayList("relaxed", "balanced", "fast"));
        pace.setValue("balanced");
        ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList("cab", "metro", "train", "flight"));
        mode.setValue("cab");
        Button generate = new Button("Generate Trip");

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.setPadding(new Insets(12));
        int row = 0;
        form.addRow(row++, new Label("Backend URL"), backendUrl);
        form.addRow(row++, new Label("Title"), title);
        form.addRow(row++, new Label("Start Date"), startDate);
        form.addRow(row++, new Label("End Date"), endDate);
        form.addRow(row++, new Label("Cities"), cities);
        form.addRow(row++, new Label("Interests"), interests);
        form.addRow(row++, new Label("Budget INR"), budget);
        form.addRow(row++, new Label("Pace"), pace);
        form.addRow(row++, new Label("Travel Mode"), mode);
        form.add(generate, 1, row);

        details.setWrapText(true);
        details.setEditable(false);
        details.setPrefRowCount(18);

        dayList.setPrefWidth(240);
        dayList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                details.setText(newValue);
            }
        });

        VBox right = new VBox(10, new Label("Trip Days"), dayList, new Label("Day Details"), details);
        right.setPadding(new Insets(12));

        BorderPane root = new BorderPane();
        root.setLeft(form);
        root.setCenter(right);

        generate.setOnAction(e -> {
            generate.setDisable(true);
            details.setText("Generating itinerary...");
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", 1);
            payload.put("title", title.getText());
            payload.put("startDate", startDate.getText());
            payload.put("endDate", endDate.getText());
            payload.put("cities", csvToList(cities.getText()));
            payload.put("interests", csvToList(interests.getText()));
            payload.put("budgetInr", Integer.parseInt(budget.getText()));
            payload.put("pace", pace.getValue());
            payload.put("preferredTravelMode", mode.getValue());

            createAndLoadTrip(backendUrl.getText(), payload)
                    .whenComplete((v, ex) -> Platform.runLater(() -> generate.setDisable(false)));
        });

        Scene scene = new Scene(root, 1050, 680);
        stage.setTitle("India Itinerary Desktop (JavaFX)");
        stage.setScene(scene);
        stage.show();
    }

    private CompletableFuture<Void> createAndLoadTrip(String baseUrl, Map<String, Object> payload) {
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/trips/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(this::extractTripId)
                    .thenCompose(tripId -> loadTrip(baseUrl, tripId))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> details.setText("Error: " + ex.getMessage()));
                        return null;
                    });
        } catch (IOException ex) {
            details.setText("Payload error: " + ex.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> loadTrip(String baseUrl, long tripId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/trips/" + tripId))
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(this::renderTrip)
                .exceptionally(ex -> {
                    Platform.runLater(() -> details.setText("Load error: " + ex.getMessage()));
                    return null;
                });
    }

    private long extractTripId(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node.path("tripId").asLong();
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid backend response", ex);
        }
    }

    private void renderTrip(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String tripTitle = root.path("title").asText("Untitled");
            List<Map<String, Object>> days = mapper.convertValue(root.path("days"), new TypeReference<>() {});
            Platform.runLater(() -> {
                dayList.getItems().clear();
                for (Map<String, Object> day : days) {
                    int dayNumber = (int) day.get("dayNumber");
                    String city = String.valueOf(day.get("city"));
                    String theme = String.valueOf(day.get("theme"));
                    List<Map<String, Object>> places = (List<Map<String, Object>>) day.get("places");
                    StringBuilder text = new StringBuilder();
                    text.append(tripTitle).append("\n");
                    text.append("Day ").append(dayNumber).append(" - ").append(city).append(" (").append(theme).append(")\n\n");
                    for (Map<String, Object> place : places) {
                        text.append(place.get("arrivalTime"))
                                .append(" | ")
                                .append(place.get("name"))
                                .append(" | ")
                                .append(place.get("category"))
                                .append("\n");
                    }
                    dayList.getItems().add(text.toString());
                }
                if (!dayList.getItems().isEmpty()) {
                    dayList.getSelectionModel().selectFirst();
                }
            });
        } catch (Exception ex) {
            Platform.runLater(() -> details.setText("Render error: " + ex.getMessage()));
        }
    }

    private static List<String> csvToList(String input) {
        return List.of(input.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
