
import java.io.*;
import java.util.zip.*;
import ae2nei.core.AE2NEITransformer;

public class TestClassVerify {
    public static void main(String[] args) throws Exception {
        AE2NEITransformer transformer = new AE2NEITransformer();
        if (args.length != 1) throw new IllegalArgumentException("Provide the NEI JAR path");
        String neiJar = args[0];

        try (ZipFile zf = new ZipFile(neiJar)) {
            verifyClass(transformer, zf, "codechicken/nei/recipe/AutoCraftingManager.class", "codechicken.nei.recipe.AutoCraftingManager");
            verifyClass(transformer, zf, "codechicken/nei/recipe/RecipeHandlerRef.class", "codechicken.nei.recipe.RecipeHandlerRef");
        }
    }

    private static void verifyClass(AE2NEITransformer transformer, ZipFile zf, String entryName, String className) throws Exception {
        ZipEntry entry = zf.getEntry(entryName);
        byte[] original = readEntry(zf, entry);
        byte[] transformed = transformer.transform(className, className, original);

        File tmp = new File("build/tmp_" + className.replace(".", "_") + ".class");
        tmp.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(transformed);
        }
        System.out.println("Wrote " + tmp.getAbsolutePath() + " (" + transformed.length + " bytes)");
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
