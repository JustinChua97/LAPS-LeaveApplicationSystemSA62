package com.iss.laps.service;

import com.iss.laps.model.PublicHoliday;
import com.iss.laps.exception.PublicHolidaySyncException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicHolidayCsvService {

    public List<PublicHoliday> fetchHolidaysFromCsv(String csvUrl) {
        List<PublicHoliday> holidays = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(csvUrl).openStream()))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) { // skip header row
                    headerSkipped = true;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 3) continue; // skip malformed rows

                LocalDate date = LocalDate.parse(parts[0].trim());
                String name = parts[2].trim();

                PublicHoliday h = new PublicHoliday();
                h.setHolidayDate(date);
                h.setYear(date.getYear());
                h.setDescription(name);

                holidays.add(h);
            }
        } catch (Exception e) {
            throw new PublicHolidaySyncException("Failed to fetch CSV", e);
        }
        return holidays;
    }
}
