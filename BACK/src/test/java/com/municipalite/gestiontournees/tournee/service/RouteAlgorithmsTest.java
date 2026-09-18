package com.municipalite.gestiontournees.tournee.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RouteAlgorithmsTest {
    @Test void heldKarpMatchesIndependentPermutationSearchOnDirectedInstances() {
        Random random=new Random(417);
        for(int n=2;n<=7;n++) {
            double[][] time=new double[n+1][n+1],distance=new double[n+1][n+1];
            for(int i=0;i<=n;i++) for(int j=0;j<=n;j++) if(i!=j) { time[i][j]=1+random.nextInt(30); distance[i][j]=1+random.nextInt(100); }
            List<Integer> points=new ArrayList<>(); for(int i=1;i<=n;i++) points.add(i);
            RouteAlgorithms.Cost brute=brute(points,0,time,distance);
            List<RouteAlgorithms.Result> results=RouteAlgorithms.compare(time,distance);
            var exact=results.stream().filter(r -> r.algorithm().equals("EXACT")).findFirst().orElseThrow();
            assertEquals(brute,exact.cost());
            for(var r:results) { assertEquals(n,new HashSet<>(r.order()).size()); assertFalse(r.order().contains(0)); assertFalse(RouteAlgorithms.better(r.cost(),exact.cost())); }
            assertFalse(RouteAlgorithms.better(results.get(1).cost(),results.get(2).cost()));
            var selected=RouteAlgorithms.bestHeuristic(results);
            for(var r:results.subList(0,3)) assertFalse(RouteAlgorithms.better(r.cost(),selected.cost()));
        }
    }
    @Test void includesDepotReturnAndDistanceTiebreaker() {
        double[][] t={{0,1,1},{5,0,1},{2,1,0}}, d={{0,2,5},{8,0,3},{4,5,0}};
        assertEquals(new RouteAlgorithms.Cost(4,9),RouteAlgorithms.cost(List.of(1,2),t,d));
        double[][] equal={{0,1,1},{1,0,1},{1,1,0}};
        assertEquals(List.of(1,2),RouteAlgorithms.heldKarp(equal,d));
    }
    @Test void directedTwoOptRecomputesReversedInternalArcs() {
        double[][] t={{0,2,9,5},{7,0,1,10},{3,20,0,2},{2,4,9,0}};
        List<Integer> initial=List.of(3,2,1); var improved=RouteAlgorithms.twoOpt(initial,t,t);
        assertFalse(RouteAlgorithms.better(RouteAlgorithms.cost(initial,t,t),RouteAlgorithms.cost(improved,t,t)));
        assertEquals(new HashSet<>(initial),new HashSet<>(improved));
    }
    @Test void oneStopIsRoundTripAndLargeCasesOmitExact() {
        var one=RouteAlgorithms.compare(new double[][]{{0,3},{7,0}},new double[][]{{0,4},{8,0}});
        assertEquals(10,one.get(0).cost().seconds()); assertEquals(12,one.get(0).cost().meters());
        double[][] matrix=new double[12][12]; for(var row:matrix) Arrays.fill(row,1);
        assertEquals(3,RouteAlgorithms.compare(matrix,matrix).size());
        assertThrows(IllegalArgumentException.class,()->RouteAlgorithms.heldKarp(matrix,matrix));
    }
    @Test void rejectsUnreachableAndNonSquareMatrices() {
        assertThrows(IllegalArgumentException.class,()->RouteAlgorithms.compare(new double[][]{{0,Double.NaN},{1,0}},new double[][]{{0,1},{1,0}}));
        assertThrows(IllegalArgumentException.class,()->RouteAlgorithms.compare(new double[][]{{0,1},{1}},new double[][]{{0,1},{1,0}}));
    }
    private RouteAlgorithms.Cost brute(List<Integer> order,int start,double[][] t,double[][] d) {
        if(start==order.size()) return RouteAlgorithms.cost(order,t,d);
        RouteAlgorithms.Cost best=new RouteAlgorithms.Cost(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        for(int i=start;i<order.size();i++) { Collections.swap(order,start,i); var cost=brute(order,start+1,t,d); if(RouteAlgorithms.better(cost,best)) best=cost; Collections.swap(order,start,i); }
        return best;
    }
}
