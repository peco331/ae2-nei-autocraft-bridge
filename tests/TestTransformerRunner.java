
import ae2nei.core.AE2NEITransformer;
import java.io.*;
import java.util.zip.*;

public class TestTransformerRunner {
    public static void main(String[] args) throws Exception {
        String neiJarPath = args[0];
        AE2NEITransformer transformer = new AE2NEITransformer();

        try (ZipFile zf = new ZipFile(neiJarPath)) {
            // Test 1: AutoCraftingManager
            ZipEntry e1 = zf.getEntry("codechicken/nei/recipe/AutoCraftingManager.class");
            if (e1 == null) throw new RuntimeException("AutoCraftingManager.class not found");
            byte[] b1 = readEntry(zf, e1);
            byte[] t1 = transformer.transform("codechicken.nei.recipe.AutoCraftingManager", "codechicken.nei.recipe.AutoCraftingManager", b1);
            if (t1 == null || t1.length == b1.length) {
                throw new RuntimeException("AutoCraftingManager transformation did not modify bytes!");
            }
            System.out.println("TEST 1 PASSED: AutoCraftingManager transformed (" + b1.length + " -> " + t1.length + " bytes)");

            // Test 2: AutoCraftingManager$1
            ZipEntry e2 = zf.getEntry("codechicken/nei/recipe/AutoCraftingManager$1.class");
            if (e2 == null) throw new RuntimeException("AutoCraftingManager$1.class not found");
            byte[] b2 = readEntry(zf, e2);
            byte[] t2 = transformer.transform("codechicken.nei.recipe.AutoCraftingManager$1", "codechicken.nei.recipe.AutoCraftingManager$1", b2);
            if (t2 == null) {
                throw new RuntimeException("AutoCraftingManager$1 transformation failed!");
            }
            System.out.println("TEST 2 PASSED: AutoCraftingManager$1 transformed successfully (" + b2.length + " -> " + t2.length + " bytes)");
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
