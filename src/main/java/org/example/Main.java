package org.example;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.*;

public class Main {

    static Map<Shipment, LinkedList<Package>> hashMap= new HashMap<>();
    static Set<String> countries = new HashSet<>();

    static Map<String, String> countryToCurrency = new HashMap<>();
    static Map<String, Double> currencyRatesToUSD = new HashMap<>();

    static Map<String, Doublee> shipmentsPerMonth = new HashMap<>();
    static Map<String, Set<String>> routeGraph = new HashMap<>();

    //the folder that contains all the data should be in the same folder as this java project
    public static void main(String[] args) {

        countryToCurrency.put("US", "USD");
        countryToCurrency.put("GB", "GBP");
        countryToCurrency.put("DE", "EUR");
        countryToCurrency.put("FR", "EUR");
        countryToCurrency.put("CA", "CAD");
        countryToCurrency.put("CN", "CNY");
        countryToCurrency.put("AU", "AUD");
        countryToCurrency.put("JP", "JPY");

        currencyRatesToUSD.put("USD", 1.0);
        currencyRatesToUSD.put("GBP", 1.27);
        currencyRatesToUSD.put("EUR", 1.08);
        currencyRatesToUSD.put("CAD", 0.73);
        currencyRatesToUSD.put("CNY", 0.14);
        currencyRatesToUSD.put("AUD", 0.66);
        currencyRatesToUSD.put("JPY", 0.0067);


        String folderPath = "../data";

        int total = 0;

        try {
            Files.walk(Paths.get(folderPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().matches(".*\\.(csv)$"))
                    .forEach(path -> {
                        File file = path.toFile();
                        try (CSVReader reader = new CSVReader(new FileReader(file))) {
                            String[] headers = reader.readNext();
                            if (headers == null) return;

                            String[] row;
                            while ((row = reader.readNext()) != null) {
                                processRow(file.getName(), headers, row);
                            }
                        } catch (IOException | CsvValidationException e) {
                            System.err.println("Error reading file " + file.getName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error walking folder: " + e.getMessage());
        }

        System.out.println("\n--- Packages Connected To Shipment Visualization ---\n");
        for (Map.Entry<Shipment, LinkedList<Package>> entry : hashMap.entrySet()) {
            Shipment shipment = entry.getKey();
            LinkedList<Package> packages = entry.getValue();

            double totalWeight = 0.0;
            double totalCost = 0.0;
            LocalDateTime minTime = LocalDateTime.MAX;
            LocalDateTime maxTime = LocalDateTime.MIN;


            for (Package pkg : packages) {
                totalWeight += pkg.weight;
                totalCost += pkg.cost;

                if(pkg.dateTime.isBefore(minTime)){
                    minTime = pkg.dateTime;
                }
                if(pkg.dateTime.isAfter(maxTime)){
                    maxTime = pkg.dateTime;
                }


            }

            Duration duration = Duration.between(minTime, maxTime);

            String currencyCountry = shipment.destinationCountry;

            String currencyCode = countryToCurrency.getOrDefault(currencyCountry, "USD");
            double conversionRate = currencyRatesToUSD.getOrDefault(currencyCode, 1.0);
            double totalCostInUSD = totalCost * conversionRate;


            System.out.printf("Shipment: %s, Total Packages: %d, Total Weight: %.2fg, Total Time: %d hours, Total Cost: %.2f USD%n",
                               shipment.shipmentID,
                               packages.size(),
                               totalWeight,
                               duration.toHours(),
                               totalCostInUSD);

            String monthYear = String.valueOf(packages.getFirst().dateTime.getYear()) + "-" + String.format("%02d", packages.getFirst().dateTime.getMonthValue());

            boolean isUSInbound = !shipment.originCountry.equals("US") && shipment.destinationCountry.equals("US");
            boolean isUSOutbound = shipment.originCountry.equals("US") && !shipment.destinationCountry.equals("US");

            if (isUSInbound) {
                Doublee d = shipmentsPerMonth.computeIfAbsent(monthYear, my -> new Doublee());
                d.entering++; 
            } else if (isUSOutbound) {
                Doublee d = shipmentsPerMonth.computeIfAbsent(monthYear, my -> new Doublee());
                d.exiting++; 
            }
        }

        System.out.println("\n--- US Shipments Visualization ---\n");
        shipmentsPerMonth.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                System.out.printf("Month: %s, Inbound to US: %d, Outbound from US: %d%n",
                                  entry.getKey(),
                                  entry.getValue().getEntering(),
                                  entry.getValue().getExiting());
            });

        findRoute("Los Angeles", "Doncaster");

        System.out.println("Done.");
    }

    private static void findRoute(String start, String end) {
        System.out.printf("\n--- Finding route from %s to %s ---\n", start, end);
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(start));
        Set<String> visited = new HashSet<>();
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String lastNode = path.get(path.size() - 1);

            if (lastNode.equals(end)) {
                System.out.println("Route found: " + String.join(" -> ", path));
                return;
            }

            Set<String> neighbors = routeGraph.get(lastNode);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        List<String> newPath = new ArrayList<>(path);
                        newPath.add(neighbor);
                        queue.add(newPath);
                    }
                }
            }
        }

        System.out.println("No route found from " + start + " to " + end);
    }

    private static void processRow(String sourceFile, String[] headers, String[] row) {
        int idx = Arrays.asList(headers).indexOf("ShipmentID");
        int origin = Arrays.asList(headers).indexOf("Origin");
        int originCountry = Arrays.asList(headers).indexOf("OriginCountry");
        int originRegion = Arrays.asList(headers).indexOf("OriginRegion");
        int destination = Arrays.asList(headers).indexOf("Destination");
        int destinationCountry = Arrays.asList(headers).indexOf("DestinationCountry");
        int destinationRegion = Arrays.asList(headers).indexOf("DestinationRegion");

        routeGraph.computeIfAbsent(row[origin], k -> new HashSet<>()).add(row[destination]);

        countries.add(row[destinationCountry]);
        Shipment s = new Shipment(row[idx], row[origin], row[originCountry], row[originRegion], row[destination], row[destinationCountry], row[destinationRegion]);


        int packageID = Arrays.asList(headers).indexOf("PackageID");
        int weight = Arrays.asList(headers).indexOf("Weight");
        int cost = Arrays.asList(headers).indexOf("Cost");
        int eventTime = Arrays.asList(headers).indexOf("EventTime");

        String packageIDValue = row[packageID];
        Double weightValue = Double.parseDouble(row[weight]);
        Double costValue = Double.parseDouble(row[cost]);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime eventTimeValue = LocalDateTime.parse(row[eventTime], formatter);

        Package p = new Package(packageIDValue, weightValue, costValue, eventTimeValue);


        if (idx >= 0 && idx < row.length) {
            LinkedList<Package> l = hashMap.computeIfAbsent(s, k -> new LinkedList<>());
            l.add(p);
        }

    }

    public static class Doublee{

        int entering;
        int exiting;

        public Doublee() {
            this.entering = 0;
            this.exiting = 0;
        }

        public int getEntering() {
            return entering;
        }

        public void setEntering(int entering) {
            this.entering = entering;
        }

        public int getExiting() {
            return exiting;
        }

        public void setExiting(int exiting) {
            this.exiting = exiting;
        }
    }

    public static class Shipment {
        String shipmentID;
        String origin;
        String originCountry;
        String originRegion;
        String destination;
        String destinationCountry;
        String destinationRegion;

        public Shipment(String shipmentID, String origin, String originCountry, String originRegion, String destination, String destinationCountry, String destinationRegion) {
            this.shipmentID = shipmentID;
            this.origin = origin;
            this.originCountry = originCountry;
            this.originRegion = originRegion;
            this.destination = destination;
            this.destinationCountry = destinationCountry;
            this.destinationRegion = destinationRegion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Shipment shipment = (Shipment) o;
            return shipmentID.equals(shipment.shipmentID);
        }

        @Override
        public int hashCode() {
            return shipmentID.hashCode();
        }
    }
    public static class Package {
        String packageID;
        Double weight;
        Double cost;
        LocalDateTime dateTime;

        public Package(String packageID, Double weight, Double cost, LocalDateTime dateTime) {
            this.packageID = packageID;
            this.weight = weight;
            this.cost = cost;
            this.dateTime = dateTime;
        }
    }


}
