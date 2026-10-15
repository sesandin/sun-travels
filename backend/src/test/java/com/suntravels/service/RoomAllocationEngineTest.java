package com.suntravels.service;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomAllocationEngineTest {
 final RoomAllocationEngine engine=new RoomAllocationEngine();
 BigDecimal d(int n) { return BigDecimal.valueOf(n); }
 @Test void reroutesFlexibleRoomRatherThanReturningFalseUnavailable() {
  var result=engine.allocate(new BigDecimal[][]{{d(1),d(2)},{d(2),null}},new int[]{1,1}).orElseThrow(); assertArrayEquals(new int[]{1,0},result);
 }
 @Test void sameOfferingCanBeUsedOnlyWithinCapacity() {
  assertArrayEquals(new int[]{0,0},engine.allocate(new BigDecimal[][]{{d(1)},{d(2)}},new int[]{2}).orElseThrow());
  assertTrue(engine.allocate(new BigDecimal[][]{{d(1)},{d(2)}},new int[]{1}).isEmpty());
  assertTrue(engine.allocate(new BigDecimal[][]{{null,d(2)},{null,d(3)}},new int[]{10,1}).isEmpty());
 }
 @Test void tiesAreRepeatableAndNoPartialAssignmentIsReturned() {
  BigDecimal[][] costs={{d(1),d(1)},{d(1),d(1)}}; int[] expected=engine.allocate(costs,new int[]{1,1}).orElseThrow();
  for(int i=0;i<10;i++) assertArrayEquals(expected,engine.allocate(costs,new int[]{1,1}).orElseThrow());
  assertTrue(engine.allocate(new BigDecimal[][]{{d(1)},{null}},new int[]{2}).isEmpty());
 }
 @Test void exactMatchingAgreesWithIndependentExhaustiveOracleOnBoundedRandomCases() {
  Random random=new Random(517); for(int test=0;test<150;test++) {
   int r=1+random.nextInt(4),o=1+random.nextInt(4); BigDecimal[][] costs=new BigDecimal[r][o]; int[] caps=new int[o];
   for(int i=0;i<o;i++) caps[i]=random.nextInt(4);
   for(int i=0;i<r;i++) for(int j=0;j<o;j++) costs[i][j]=random.nextInt(4)==0?null:new BigDecimal(random.nextInt(500)+".05");
   BigDecimal optimum=brute(costs,caps.clone(),0,BigDecimal.ZERO); var actual=engine.allocate(costs,caps);
   if(optimum==null) assertTrue(actual.isEmpty()); else {
    int[] a=actual.orElseThrow(),used=new int[o]; BigDecimal price=BigDecimal.ZERO;
    for(int i=0;i<r;i++) { assertNotNull(costs[i][a[i]]);used[a[i]]++;price=price.add(costs[i][a[i]]); }
    for(int i=0;i<o;i++) assertTrue(used[i]<=caps[i]); assertEquals(0,optimum.compareTo(price));
   }
  }
 }
 BigDecimal brute(BigDecimal[][] costs,int[] cap,int room,BigDecimal sum) {
  if(room==costs.length) return sum; BigDecimal best=null;
  for(int o=0;o<cap.length;o++) if(cap[o]>0 && costs[room][o]!=null) {
   cap[o]--;BigDecimal value=brute(costs,cap,room+1,sum.add(costs[room][o]));cap[o]++;
   if(value!=null && (best==null || value.compareTo(best)<0)) best=value;
  } return best;
 }
}
