package com.suntravels.service;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
/** Exact min-cost capacitated bipartite matching, not a greedy/beam search.
 * At most 8 augmentations; reverse edges allow cheaper/feasible reassignment. */
@Component
public class RoomAllocationEngine {
 private static class Edge {
  final int to,reverse; int capacity; final BigDecimal cost;
  Edge(int to,int reverse,int capacity,BigDecimal cost) { this.to=to;this.reverse=reverse;this.capacity=capacity;this.cost=cost; }
 }
 private void edge(List<List<Edge>> graph,int from,int to,int capacity,BigDecimal cost) {
  Edge forward=new Edge(to,graph.get(to).size(),capacity,cost),reverse=new Edge(from,graph.get(from).size(),0,cost.negate());
  graph.get(from).add(forward);graph.get(to).add(reverse);
 }
 /** Null cost means occupancy-ineligible. Returns offering-column per input room, or empty if infeasible. */
 public Optional<int[]> allocate(BigDecimal[][] costs,int[] capacities) {
  int rooms=costs.length,offers=capacities.length,source=rooms+offers,sink=source+1,size=sink+1;
  List<List<Edge>> graph=new ArrayList<>(); for(int i=0;i<size;i++) graph.add(new ArrayList<>());
  Edge[][] choices=new Edge[rooms][offers];
  for(int r=0;r<rooms;r++) {
   edge(graph,source,r,1,BigDecimal.ZERO);
   for(int o=0;o<offers;o++) if(costs[r][o]!=null && capacities[o]>0) {
    int index=graph.get(r).size(); edge(graph,r,rooms+o,1,costs[r][o]); choices[r][o]=graph.get(r).get(index);
   }
  }
  for(int o=0;o<offers;o++) edge(graph,rooms+o,sink,Math.min(rooms,capacities[o]),BigDecimal.ZERO);
  for(int flow=0;flow<rooms;flow++) {
   BigDecimal[] distance=new BigDecimal[size]; int[] parent=new int[size],via=new int[size]; Arrays.fill(parent,-1);distance[source]=BigDecimal.ZERO;
   for(int pass=0;pass<size-1;pass++) {
    boolean changed=false;
    for(int u=0;u<size;u++) if(distance[u]!=null) for(int e=0;e<graph.get(u).size();e++) {
     Edge v=graph.get(u).get(e); if(v.capacity==0) continue;
     BigDecimal candidate=distance[u].add(v.cost);
     if(distance[v.to]==null || candidate.compareTo(distance[v.to])<0) { distance[v.to]=candidate;parent[v.to]=u;via[v.to]=e;changed=true; }
    }
    if(!changed) break;
   }
   if(distance[sink]==null) return Optional.empty();
   for(int v=sink;v!=source;v=parent[v]) {
    Edge e=graph.get(parent[v]).get(via[v]); e.capacity--;graph.get(v).get(e.reverse).capacity++;
   }
  }
  int[] assignment=new int[rooms];Arrays.fill(assignment,-1);
  for(int r=0;r<rooms;r++) for(int o=0;o<offers;o++) if(choices[r][o]!=null && choices[r][o].capacity==0) assignment[r]=o;
  return Optional.of(assignment);
 }
}
