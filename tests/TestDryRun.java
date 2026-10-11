
import java.io.*;
import java.util.zip.*;
import ae2nei.core.AE2NEITransformer;

public class TestDryRun {
    public static void main(String[] args) throws Exception {
        AE2NEITransformer transformer = new AE2NEITransformer();
        if (args.length != 1) throw new IllegalArgumentException("Provide the NEI JAR path");
        String neiJar = args[0];

        try (ZipFile zf = new ZipFile(neiJar)) {
            // Check AutoCraftingManager
            byte[] b1 = readEntry(zf, zf.getEntry("codechicken/nei/recipe/AutoCraftingManager.class"));
            byte[] t1 = transformer.transform("codechicken.nei.recipe.AutoCraftingManager", "codechicken.nei.recipe.AutoCraftingManager", b1);

            // Check AutoCraftingManager$1
            byte[] b2 = readEntry(zf, zf.getEntry("codechicken/nei/recipe/AutoCraftingManager$1.class"));
            byte[] t2 = transformer.transform("codechicken.nei.recipe.AutoCraftingManager$1", "codechicken.nei.recipe.AutoCraftingManager$1", b2);

            System.out.println("DRY RUN PASSED: Both classes transformed without errors.");
        }
    }

    private static byte[] readEntry(ZipFile zf, ZipEntry entry) throws IOException {
        try (InputStream in = zf.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }
}
