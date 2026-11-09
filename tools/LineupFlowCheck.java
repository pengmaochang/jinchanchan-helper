package local.jcc.probe;
import static local.jcc.probe.LineupFlow.Action.*;
public class LineupFlowCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  LineupFlow f=new LineupFlow();LineupFlow.View v=new LineupFlow.View();v.board=true;v.shop=true;
  check(f.next(v)==CLOSE_SHOP,"Close an open shop using total gold first");check(f.next(v)==WAIT,"Never toggle gold repeatedly on the old open frame");
  v.shop=false;check(f.next(v)==OPEN_MENU,"Open formation only after shop closes");check(f.next(v)==WAIT,"Do not click import before the menu appears");
  v.menu=true;check(f.next(v)==IMPORT,"Use the recognized import button");
  v.menu=false;v.replacement=true;check(f.next(v)==WAIT,"First replacement frame is not stable");check(f.next(v)==REPLACE_THIRD,"Replace only the third row after confirmation");
  check(f.next(v)==WAIT,"Never re-click replace while transition is pending");v.replacement=false;v.menu=true;
  check(f.next(v)==WAIT,"A different imported title must not be applied");v.targetMatches=true;
  check(f.next(v)==WAIT,"Verify imported row before applying");check(f.next(v)==APPLY_TARGET,"Apply only the expected stable target lineup");v.menu=false;
  check(f.next(v)==WAIT,"Closing the panel alone does not prove application");v.menu=true;v.applied=true;
  check(f.next(v)==CLOSE_EXISTING,"Confirm application via Cancel state then close");v.menu=false;
  check(f.next(v)==FINISH,"Complete on the game board after confirmed application");
  f=new LineupFlow();v=new LineupFlow.View();v.menu=true;check(f.next(v)==CLOSE_EXISTING,"Reset an existing menu before importing new clipboard data");
  f=new LineupFlow();v=new LineupFlow.View();v.board=true;check(f.next(v)==OPEN_MENU,"Open from closed board");
  v.menu=true;v.full=true;v.targetMatches=true;check(f.next(v)==IMPORT,"Import with full favorites");
  for(int i=0;i<8;i++)check(f.next(v)==WAIT,"An old same-title third row must not bypass the required replacement dialog");
  System.out.println("Lineup flow checks passed.");
 }
}
