package local.jcc.probe;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class GuardCheck {
    static byte[][] signature(String file) throws Exception {
        BufferedImage image = ImageIO.read(new File(file));
        int w = image.getWidth(), h = image.getHeight();
        return ScreenGuard.capture(image.getRGB(0, 0, w, h, null, 0, w), w, h);
    }
    public static void main(String[] args) throws Exception {
        byte[][] closed = signature(args[0]), before = signature(args[1]), after = signature(args[2]);
        if (!ScreenGuard.same(before, before)) throw new AssertionError("Unchanged screen rejected");
        if (ScreenGuard.same(before, after)) throw new AssertionError("Purchase change not rejected");
        if (ScreenGuard.same(before, closed)) throw new AssertionError("Closed shop not rejected");
        System.out.println("Pre-gesture guard checks passed.");
    }
}
