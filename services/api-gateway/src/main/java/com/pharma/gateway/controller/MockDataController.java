package com.pharma.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MockDataController {

    @GetMapping(value = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getInventory() {
        return Mono.just(List.of(
            Map.of("id", 1, "itemCode", "INV-001", "itemName", "Paracetamol 500mg", "itemType", "Finished Good", "quantity", 5000, "unitOfMeasure", "Tablets", "status", "ACTIVE"),
            Map.of("id", 2, "itemCode", "INV-002", "itemName", "Amoxicillin 250mg", "itemType", "Finished Good", "quantity", 3200, "unitOfMeasure", "Capsules", "status", "ACTIVE"),
            Map.of("id", 3, "itemCode", "INV-003", "itemName", "Ibuprofen API", "itemType", "Raw Material", "quantity", 800, "unitOfMeasure", "KG", "status", "ACTIVE"),
            Map.of("id", 4, "itemCode", "INV-004", "itemName", "Metformin 850mg", "itemType", "Finished Good", "quantity", 1200, "unitOfMeasure", "Tablets", "status", "ACTIVE"),
            Map.of("id", 5, "itemCode", "INV-005", "itemName", "Cellulose Excipient", "itemType", "Raw Material", "quantity", 250, "unitOfMeasure", "KG", "status", "ACTIVE")
        ));
    }

    @GetMapping(value = "/manufacturing", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getManufacturing() {
        return Mono.just(List.of(
            Map.of("id", 1, "batchNumber", "BATCH-2024-001", "productName", "Paracetamol 500mg", "quantity", 10000, "scheduledDate", "2024-04-10", "assignedLine", "Line A", "status", "ACTIVE"),
            Map.of("id", 2, "batchNumber", "BATCH-2024-002", "productName", "Amoxicillin 250mg", "quantity", 8000, "scheduledDate", "2024-04-12", "assignedLine", "Line B", "status", "ACTIVE"),
            Map.of("id", 3, "batchNumber", "BATCH-2024-003", "productName", "Ibuprofen 400mg", "quantity", 6000, "scheduledDate", "2024-04-15", "assignedLine", "Line A", "status", "ACTIVE"),
            Map.of("id", 4, "batchNumber", "BATCH-2024-004", "productName", "Metformin 850mg", "quantity", 5000, "scheduledDate", "2024-04-18", "assignedLine", "Line C", "status", "ACTIVE")
        ));
    }

    @GetMapping(value = "/distribution", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getDistribution() {
        return Mono.just(List.of(
            Map.of("id", 1, "shipmentNumber", "SHIP-2024-001", "destination", "City Hospital, Mumbai", "carrier", "BlueDart Logistics", "dispatchDate", "2024-04-05", "estimatedArrival", "2024-04-07", "status", "ACTIVE"),
            Map.of("id", 2, "shipmentNumber", "SHIP-2024-002", "destination", "Apollo Pharmacy, Delhi", "carrier", "DHL Express", "dispatchDate", "2024-04-06", "estimatedArrival", "2024-04-08", "status", "ACTIVE"),
            Map.of("id", 3, "shipmentNumber", "SHIP-2024-003", "destination", "Medplus, Bangalore", "carrier", "FedEx", "dispatchDate", "2024-04-08", "estimatedArrival", "2024-04-10", "status", "ACTIVE")
        ));
    }

    @GetMapping(value = "/suppliers", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getSuppliers() {
        return Mono.just(List.of(
            Map.of("id", 1, "supplierCode", "SUP-001", "companyName", "ChemIndia Pvt Ltd", "contactPerson", "Rajesh Kumar", "email", "rajesh@chemindia.com", "phone", "+91-9876543210", "status", "ACTIVE"),
            Map.of("id", 2, "supplierCode", "SUP-002", "companyName", "PharmaRaw Corp", "contactPerson", "Priya Sharma", "email", "priya@pharmaraw.com", "phone", "+91-9876543211", "status", "ACTIVE"),
            Map.of("id", 3, "supplierCode", "SUP-003", "companyName", "BioSynth Labs", "contactPerson", "Amit Patel", "email", "amit@biosynth.com", "phone", "+91-9876543212", "status", "ACTIVE"),
            Map.of("id", 4, "supplierCode", "SUP-004", "companyName", "Global APIs Ltd", "contactPerson", "Sunita Rao", "email", "sunita@globalapis.com", "phone", "+91-9876543213", "status", "ACTIVE")
        ));
    }

    @GetMapping(value = "/qc", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getQualityControl() {
        return Mono.just(List.of(
            Map.of("id", 1, "batchNumber", "BATCH-2024-001", "testType", "Dissolution Test", "result", "PASS", "testedBy", "Dr. Mehta", "testDate", "2024-04-11", "status", "ACTIVE"),
            Map.of("id", 2, "batchNumber", "BATCH-2024-001", "testType", "Assay Test", "result", "PASS", "testedBy", "Dr. Mehta", "testDate", "2024-04-11", "status", "ACTIVE"),
            Map.of("id", 3, "batchNumber", "BATCH-2024-002", "testType", "Microbial Test", "result", "PASS", "testedBy", "Dr. Singh", "testDate", "2024-04-13", "status", "ACTIVE"),
            Map.of("id", 4, "batchNumber", "BATCH-2024-003", "testType", "Dissolution Test", "result", "FAIL", "testedBy", "Dr. Mehta", "testDate", "2024-04-16", "status", "ACTIVE")
        ));
    }

    @GetMapping(value = "/reports", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<Map<String, Object>>> getReports() {
        return Mono.just(List.of(
            Map.of("id", 1, "reportName", "Monthly Production Summary", "reportType", "Production", "generatedBy", "admin", "parameters", "April 2024", "status", "ACTIVE"),
            Map.of("id", 2, "reportName", "Inventory Stock Report", "reportType", "Inventory", "generatedBy", "admin", "parameters", "Q1 2024", "status", "ACTIVE"),
            Map.of("id", 3, "reportName", "QC Failure Analysis", "reportType", "Quality", "generatedBy", "pharmacist1", "parameters", "April 2024", "status", "ACTIVE"),
            Map.of("id", 4, "reportName", "Supplier Performance Report", "reportType", "Procurement", "generatedBy", "admin", "parameters", "FY 2024", "status", "ACTIVE")
        ));
    }
}
