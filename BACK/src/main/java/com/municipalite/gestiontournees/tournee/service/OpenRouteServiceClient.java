package com.municipalite.gestiontournees.tournee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service @RequiredArgsConstructor
public class OpenRouteServiceClient {
    public record Matrix(double[][] durations, double[][] distances) {}
    private final RoutingProperties properties;
    private final ObjectMapper mapper;
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build();

    public Matrix matrix(List<List<Double>> coordinates) {
        JsonNode json = post("/v2/matrix/" + properties.getProfile(),
                Map.of("locations", coordinates, "metrics", List.of("duration", "distance")));
        return new Matrix(matrixValues(json.get("durations"), coordinates.size()),
                matrixValues(json.get("distances"), coordinates.size()));
    }

    public List<List<Double>> geometry(List<List<Double>> orderedCoordinates) {
        JsonNode json = post("/v2/directions/" + properties.getProfile() + "/geojson",
                Map.of("coordinates", orderedCoordinates, "instructions", false));
        JsonNode coordinates = json.path("features").path(0).path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) throw unavailable("Géométrie routière absente");
        List<List<Double>> result = new ArrayList<>();
        for (JsonNode pair : coordinates) {
            if (pair.size() < 2 || !pair.get(0).isNumber() || !pair.get(1).isNumber())
                throw unavailable("Géométrie routière invalide");
            double lon = pair.get(0).asDouble(), lat = pair.get(1).asDouble();
            if (!Double.isFinite(lon) || !Double.isFinite(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90)
                throw unavailable("Coordonnées de géométrie routière invalides");
            result.add(List.of(lon, lat));
        }
        return result;
    }

    private double[][] matrixValues(JsonNode array, int size) {
        if (array == null || !array.isArray() || array.size() != size) throw unavailable("Matrice routière invalide");
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) {
            if (!array.get(i).isArray() || array.get(i).size() != size) throw unavailable("Matrice routière incomplète");
            for (int j = 0; j < size; j++) {
                JsonNode value = array.get(i).get(j);
                if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0)
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Liaison routière inaccessible entre les positions " + i + " et " + j + " (0 = dépôt)");
                result[i][j] = value.asDouble();
            }
        }
        return result;
    }

    private JsonNode post(String path, Object body) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw unavailable("Configurez ORS_API_KEY pour calculer un itinéraire routier");
        try {
            Request request = new Request.Builder().url(properties.getBaseUrl().replaceAll("/$", "") + path)
                    .header("Authorization", properties.getApiKey())
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), MediaType.get("application/json"))).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                    throw unavailable("Service routier indisponible (HTTP " + response.code() + "). Réessayez plus tard.");
                return mapper.readTree(response.body().string());
            }
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw unavailable("Service routier inaccessible. Réessayez plus tard."); }
    }
    private ResponseStatusException unavailable(String reason) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }
}
