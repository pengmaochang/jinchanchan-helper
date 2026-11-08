import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import local.jcc.probe.ShopVision;

/** Run the exact phone matcher against the user-provided reference screenshots. */
public class VisionCheck {
    static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
    public static void main(String[] args) throws Exception {
        Path assets = Path.of("app/src/main/assets/templates");
        Files.createDirectories(assets);
        BufferedImage before = ImageIO.read(Path.of(args[1]).toFile());
        byte[][] templates = new byte[5][];
        for (int i = 0; i < 5; i++) {
            templates[i] = ShopVision.strip(pixels(before), before.getWidth(), before.getHeight(), i);
            Files.write(assets.resolve("name" + i + ".bin"), templates[i]);
        }
        for (int j = 0; j < args.length; j++) {
            BufferedImage image = ImageIO.read(Path.of(args[j]).toFile());
            ShopVision.Result result = ShopVision.analyze(pixels(image), image.getWidth(), image.getHeight(), templates, ShopVision.NAMES, templates[0]);
            System.out.println(Path.of(args[j]).getFileName() + "\n" + result.summary());
            System.out.println("ink=" + java.util.Arrays.toString(result.ink));
            if (j == 0 && result.shopCandidate) throw new AssertionError("Closed shop was treated as open");
            if (j == 1 && (!result.hits[0] || !result.shopCandidate)) throw new AssertionError("Target not found");
            if (j == 2 && (result.hits[0] || !result.names[0].equals("空位"))) throw new AssertionError("Bought slot not empty");
            for (int i = 1; i < 5; i++) if (result.hits[i]) throw new AssertionError("Wrong card matched");
        }
        // All non-target strips are explicit negative examples.
        for (int i = 1; i < 5; i++) if (ShopVision.similarity(templates[i], templates[0]) >= ShopVision.MATCH_THRESHOLD)
            throw new AssertionError("Non-target matched target");
        System.out.println("Reference assertions passed.");
    }
}
