import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** 生成各密度的启动图标（青→蓝渐变 + 白色收纳格图案） */
public class GenIcons {
    public static void main(String[] args) throws Exception {
        int[] sizes = {48, 72, 96, 144, 192};
        String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int k = 0; k < sizes.length; k++) {
            int size = sizes[k];
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gp = new GradientPaint(0, 0, new Color(0x00, 0x89, 0x7B),
                    size, size, new Color(0x1E, 0x88, 0xE5));
            g.setPaint(gp);
            g.fill(new RoundRectangle2D.Float(0, 0, size - 1, size - 1,
                    size * 0.22f, size * 0.22f));
            g.setColor(Color.WHITE);
            float inset = size * 0.18f;
            float cell = size - inset * 2f;
            float gap = cell * 0.10f;
            float w = (cell - gap) / 2f;
            float h = (cell - gap * 2) / 3f;
            float r = Math.min(w, h) * 0.28f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 2; col++) {
                    float x = inset + col * (w + gap);
                    float y = inset + row * (h + gap);
                    g.fill(new RoundRectangle2D.Float(x, y, w, h, r, r));
                }
            }
            g.dispose();
            File out = new File("app/res/mipmap-" + names[k]);
            out.mkdirs();
            ImageIO.write(img, "png", new File(out, "ic_launcher.png"));
        }
        System.out.println("icons generated");
    }
}
