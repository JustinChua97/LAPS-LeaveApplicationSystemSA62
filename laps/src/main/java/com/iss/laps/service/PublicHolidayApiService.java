package com.iss.laps.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iss.laps.exception.PublicHolidaySyncException;
import com.iss.laps.model.PublicHoliday;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicHolidayApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3000;

    public List<PublicHoliday> fetchHolidaysFromApi(String apiUrl) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429) {
                    if (attempt == MAX_RETRIES) {
                        throw new PublicHolidaySyncException(
                                "data.gov.sg rate limit reached after " + MAX_RETRIES + " attempts. Please try again in a minute.");
                    }
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                    continue;
                }
                if (response.statusCode() != 200) {
                    throw new PublicHolidaySyncException("API returned HTTP " + response.statusCode());
                }
                break;
            } catch (PublicHolidaySyncException e) {
                throw e;
            } catch (Exception e) {
                throw new PublicHolidaySyncException("Failed to connect to data.gov.sg API", e);
            }
        }

        List<PublicHoliday> holidays = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(response.body());
            if (!root.path("success").asBoolean(true)) {
                throw new PublicHolidaySyncException("API error: " + root.path("errorMsg").asText("unknown error"));
            }
            JsonNode records = root.path("result").path("records");
            for (JsonNode record : records) {
                String dateStr = record.path("date").asText();
                String name    = record.path("holiday").asText();
                if (dateStr.isBlank() || name.isBlank()) continue;
                LocalDate date = LocalDate.parse(dateStr);
                PublicHoliday h = new PublicHoliday();
                h.setHolidayDate(date);
                h.setYear(date.getYear());
                h.setDescription(name);
                holidays.add(h);
            }
        } catch (PublicHolidaySyncException e) {
            throw e;
        } catch (Exception e) {
            throw new PublicHolidaySyncException("Failed to parse API response", e);
        }
        return holidays;
    }
}
