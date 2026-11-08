package local.jcc.probe;

public class ReserveCheck {
    public static void main(String[] args) {
        int[] expected = {0,10,20,30,40,50,60,80,100};
        if (GoldReserve.DEFAULT != 50) throw new AssertionError("Default");
        for (int i = 0; i < expected.length; i++) {
            if (GoldReserve.step(expected[i], true) != expected[Math.min(i+1, expected.length-1)]) throw new AssertionError("Up");
            if (GoldReserve.step(expected[i], false) != expected[Math.max(i-1, 0)]) throw new AssertionError("Down");
        }
        if (GoldReserve.step(55, true) != 60 || GoldReserve.step(55, false) != 50 || GoldReserve.step(999, true) != 999)
            throw new AssertionError("Custom input");
        System.out.println("Reserve checks passed.");
    }
}
