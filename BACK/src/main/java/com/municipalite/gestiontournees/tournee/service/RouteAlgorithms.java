package com.municipalite.gestiontournees.tournee.service;

import java.util.*;
import java.util.function.Supplier;

/** Closed directed TSP: index 0 is the depot, remaining indices are manual order. */
public final class RouteAlgorithms {
    private RouteAlgorithms() {}
    public record Cost(double seconds, double meters) {}
    public record Result(String algorithm, List<Integer> order, Cost cost, double runtimeMillis) {}
    private static final double EPS = 1e-9;

    public static List<Result> compare(double[][] durations, double[][] distances) {
        validate(durations, distances);
        List<Integer> manual = new ArrayList<>();
        for (int i=1; i<durations.length; i++) manual.add(i);
        List<Result> results = new ArrayList<>();
        results.add(measure("MANUAL", () -> manual, durations, distances));
        Result nn = measure("NN", () -> nearestNeighbour(durations, distances), durations, distances);
        results.add(nn);
        results.add(measure("NN_2OPT", () -> twoOpt(nn.order(), durations, distances), durations, distances));
        if (manual.size() <= 10) results.add(measure("EXACT", () -> heldKarp(durations, distances), durations, distances));
        return results;
    }
    public static Result bestHeuristic(List<Result> results) {
        return results.stream().filter(r -> !r.algorithm().equals("EXACT"))
                .reduce((a,b) -> better(b.cost(), a.cost()) ? b : a).orElseThrow();
    }
    public static Cost cost(List<Integer> order, double[][] durations, double[][] distances) {
        int from=0; double seconds=0, meters=0;
        for (int next : order) { seconds+=durations[from][next]; meters+=distances[from][next]; from=next; }
        return new Cost(seconds+durations[from][0], meters+distances[from][0]);
    }
    public static List<Integer> nearestNeighbour(double[][] durations, double[][] distances) {
        List<Integer> order=new ArrayList<>(); Set<Integer> remaining=new LinkedHashSet<>();
        for (int i=1; i<durations.length; i++) remaining.add(i);
        int current=0;
        while (!remaining.isEmpty()) {
            final int from=current;
            int next=remaining.stream().min(Comparator.<Integer>comparingDouble(i -> durations[from][i])
                    .thenComparingDouble(i -> distances[from][i])).orElseThrow();
            order.add(next); remaining.remove(next); current=next;
        }
        return order;
    }
    public static List<Integer> twoOpt(List<Integer> initial, double[][] durations, double[][] distances) {
        List<Integer> best=new ArrayList<>(initial); boolean improved;
        do {
            improved=false; Cost bestCost=cost(best,durations,distances);
            outer: for (int i=0; i<best.size()-1; i++) for (int j=i+1; j<best.size(); j++) {
                List<Integer> candidate=new ArrayList<>(best); Collections.reverse(candidate.subList(i,j+1));
                // All reversed internal arcs change in an asymmetric matrix.
                Cost candidateCost=cost(candidate,durations,distances);
                if (better(candidateCost,bestCost)) { best=candidate; improved=true; break outer; }
            }
        } while(improved);
        return best;
    }
    public static List<Integer> heldKarp(double[][] durations, double[][] distances) {
        int n=durations.length-1;
        if(n>10) throw new IllegalArgumentException("Exact benchmark limited to 10 collection points");
        if(n==0) return List.of();
        int count=1<<n;
        double[][] time=new double[count][n], distance=new double[count][n]; int[][] parent=new int[count][n];
        for(int mask=0;mask<count;mask++) { Arrays.fill(time[mask],Double.POSITIVE_INFINITY); Arrays.fill(distance[mask],Double.POSITIVE_INFINITY); Arrays.fill(parent[mask],-1); }
        for(int j=0;j<n;j++) { time[1<<j][j]=durations[0][j+1]; distance[1<<j][j]=distances[0][j+1]; }
        for(int mask=1;mask<count;mask++) for(int last=0;last<n;last++) {
            if((mask&(1<<last))==0 || !Double.isFinite(time[mask][last])) continue;
            for(int next=0;next<n;next++) if((mask&(1<<next))==0) {
                int newMask=mask|(1<<next);
                Cost candidate=new Cost(time[mask][last]+durations[last+1][next+1], distance[mask][last]+distances[last+1][next+1]);
                if(better(candidate,new Cost(time[newMask][next],distance[newMask][next]))) {
                    time[newMask][next]=candidate.seconds(); distance[newMask][next]=candidate.meters(); parent[newMask][next]=last;
                }
            }
        }
        int mask=count-1,last=-1; Cost best=new Cost(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        for(int j=0;j<n;j++) {
            Cost candidate=new Cost(time[mask][j]+durations[j+1][0],distance[mask][j]+distances[j+1][0]);
            if(better(candidate,best)) { best=candidate; last=j; }
        }
        List<Integer> order=new ArrayList<>();
        while(last>=0) { order.add(last+1); int previous=parent[mask][last]; mask^=1<<last; last=previous; }
        Collections.reverse(order); return order;
    }
    public static boolean better(Cost a, Cost b) {
        return a.seconds()<b.seconds()-EPS || (Math.abs(a.seconds()-b.seconds())<=EPS && a.meters()<b.meters()-EPS);
    }
    private static Result measure(String name,Supplier<List<Integer>> action,double[][] durations,double[][] distances) {
        long start=System.nanoTime(); List<Integer> order=List.copyOf(action.get()); double elapsed=(System.nanoTime()-start)/1_000_000.;
        return new Result(name,order,cost(order,durations,distances),elapsed);
    }
    public static void validate(double[][] durations,double[][] distances) {
        if(durations==null || distances==null || durations.length<2 || durations.length!=distances.length)
            throw new IllegalArgumentException("Matrices carrées avec dépôt et au moins un point requises");
        for(double[][] matrix:List.of(durations,distances)) for(int i=0;i<matrix.length;i++) {
            if(matrix[i]==null || matrix[i].length!=matrix.length) throw new IllegalArgumentException("Matrice incomplète");
            for(double value:matrix[i]) if(!Double.isFinite(value) || value<0) throw new IllegalArgumentException("Liaison inaccessible ou coût invalide");
        }
    }
}
