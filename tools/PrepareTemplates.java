package local.jcc.probe;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;

public class PrepareTemplates {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(Path.of(args[0]).toFile());
        if (!PhoneLayout.accepts(image.getWidth(), image.getHeight())) throw new IllegalArgumentException();
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        Path directory = Path.of("app/src/main/assets/templates"); Files.createDirectories(directory);
        for (int i = 0; i < 5; i++) Files.write(directory.resolve("name" + i + ".bin"), PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.name(i)));
        Files.write(directory.resolve("refresh.bin"), PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.REFRESH));
        Files.write(directory.resolve("fee1.bin"), PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.cost(0)));
        Files.write(directory.resolve("fee2.bin"), PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.cost(4)));
        if (args.length > 1) {
            BufferedImage live = ImageIO.read(Path.of(args[1]).toFile());
            int[] sample = live.getRGB(0, 0, live.getWidth(), live.getHeight(), null, 0, live.getWidth());
            Files.write(directory.resolve("fee3.bin"), PhoneVision.mask(sample, live.getWidth(), PhoneLayout.cost(4)));
        }
        if (args.length > 2) {
            BufferedImage diana = ImageIO.read(Path.of(args[2]).toFile());
            if (!PhoneLayout.accepts(diana.getWidth(), diana.getHeight())) throw new IllegalArgumentException();
            int[] sample = diana.getRGB(0, 0, diana.getWidth(), diana.getHeight(), null, 0, diana.getWidth());
            Files.write(directory.resolve("name5.bin"), PhoneVision.mask(sample, diana.getWidth(), PhoneLayout.name(2)));
        }
        System.out.println("Native-pixel reference templates generated.");
    }
}
