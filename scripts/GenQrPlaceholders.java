import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * 生成 QQ 群二维码 / 赞助收款码的占位图（白底 + 定位角 + 固定种子伪点阵 + 中央提示文字）。
 * 拿到真实二维码后直接替换 app/res/drawable-nodpi/ 下同名文件重新构建即可。
 */
public class GenQrPlaceholders {
    public static void main(String[] args) throws Exception {
        gen("app/res/drawable-nodpi/qr_qq.png", "QQ 交流群", "二维码", 42);
        gen("app/res/drawable-nodpi/qr_donate.png", "赞助收款码", "二维码", 42);
        System.out.println("qr placeholders generated");
    }

    static void gen(String path, String line1, String line2, int seed) throws Exception {
        int size = 660, quiet = 33, grid = 29; // 29x29 模块
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);

        float cell = (size - quiet * 2f) / grid;
        Random rnd = new Random(seed);
        g.setColor(Color.BLACK);
        for (int r = 0; r < grid; r++) {
            for (int c = 0; c < grid; c++) {
                if (inFinder(r, c, grid)) continue;
                if (rnd.nextBoolean()) {
                    g.fillRect(Math.round(quiet + c * cell), Math.round(quiet + r * cell),
                            (int) Math.ceil(cell), (int) Math.ceil(cell));
                }
            }
        }
        // 三个定位角（7x7 外框 + 3x3 实心）
        drawFinder(g, quiet, quiet, cell);
        drawFinder(g, quiet + (grid - 7) * cell, quiet, cell);
        drawFinder(g, quiet, quiet + (grid - 7) * cell, cell);

        // 中央白色圆角提示区
        float cw = size * 0.52f, ch = size * 0.235f;
        float cx = (size - cw) / 2f, cy = (size - ch) / 2f;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(cx, cy, cw, ch, 22, 22));
        g.setColor(new Color(0x9E, 0x9E, 0x9E));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        drawCentered(g, line1, size / 2f, cy + ch * 0.42f);
        g.setColor(new Color(0xF4, 0x43, 0x36));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        drawCentered(g, "请替换为真实" + line2, size / 2f, cy + ch * 0.78f);
        g.dispose();

        File out = new File(path);
        out.mkdirs();
        ImageIO.write(img, "png", out);
    }

    static boolean inFinder(int r, int c, int grid) {
        return (r < 8 && c < 8) || (r < 8 && c >= grid - 8) || (r >= grid - 8 && c < 8);
    }

    static void drawFinder(Graphics2D g, float x, float y, float cell) {
        g.setColor(Color.BLACK);
        g.fillRect(Math.round(x), Math.round(y), Math.round(cell * 7), Math.round(cell * 7));
        g.setColor(Color.WHITE);
        g.fillRect(Math.round(x + cell), Math.round(y + cell),
                Math.round(cell * 5), Math.round(cell * 5));
        g.setColor(Color.BLACK);
        g.fillRect(Math.round(x + cell * 2), Math.round(y + cell * 2),
                Math.round(cell * 3), Math.round(cell * 3));
    }

    static void drawCentered(Graphics2D g, String s, float cx, float baseline) {
        float w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, cx - w / 2f, baseline);
    }
}
