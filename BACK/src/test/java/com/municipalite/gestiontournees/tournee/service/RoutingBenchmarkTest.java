package com.municipalite.gestiontournees.tournee.service;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Replays saved road matrices with the same algorithms used by the application. */
class RoutingBenchmarkTest {
    @Test void replayFixtures() throws Exception {
        Path directory=Path.of(System.getProperty("route.fixtures","../evaluation/routing/fixtures"));
        Path output=Path.of(System.getProperty("route.output","target/evaluation/routing.csv"));
        ObjectMapper mapper=new ObjectMapper();
        List<String> rows=new ArrayList<>(List.of("scenario,source,points,algorithm,distance_m,travel_s,service_s,total_s,runtime_ms,improvement_vs_manual_pct,gap_to_exact_pct"));
        try(var paths=Files.list(directory)) {
            List<Path> fixtures=paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(fixtures.isEmpty(),"Supply saved route-export JSON files");
            for(Path path:fixtures) {
                JsonNode root=mapper.readTree(path.toFile()); JsonNode node=root.has("route") ? root.get("route") : root;
                double[][] time=mapper.treeToValue(node.get("durations"),double[][].class), distance=mapper.treeToValue(node.get("distances"),double[][].class);
                List<RouteAlgorithms.Result> results=RouteAlgorithms.compare(time,distance);
                double baseline=results.get(0).cost().seconds();
                Double exact=results.stream().filter(r->r.algorithm().equals("EXACT")).map(r->r.cost().seconds()).findFirst().orElse(null);
                double service=node.path("serviceSeconds").asDouble(300.0*(time.length-1));
                String source=node.path("source").asText(node.path("provider").asText("UNSPECIFIED"));
                for(var r:results) rows.add(String.join(",",path.getFileName().toString(),source,Integer.toString(time.length-1),r.algorithm(),
                        Double.toString(r.cost().meters()),Double.toString(r.cost().seconds()),Double.toString(service),Double.toString(r.cost().seconds()+service),
                        Double.toString(r.runtimeMillis()),baseline>0 ? Double.toString(100*(baseline-r.cost().seconds())/baseline) : "",
                        exact!=null && exact>0 ? Double.toString(100*(r.cost().seconds()-exact)/exact) : ""));
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent()); Files.write(output,rows);
        System.out.println("Routing benchmark written to " + output.toAbsolutePath());
    }
}
